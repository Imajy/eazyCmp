package com.aj.shared.pdf.parser

import com.aj.shared.pdf.model.PdfDocumentInfo
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.model.PdfRect
import com.aj.shared.pdf.model.PdfSearchMatch

/**
 * Pure Kotlin parser for reading PDF metadata, page tree, text, and structure.
 */
class PdfReader(private val bytes: ByteArray) {

    private val textContent: String by lazy {
        decodeLatin1(bytes)
    }

    /**
     * Extracts high-level document metadata.
     */
    fun readDocumentInfo(): PdfDocumentInfo {
        val version = parseVersion()
        val isEncrypted = textContent.contains("/Encrypt")
        val pageInfos = readPageInfos()
        val title = extractInfoField("Title")
        val author = extractInfoField("Author")
        val subject = extractInfoField("Subject")
        val keywords = extractInfoField("Keywords")
        val creator = extractInfoField("Creator")
        val producer = extractInfoField("Producer")
        val creationDate = extractInfoField("CreationDate")
        val modDate = extractInfoField("ModDate")

        return PdfDocumentInfo(
            title = title,
            author = author,
            subject = subject,
            keywords = keywords,
            creator = creator,
            producer = producer,
            creationDate = creationDate,
            modificationDate = modDate,
            pageCount = pageInfos.size,
            version = version,
            isEncrypted = isEncrypted,
            fileSize = bytes.size.toLong()
        )
    }

    /**
     * Extracts page dimensions and rotations.
     */
    fun readPageInfos(): List<PdfPageInfo> {
        val pages = mutableListOf<PdfPageInfo>()
        // Match /Type /Page objects (not /Pages)
        val pageRegex = Regex("""/Type\s*/Page\b(?!\s*s)""")
        val matches = pageRegex.findAll(textContent).toList()

        for ((index, match) in matches.withIndex()) {
            val objStart = textContent.lastIndexOf("obj", match.range.first)
            val objEnd = textContent.indexOf("endobj", match.range.last)
            val objBlock = if (objStart != -1 && objEnd != -1 && objEnd > objStart) {
                textContent.substring(objStart, objEnd)
            } else {
                textContent.substring(match.range.first, (match.range.first + 1000).coerceAtMost(textContent.length))
            }

            val mediaBox = parseMediaBox(objBlock) ?: parseInheritedMediaBox() ?: PdfRect(0f, 0f, 612f, 792f)
            val rotation = parseRotation(objBlock)

            pages.add(
                PdfPageInfo(
                    pageIndex = index,
                    width = mediaBox.width,
                    height = mediaBox.height,
                    rotation = rotation
                )
            )
        }

        // Fallback: if no /Type /Page found but /Count N exists
        if (pages.isEmpty()) {
            val countRegex = Regex("""/Count\s+(\d+)""")
            val count = countRegex.find(textContent)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            for (i in 0 until count) {
                pages.add(PdfPageInfo(pageIndex = i, width = 612f, height = 792f, rotation = 0))
            }
        }

        return pages
    }

    /**
     * Search text across the PDF document.
     */
    fun searchText(query: String, maxResults: Int = 100): List<PdfSearchMatch> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<PdfSearchMatch>()
        val lowerQuery = query.lowercase().trim()

