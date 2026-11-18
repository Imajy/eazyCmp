package com.aj.shared.parser

/**
 * Parse worksheet XML from raw UTF-8 bytes without building one giant [String].
 * Used for oversized sheets to cut peak RAM (tags are ASCII; only cell text is decoded).
 */
internal object SheetXmlByteParser {

    fun parse(
        bytes: ByteArray,
        sharedStrings: Map<Int, String>,
        styles: ExcelValueFormatter.StyleInfo,
        options: FileParseOptions,
        date1904: Boolean,
        applyMerges: (String, MutableMap<Int, MutableMap<Int, String>>, Int) -> Unit,
        parseCellRef: (String) -> Pair<Int, Int>?,
    ): XlsxParserSheetBuild {
        val warnings = mutableListOf<String>()
        val grid = mutableMapOf<Int, MutableMap<Int, String>>()
        val hiddenRows = hashSetOf<Int>()
        var maxCol = -1
        var cellCount = 0
        var truncated = false
        var hitCellCap = false

        if (options.skipHiddenRows) {
            forEachOpenTag(bytes, "row") { open ->
                val r = attr(open, "r")?.toIntOrNull()?.minus(1) ?: return@forEachOpenTag
                val hidden = attr(open, "hidden")
                if (hidden == "1" || hidden.equals("true", true)) hiddenRows += r
            }
        }

        forEachElement(bytes, "c") { open, innerBytes ->
            if (hitCellCap) return@forEachElement
            if (!options.isActive()) throw ParseCancelledException("Parse cancelled")
            val ref = attr(open, "r") ?: return@forEachElement
            val (row, col) = parseCellRef(ref) ?: return@forEachElement
            if (options.skipHiddenRows && row in hiddenRows) return@forEachElement
            if (col >= options.maxColumns) {
                truncated = true
                return@forEachElement
            }
            if (options.maxRows != null && row >= options.maxRows + 1) {
                truncated = true
                return@forEachElement
            }
            val type = attr(open, "t")
            val styleIdx = attr(open, "s")?.toIntOrNull()
            val inner = if (innerBytes.isEmpty()) "" else innerBytes.decodeToString()
            var raw = when (type) {
                "inlineStr" -> SimpleXml.firstText(inner, "t").orEmpty()
                "s" -> {
                    val idx = SimpleXml.firstText(inner, "v")?.toIntOrNull()
                    if (idx != null) sharedStrings[idx].orEmpty() else ""
                }
                "b" -> when (SimpleXml.firstText(inner, "v")) {
                    "1" -> "TRUE"
                    "0" -> "FALSE"
                    else -> SimpleXml.firstText(inner, "v").orEmpty()
                }
                else -> SimpleXml.firstText(inner, "v").orEmpty()
            }
            if (raw.isEmpty()) {
                val f = SimpleXml.firstText(inner, "f")
                if (!f.isNullOrBlank()) raw = "=$f"
            }
            val value = ExcelValueFormatter.format(
                raw = raw,
                type = type,
                styleIndex = styleIdx,
                styles = styles,
                enabled = options.formatExcelValues,
                date1904 = date1904,
            )
            grid.getOrPut(row) { mutableMapOf() }[col] = value
            if (col > maxCol) maxCol = col
            cellCount++
            if (cellCount > options.maxCells) {
                truncated = true
                hitCellCap = true
                warnings += "Sheet truncated at maxCells=${options.maxCells}"
            }
        }

        if (options.fillMergedCells) {
            // Decode only merge region if present; else skip.
            val mergeStart = indexOfAscii(bytes, 0, "mergeCells")
            if (mergeStart >= 0) {
                val mergeEnd = indexOfAscii(bytes, mergeStart, "</mergeCells>")
                val slice = if (mergeEnd > mergeStart) {
                    bytes.copyOfRange(mergeStart, minOf(bytes.size, mergeEnd + 13))
                } else {
                    bytes.copyOfRange(mergeStart, minOf(bytes.size, mergeStart + 256_000))
                }
                applyMerges(slice.decodeToString(), grid, options.maxColumns)
            }
        }
        if (grid.isEmpty()) return XlsxParserSheetBuild(emptyList(), warnings, truncated)

        SimpleFormulaEvaluator.evaluateGrid(grid)

        val minRow = grid.keys.minOrNull() ?: 0
        val maxRow = grid.keys.maxOrNull() ?: 0
        val width = (maxCol + 1).coerceAtMost(options.maxColumns)
        val matrix = ArrayList<List<String>>(grid.size)
        for (r in minRow..maxRow) {
            val rowMap = grid[r] ?: continue
            if (options.skipHiddenRows && r in hiddenRows) continue
            val line = MutableList(width) { "" }
            for ((c, v) in rowMap) {
                if (c in 0 until width) line[c] = v
            }
            matrix += line
            if (options.maxRows != null && matrix.size > options.maxRows + 1) {
                truncated = true
                break
            }
        }
        if (truncated) warnings += "Rows/columns truncated by safety limits (maxRows/maxColumns/maxCells)."
        return XlsxParserSheetBuild(matrix, warnings, truncated)
    }

