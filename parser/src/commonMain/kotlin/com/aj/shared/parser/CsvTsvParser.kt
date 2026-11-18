package com.aj.shared.parser

/**
 * Custom RFC4180-style CSV/TSV parser — encoding auto-detect, quote recovery, unique headers.
 */
internal object CsvTsvParser {

    fun parse(
        bytes: ByteArray,
        format: FileParseFormat,
        options: FileParseOptions,
        sourceName: String?,
    ): ParseResult {
        return try {
            if (!options.isActive()) {
                return ParseResult.Error("Parse cancelled", format = format)
            }
            val text = TextDecoder.decode(bytes, options.charsetName)
            val delimiter = options.delimiter
                ?: if (format == FileParseFormat.TSV) '\t' else detectDelimiter(text)
            val matrix = parseMatrix(text, delimiter)
            val widthCapped = matrix.map { it.take(options.maxColumns) }
            val warnings = mutableListOf<String>()
            var truncated = false
            if (matrix.any { it.size > options.maxColumns }) {
                truncated = true
                warnings += "CSV truncated to maxColumns=${options.maxColumns}"
            }
            val dataRowCount = (widthCapped.size - if (options.hasHeader && widthCapped.isNotEmpty()) 1 else 0)
                .coerceAtLeast(0)
            if (options.maxRows != null && dataRowCount > options.maxRows) {
                truncated = true
                warnings += "CSV truncated to maxRows=${options.maxRows}"
            }
            val sheet = matrixToSheet(
                name = "Sheet1",
                matrix = widthCapped,
                hasHeader = options.hasHeader,
                maxRows = options.maxRows,
                uniquifyHeaders = options.uniquifyHeaders,
                includeRowId = options.includeRowId,
                autoDetectHeader = options.autoDetectHeader,
            )
            ParseResult.Success(
                ParseSuccess(
                    format = format,
                    sheets = listOf(sheet),
                    sourceName = sourceName,
                    warnings = warnings,
                    truncated = truncated,
                ),
            )
        } catch (t: Throwable) {
            ParseResult.Error(
                message = "Failed to parse ${format.name}: ${t.message ?: t::class.simpleName}",
                cause = t,
                format = format,
            )
        }
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        fun countOutsideQuotes(ch: Char): Int {
            var count = 0
            var inQuotes = false
            var i = 0
            while (i < firstLine.length) {
                val c = firstLine[i]
                when {
                    c == '"' -> {
                        if (inQuotes && i + 1 < firstLine.length && firstLine[i + 1] == '"') {
                            i++
                        } else {
                            inQuotes = !inQuotes
                        }
                    }
                    !inQuotes && c == ch -> count++
                }
                i++
            }
            return count
        }
        val comma = countOutsideQuotes(',')
        val semi = countOutsideQuotes(';')
        val tab = countOutsideQuotes('\t')
        val pipe = countOutsideQuotes('|')
        return listOf(',' to comma, ';' to semi, '\t' to tab, '|' to pipe)
            .maxBy { it.second }
            .let { (ch, n) -> if (n > 0) ch else ',' }
    }

    internal fun parseMatrix(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        val row = mutableListOf<String>()
        var i = 0
        var inQuotes = false
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == delimiter -> {
                    row += field.toString()
                    field.clear()
                }
                ch == '\n' -> {
                    row += field.toString()
                    field.clear()
                    if (row.any { it.isNotEmpty() } || row.size > 1) {
                        rows += row.toList()
                    }
                    row.clear()
                }
                ch == '\r' -> Unit
                else -> field.append(ch)
            }
            i++
        }
        // Unclosed quote recovery: flush remaining field/row
        if (field.isNotEmpty() || row.isNotEmpty() || inQuotes) {
            row += field.toString()
            if (row.any { it.isNotEmpty() } || row.size > 1) {
                rows += row.toList()
            }
        }
        return rows
    }
}

internal fun matrixToSheet(
    name: String,
    matrix: List<List<String>>,
    hasHeader: Boolean,
    maxRows: Int?,
    uniquifyHeaders: Boolean = true,
    includeRowId: Boolean = false,
    autoDetectHeader: Boolean = false,
): ParsedSheet {
    if (matrix.isEmpty()) return ParsedSheet(name, emptyList(), emptyList())
    val useHeader = if (autoDetectHeader) detectHeaderRow(matrix) else hasHeader
    val rawColumns: List<String>
    val data: List<List<String>>
    if (useHeader) {
        rawColumns = matrix.first().mapIndexed { index, value ->
            value.trim().ifBlank { "column_${index + 1}" }
        }
        data = matrix.drop(1)
    } else {
        val width = matrix.maxOf { it.size }
        rawColumns = (1..width).map { "column_$it" }
        data = matrix
    }
    var columns = if (uniquifyHeaders) uniquify(rawColumns) else rawColumns
    if (includeRowId) {
        columns = columns.map { if (it.equals("id", ignoreCase = true)) "id_col" else it }
        columns = listOf("id") + columns
    }
    val limited = maxRows?.let { data.take(it) } ?: data
    val rows = limited.mapIndexed { index, cells ->
        val ordered = linkedMapOf<String, String>()
        if (includeRowId) {
            ordered["id"] = (index + 1).toString()
            val dataCols = columns.drop(1)
            dataCols.forEachIndexed { colIndex, col ->
                ordered[col] = cells.getOrNull(colIndex)?.trim().orEmpty()
            }
        } else {
            columns.forEachIndexed { colIndex, col ->
                ordered[col] = cells.getOrNull(colIndex)?.trim().orEmpty()
            }
        }
        ordered
    }
    return ParsedSheet(name, columns, rows)
}

internal fun uniquify(headers: List<String>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return headers.map { raw ->
        val base = raw.ifBlank { "column" }
        val count = seen[base] ?: 0
        seen[base] = count + 1
        if (count == 0) base else "${base}_${count + 1}"
    }
}

/** Heuristic: first row looks like labels if second row is more numeric/date-like. */
internal fun detectHeaderRow(matrix: List<List<String>>): Boolean {
    if (matrix.size < 2) return true
    val first = matrix[0]
    val second = matrix[1]
    fun score(row: List<String>): Int = row.sumOf { cell ->
        val t = cell.trim()
        when {
            t.isEmpty() -> 0
            t.toDoubleOrNull() != null -> 2
            t.matches(Regex("""\d{4}-\d{2}-\d{2}.*""")) -> 2
            t.equals("true", true) || t.equals("false", true) -> 2
            else -> 0
        }
    }
    val s1 = score(first)
    val s2 = score(second)
    // Header if first is less "data-like" than second
    return s1 < s2 || (s1 == 0 && first.any { it.isNotBlank() })
}
