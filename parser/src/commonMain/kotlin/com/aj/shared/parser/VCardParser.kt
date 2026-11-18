package com.aj.shared.parser

/** Custom vCard 2.1/3.0/4.0-ish parser — QP, PHOTO URI/meta, typed TEL/EMAIL. */
internal object VCardParser {

    fun parse(
        bytes: ByteArray,
        options: FileParseOptions,
        sourceName: String?,
    ): ParseResult {
        return try {
            if (!options.isActive()) {
                return ParseResult.Error("Parse cancelled", format = FileParseFormat.VCARD)
            }
            val text = TextDecoder.decode(bytes, options.charsetName)
            val cards = splitCards(text)
            val maps = cards.map { parseCard(it, options.maxVCardPhotoBytes) }
            val limited = options.maxRows?.let { maps.take(it) } ?: maps
            val warnings = mutableListOf<String>()
            var truncated = false
            if (options.maxRows != null && maps.size > options.maxRows) {
                truncated = true
                warnings += "vCard truncated to maxRows=${options.maxRows}"
            }
            if (limited.any { it.containsKey("photo_present") || it.containsKey("photo_url") }) {
                warnings += "PHOTO: URI/meta kept; large binary blobs skipped."
            } else if (text.contains("PHOTO", ignoreCase = true) || text.contains("LOGO", ignoreCase = true)) {
                warnings += "PHOTO/LOGO binary blobs were skipped (contacts text fields only)."
            }
            val columns = linkedSetOf<String>()
            limited.forEach { columns.addAll(it.keys) }
            var colList = columns.toList().ifEmpty { listOf("fn", "n", "tel", "email", "org") }
            if (options.includeRowId) {
                colList = listOf("id") + colList.filter { it != "id" }
            }
            val rows = limited.mapIndexed { index, card ->
                val ordered = linkedMapOf<String, String>()
                if (options.includeRowId) ordered["id"] = (index + 1).toString()
                colList.forEach { key ->
                    if (key == "id" && options.includeRowId) return@forEach
                    ordered[key] = card[key].orEmpty()
                }
                ordered
            }
            ParseResult.Success(
                ParseSuccess(
                    format = FileParseFormat.VCARD,
                    sheets = listOf(ParsedSheet("contacts", colList, rows)),
                    sourceName = sourceName,
                    warnings = warnings,
                    truncated = truncated,
                ),
            )
        } catch (t: Throwable) {
            ParseResult.Error(
                message = "Failed to parse vCard: ${t.message ?: t::class.simpleName}",
                cause = t,
                format = FileParseFormat.VCARD,
            )
        }
    }

    private fun splitCards(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val cards = mutableListOf<String>()
        val current = StringBuilder()
        var inCard = false
        for (line in normalized.lineSequence()) {
            val upper = line.trim().uppercase()
            if (upper == "BEGIN:VCARD") {
                inCard = true
                current.clear()
                current.appendLine(line)
                continue
            }
            if (!inCard) continue
            current.appendLine(line)
            if (upper == "END:VCARD") {
                cards += current.toString()
                inCard = false
            }
        }
        return cards
    }