        val pagesText = extractPagesText()
        for ((pageIndex, text) in pagesText.withIndex()) {
            val lowerText = text.lowercase()
            var startIdx = 0
            var matchInPage = 0
            while (startIdx < lowerText.length) {
                val found = lowerText.indexOf(lowerQuery, startIdx)
                if (found == -1) break

                val snippetStart = (found - 30).coerceAtLeast(0)
                val snippetEnd = (found + query.length + 30).coerceAtMost(text.length)
                val snippet = text.substring(snippetStart, snippetEnd).replace('\n', ' ').trim()

                results.add(
                    PdfSearchMatch(
                        matchId = "$pageIndex-$matchInPage",
                        pageIndex = pageIndex,
                        textSnippet = snippet,
                        matchIndexInPage = matchInPage
                    )
                )
                matchInPage++
                if (results.size >= maxResults) return results
                startIdx = found + query.length
            }
        }
        return results
    }

    /**
     * Extract plain text content per page.
     */
    fun extractPagesText(): List<String> {
        val pageInfos = readPageInfos()
        if (pageInfos.isEmpty()) return emptyList()

        val pagesText = mutableListOf<String>()
        // Find text inside BT (Begin Text) ... ET (End Text) blocks
        val btEtRegex = Regex("""BT\s+([\s\S]*?)\s+ET""")
        val textBlocks = btEtRegex.findAll(textContent).map { it.groupValues[1] }.toList()

        if (textBlocks.isNotEmpty()) {
            val perPage = (textBlocks.size / pageInfos.size).coerceAtLeast(1)
            for (p in pageInfos.indices) {
                val slice = textBlocks.drop(p * perPage).take(perPage)
                val sb = StringBuilder()
                for (block in slice) {
                    val parsed = parseTextInBlock(block)
                    sb.append(parsed)
                    sb.append(" ")
                }
                pagesText.add(sb.toString().trim())
            }
        } else {
            // General text string scanner fallback
            val generalText = parseAllLiteralStrings(textContent)
            val chunkSize = (generalText.length / pageInfos.size).coerceAtLeast(1)
            for (p in pageInfos.indices) {
                val part = generalText.drop(p * chunkSize).take(chunkSize)
                pagesText.add(part.trim())
            }
        }

        return pagesText
    }

    private fun parseTextInBlock(block: String): String {
        val sb = StringBuilder()
        // Tj operator: (string) Tj or <hex> Tj
        val tjRegex = Regex("""(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F]+>)\s*Tj""")
        for (m in tjRegex.findAll(block)) {
            sb.append(decodePdfString(m.groupValues[1])).append(" ")
        }
        // TJ operator: [ (str1) 20 (str2) ] TJ
        val tjArrayRegex = Regex("""\[(.*?)\]\s*TJ""")
        for (m in tjArrayRegex.findAll(block)) {
            val inner = m.groupValues[1]
            val strRegex = Regex("""\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F]+>""")
            for (sm in strRegex.findAll(inner)) {
                sb.append(decodePdfString(sm.value))
            }
            sb.append(" ")
        }
        return sb.toString()
    }

    private fun parseAllLiteralStrings(source: String): String {
        val sb = StringBuilder()
        val regex = Regex("""\((?:[^()\\]|\\.)*\)""")
        for (m in regex.findAll(source)) {
            val decoded = decodePdfString(m.value)
            if (decoded.length > 2 && decoded.any { it.isLetter() }) {
                sb.append(decoded).append(" ")
            }
        }
        return sb.toString()
    }

    private fun decodePdfString(raw: String): String {
        if (raw.startsWith("<") && raw.endsWith(">")) {
            val hex = raw.substring(1, raw.length - 1)
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i < hex.length) {
                if (i + 1 < hex.length) {
                    val b = hex.substring(i, i + 2).toIntOrNull(16)?.toByte()
                    if (b != null) bytes.add(b)
                    i += 2
                } else {
                    val b = (hex.substring(i, i + 1) + "0").toIntOrNull(16)?.toByte()
                    if (b != null) bytes.add(b)
                    i++
                }
            }
            return bytes.toByteArray().decodeToString()
        }
        if (raw.startsWith("(") && raw.endsWith(")")) {
            val inner = raw.substring(1, raw.length - 1)
            return unescapePdfString(inner)
        }
        return raw
    }

    private fun unescapePdfString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    '(', ')', '\\' -> { sb.append(next); i += 2 }
                    in '0'..'7' -> {
                        // Octal sequence
                        var octal = next.toString()
                        var j = i + 2
                        while (j < s.length && j < i + 4 && s[j] in '0'..'7') {
                            octal += s[j]
                            j++
                        }
                        sb.append(octal.toInt(8).toChar())
                        i = j
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun parseVersion(): String {
        val line = textContent.lineSequence().firstOrNull() ?: ""
        val match = Regex("""%PDF-(\d+\.\d+)""").find(line)
        return match?.groupValues?.get(1) ?: "1.7"
    }

    private fun extractInfoField(field: String): String? {
        val pattern = Regex("""/$field\s*(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F]+>)""")
        val match = pattern.find(textContent) ?: return null
        return decodePdfString(match.groupValues[1]).takeIf { it.isNotBlank() }
    }

    private fun parseMediaBox(block: String): PdfRect? {
        val boxRegex = Regex("""/MediaBox\s*\[\s*([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s*\]""")
        val m = boxRegex.find(block) ?: return null
        val x0 = m.groupValues[1].toFloatOrNull() ?: 0f
        val y0 = m.groupValues[2].toFloatOrNull() ?: 0f
        val x1 = m.groupValues[3].toFloatOrNull() ?: 612f
        val y1 = m.groupValues[4].toFloatOrNull() ?: 792f
        return PdfRect(x0, y0, x1, y1)
    }

    private fun parseInheritedMediaBox(): PdfRect? {
        val boxRegex = Regex("""/MediaBox\s*\[\s*([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s*\]""")
        val m = boxRegex.find(textContent) ?: return null
        val x0 = m.groupValues[1].toFloatOrNull() ?: 0f
        val y0 = m.groupValues[2].toFloatOrNull() ?: 0f
        val x1 = m.groupValues[3].toFloatOrNull() ?: 612f
        val y1 = m.groupValues[4].toFloatOrNull() ?: 792f
        return PdfRect(x0, y0, x1, y1)
    }

    private fun parseRotation(block: String): Int {
        val rotRegex = Regex("""/Rotate\s+(\d+)""")
        val deg = rotRegex.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return ((deg % 360) + 360) % 360
    }

    private fun decodeLatin1(bytes: ByteArray): String {
        val chars = CharArray(bytes.size)
        for (i in bytes.indices) {
            chars[i] = (bytes[i].toInt() and 0xFF).toChar()
        }
        return chars.concatToString()
    }
}