    private fun forEachOpenTag(bytes: ByteArray, tag: String, block: (open: String) -> Unit) {
        var i = 0
        val needle = "<$tag"
        while (i < bytes.size) {
            val at = indexOfAscii(bytes, i, needle)
            if (at < 0) break
            val afterTag = at + needle.length
            if (afterTag < bytes.size) {
                val ch = bytes[afterTag].toInt() and 0xff
                if (ch != ' '.code && ch != '>'.code && ch != '/'.code && ch != '\t'.code &&
                    ch != '\n'.code && ch != '\r'.code
                ) {
                    i = afterTag
                    continue
                }
            }
            val gt = indexOfByte(bytes, afterTag, '>'.code.toByte())
            if (gt < 0) break
            block(bytes.decodeToString(at, gt + 1))
            i = gt + 1
        }
    }

    private fun forEachElement(
        bytes: ByteArray,
        tag: String,
        block: (open: String, inner: ByteArray) -> Unit,
    ) {
        var i = 0
        val openNeedle = "<$tag"
        val closeNeedle = "</$tag>"
        while (i < bytes.size) {
            val at = indexOfAscii(bytes, i, openNeedle)
            if (at < 0) break
            val afterTag = at + openNeedle.length
            if (afterTag < bytes.size) {
                val ch = bytes[afterTag].toInt() and 0xff
                if (ch != ' '.code && ch != '>'.code && ch != '/'.code && ch != '\t'.code &&
                    ch != '\n'.code && ch != '\r'.code
                ) {
                    i = afterTag
                    continue
                }
            }
            val gt = indexOfByte(bytes, afterTag, '>'.code.toByte())
            if (gt < 0) break
            val open = bytes.decodeToString(at, gt + 1)
            if (bytes[gt - 1] == '/'.code.toByte()) {
                block(open, ByteArray(0))
                i = gt + 1
                continue
            }
            val close = indexOfAscii(bytes, gt + 1, closeNeedle)
            if (close < 0) {
                i = gt + 1
                continue
            }
            block(open, bytes.copyOfRange(gt + 1, close))
            i = close + closeNeedle.length
        }
    }

    private fun attr(openTag: String, name: String): String? = SimpleXml.attr(openTag, name)

    private fun indexOfAscii(bytes: ByteArray, from: Int, needle: String): Int {
        if (needle.isEmpty() || from >= bytes.size) return -1
        val n = needle.encodeToByteArray()
        outer@ for (i in from..bytes.size - n.size) {
            for (k in n.indices) {
                if (bytes[i + k] != n[k]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun indexOfByte(bytes: ByteArray, from: Int, b: Byte): Int {
        for (i in from until bytes.size) if (bytes[i] == b) return i
        return -1
    }

    private fun ByteArray.decodeToString(start: Int, endExclusive: Int): String =
        copyOfRange(start, endExclusive).decodeToString()
}

/** Shared sheet-build result (avoids exposing XlsxParser private type). */
internal data class XlsxParserSheetBuild(
    val matrix: List<List<String>>,
    val warnings: List<String>,
    val truncated: Boolean,
)