    private fun parseCard(cardText: String, maxPhotoBytes: Int = 8 * 1024): Map<String, String> {
        val unfolded = unfold(cardText)
        val values = linkedMapOf<String, MutableList<String>>()
        for (rawLine in unfolded.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val upper = line.uppercase()
            if (upper == "BEGIN:VCARD" || upper == "END:VCARD" || upper.startsWith("VERSION")) continue
            val sep = line.indexOf(':')
            if (sep <= 0) continue
            val left = line.substring(0, sep)
            var right = line.substring(sep + 1).trim()
            val namePart = left.substringBefore(';')
            val params = left.substringAfter(';', missingDelimiterValue = "")
            val baseKey = normalizeKey(namePart)
            if (baseKey.isEmpty()) continue
            // PHOTO / LOGO: URI, small base64 data-URL, or present meta
            if (baseKey == "photo" || baseKey == "logo") {
                val type = paramValue(params, "TYPE") ?: paramValue(params, "MEDIATYPE")
                val valueParam = paramValue(params, "VALUE")
                val encoding = paramValue(params, "ENCODING")?.uppercase()
                val looksUri = valueParam.equals("URI", true) ||
                    right.startsWith("http://", true) ||
                    right.startsWith("https://", true) ||
                    (right.startsWith("data:", true) && right.length < 512)
                when {
                    looksUri && right.length < 2048 -> {
                        values.getOrPut("photo_url") { mutableListOf() }.add(unescape(right.take(2048)))
                    }
                    maxPhotoBytes > 0 &&
                        (encoding == "BASE64" || encoding == "B" || valueParam.equals("BINARY", true) ||
                            params.contains("BASE64", true)) &&
                        right.length in 1..(maxPhotoBytes * 2) -> {
                        val mime = when {
                            type?.contains("png", true) == true -> "image/png"
                            type?.contains("gif", true) == true -> "image/gif"
                            else -> "image/jpeg"
                        }
                        val cleaned = right.replace(Regex("\\s"), "")
                        values.getOrPut("photo_url") { mutableListOf() }
                            .add("data:$mime;base64,${cleaned.take(maxPhotoBytes * 2)}")
                        values.getOrPut("photo_present") { mutableListOf() }.add("embedded")
                    }
                    else -> {
                        values.getOrPut("photo_present") { mutableListOf() }.add("yes")
                    }
                }
                if (!type.isNullOrBlank()) {
                    values.getOrPut("photo_type") { mutableListOf() }.add(type)
                }
                continue
            }
            if (baseKey == "sound" || baseKey == "key") {
                continue
            }
            val encoding = paramValue(params, "ENCODING")?.uppercase()
            val charset = paramValue(params, "CHARSET")
            if (encoding == "QUOTED-PRINTABLE" || right.contains('=')) {
                // Only QP-decode when declared or classic v2.1 soft breaks
                if (encoding == "QUOTED-PRINTABLE" || params.contains("QUOTED-PRINTABLE", ignoreCase = true)) {
                    right = decodeQuotedPrintable(right)
                }
            }
            if (charset.equals("UTF-8", ignoreCase = true)) {
                // already unicode string
            }
            val type = paramValue(params, "TYPE")?.lowercase()?.replace(',', '_')
            val key = when {
                type != null && (baseKey == "tel" || baseKey == "email" || baseKey == "adr") ->
                    "${baseKey}_$type"
                else -> baseKey
            }
            values.getOrPut(key) { mutableListOf() }.add(unescape(right))
        }
        return values.mapValues { (_, list) -> list.distinct().joinToString(" | ") }
    }

    private fun paramValue(params: String, name: String): String? {
        if (params.isBlank()) return null
        val parts = params.split(';')
        for (p in parts) {
            val eq = p.indexOf('=')
            if (eq <= 0) {
                // bare TYPE like CELL
                if (name.equals("TYPE", ignoreCase = true) && p.isNotBlank() && !p.contains('=')) {
                    return p.trim()
                }
                continue
            }
            val k = p.substring(0, eq).trim()
            val v = p.substring(eq + 1).trim().removeSurrounding("\"")
            if (k.equals(name, ignoreCase = true)) return v
        }
        return null
    }

    private fun decodeQuotedPrintable(input: String): String {
        val cleaned = input.replace("=\n", "").replace("=\r\n", "")
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < cleaned.length) {
            val ch = cleaned[i]
            if (ch == '=' && i + 2 < cleaned.length) {
                val hex = cleaned.substring(i + 1, i + 3)
                val b = hex.toIntOrNull(16)
                if (b != null) {
                    bytes += b.toByte()
                    i += 3
                    continue
                }
            }
            bytes += ch.code.toByte()
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun unfold(text: String): String {
        val out = StringBuilder()
        for (line in text.replace("\r\n", "\n").replace('\r', '\n').lineSequence()) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                out.append(line.drop(1))
            } else {
                if (out.isNotEmpty()) out.append('\n')
                out.append(line)
            }
        }
        return out.toString()
    }

    private fun normalizeKey(name: String): String {
        val base = name.trim().lowercase()
            .replace(Regex("""^item\d+\."""), "")
        return base.replace(Regex("[^a-z0-9_]+"), "_").trim('_')
    }

    private fun unescape(value: String): String =
        value.replace("\\n", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
}
