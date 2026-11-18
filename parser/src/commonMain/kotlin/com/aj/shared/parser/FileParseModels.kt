package com.aj.shared.parser

enum class FileParseFormat {
    CSV,
    TSV,
    XLSX,
    XLS,
    VCARD,
    UNKNOWN,
}

data class FileParseOptions(
    val hasHeader: Boolean = true,
    /** When true, infer header from first vs second row shape (overrides [hasHeader] if confident). */
    val autoDetectHeader: Boolean = false,
    val delimiter: Char? = null,
    val sheetIndex: Int = 0,
    val sheetName: String? = null,
    val allSheets: Boolean = false,
    /** Cap data rows (null = unlimited). Viewer defaults to a safe cap. */
    val maxRows: Int? = null,
    /** Hard cap on columns to avoid sparse/wide OOM. */
    val maxColumns: Int = 512,
    /** Abort if densified cell count would exceed this. */
    val maxCells: Int = 4_000_000,
    /** Cap shared-strings table size (extra indices → empty + warning). */
    val maxSharedStrings: Int = 1_000_000,
    /** Refuse to fully decode a worksheet XML larger than this (bytes). */
    val maxSheetXmlBytes: Int = 192 * 1024 * 1024,
    /**
     * Keep inflated sheet XML between SST-index pass and parse when each sheet is
     * at or under this size (avoids 2× inflate). Larger sheets re-inflate + byte-parse.
     */
    val retainSheetXmlBytes: Int = 32 * 1024 * 1024,
    /** Append a `_meta` sheet with chart/pivot/validation hints when present. */
    val includeWorkbookMeta: Boolean = true,
    /** Keep vCard PHOTO base64 as data-URL when under this many decoded bytes (0 = never). */
    val maxVCardPhotoBytes: Int = 16 * 1024,
    /**
     * Force charset: UTF-8 / UTF-16 / Windows-1252 / Shift-JIS / GBK / … (platform-dependent).
     * Null = auto BOM + UTF-8 / 1252 fallback.
     */
    val charsetName: String? = null,
    val uniquifyHeaders: Boolean = true,
    val fillMergedCells: Boolean = true,
    val formatExcelValues: Boolean = true,
    val skipHiddenRows: Boolean = true,
    /** null = read workbookPr date1904; true/false force. */
    val date1904: Boolean? = null,
    /** Prepend stable row id column ("id") = 1-based data row number. */
    val includeRowId: Boolean = false,
    val prettyJson: Boolean = false,
    /**
     * Password for OOXML Agile-encrypted workbooks (`.xlsx` saved with Encrypt).
     * Uses platform crypto (AES/SHA) — not a third-party library.
     */
    val password: String? = null,
    /** Cooperative cancel — return false to abort long parses. */
    val isActive: () -> Boolean = { true },
) {
    companion object {
        /** High ceilings for power users / server-side batch jobs. */
        fun unlimited(
            password: String? = null,
            isActive: () -> Boolean = { true },
        ): FileParseOptions = FileParseOptions(
            maxRows = null,
            maxColumns = 16_384,
            maxCells = Int.MAX_VALUE / 4,
            maxSharedStrings = Int.MAX_VALUE / 8,
            maxSheetXmlBytes = Int.MAX_VALUE / 4,
            retainSheetXmlBytes = 64 * 1024 * 1024,
            maxVCardPhotoBytes = 64 * 1024,
            password = password,
            isActive = isActive,
        )
    }
}

data class ParsedSheet(
    val name: String,
    val columns: List<String>,
    val rows: List<Map<String, String>>,
)

data class ParsedChart(
    val title: String,
    val categories: List<String>,
    val values: List<Double>,
)

data class ParseSuccess(
    val format: FileParseFormat,
    val sheets: List<ParsedSheet>,
    val sourceName: String? = null,
    val warnings: List<String> = emptyList(),
    val truncated: Boolean = false,
    /** Data rows available in [sheets] before any UI windowing (null = same as sheet size). */
    val loadedDataRows: Int? = null,
    /** Extracted chart series (bar-friendly); empty when none / not rendered as Excel charts. */
    val charts: List<ParsedChart> = emptyList(),
) {
    val rows: List<Map<String, String>> get() = sheets.firstOrNull()?.rows.orEmpty()
    val columns: List<String> get() = sheets.firstOrNull()?.columns.orEmpty()

    /**
     * UI helper: show only the first [maxDataRows] data rows without re-parsing.
     * Header/columns unchanged; [truncated] stays true if more rows remain in this payload
     * or the original parse was truncated.
     */
    fun takeDataRows(maxDataRows: Int): ParseSuccess {
        if (maxDataRows < 0) return this
        val sliced = sheets.map { sheet ->
            if (sheet.rows.size <= maxDataRows) sheet
            else sheet.copy(rows = sheet.rows.take(maxDataRows))
        }
        val loaded = loadedDataRows ?: sheets.maxOfOrNull { it.rows.size } ?: 0
        val moreInMemory = sliced.zip(sheets).any { (a, b) -> a.rows.size < b.rows.size }
        return copy(
            sheets = sliced,
            truncated = truncated || moreInMemory,
            loadedDataRows = loaded,
        )
    }

    /**
     * Apply in-viewer cell edits. Keys are `"rowIndex|column"` using the sheet's
     * original 0-based data-row index (not filtered/sorted display index).
     */
    fun withCellEdits(sheetIndex: Int, edits: Map<String, String>): ParseSuccess {
        if (edits.isEmpty()) return this
        val byRow = HashMap<Int, MutableMap<String, String>>()
        for ((key, value) in edits) {
            val bar = key.indexOf('|')
            if (bar <= 0) continue
            val row = key.substring(0, bar).toIntOrNull() ?: continue
            val col = key.substring(bar + 1)
            byRow.getOrPut(row) { mutableMapOf() }[col] = value
        }
        if (byRow.isEmpty()) return this
        val updated = sheets.mapIndexed { i, sheet ->
            if (i != sheetIndex) sheet
            else {
                val newRows = sheet.rows.mapIndexed { ri, row ->
                    val patch = byRow[ri] ?: return@mapIndexed row
                    row + patch
                }
                sheet.copy(rows = newRows)
            }
        }
        return copy(sheets = updated)
    }

    fun toJsonString(pretty: Boolean = false): String = JsonWriter.write(this, pretty)

    fun toCsvString(sheetIndex: Int = 0): String {
        val sheet = sheets.getOrNull(sheetIndex) ?: return ""
        return buildString {
            append(sheet.columns.joinToString(",") { csvEscape(it) })
            append('\n')
            sheet.rows.forEach { row ->
                append(sheet.columns.joinToString(",") { csvEscape(row[it].orEmpty()) })
                append('\n')
            }
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}

sealed class ParseResult {
    data class Success(val value: ParseSuccess) : ParseResult()
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val format: FileParseFormat = FileParseFormat.UNKNOWN,
        /** Machine-friendly: ENCRYPTED, TOO_LARGE, UNSUPPORTED, IO, CANCELLED, … */
        val code: String? = null,
    ) : ParseResult()

    fun getOrNull(): ParseSuccess? = (this as? Success)?.value

    fun getOrThrow(): ParseSuccess = when (this) {
        is Success -> value
        is Error -> throw IllegalStateException(message, cause)
    }
}

/** Hand-written JSON encoder — no serialization libraries. */
internal object JsonWriter {
    fun write(success: ParseSuccess, pretty: Boolean): String {
        val indent = if (pretty) "  " else null
        return if (success.sheets.size <= 1) {
            writeRows(success.rows, indent, 0)
        } else {
            buildString {
                append('{')
                if (pretty) append('\n')
                success.sheets.forEachIndexed { index, sheet ->
                    if (pretty) append(indent)
                    append('"').append(escape(sheet.name)).append('"').append(':')
                    if (pretty) append(' ')
                    append(writeRows(sheet.rows, indent, 1))
                    if (index != success.sheets.lastIndex) append(',')
                    if (pretty) append('\n')
                }
                append('}')
            }
        }
    }

    private fun writeRows(rows: List<Map<String, String>>, indent: String?, depth: Int): String =
        buildString {
            append('[')
            if (prettyNewline(indent) && rows.isNotEmpty()) append('\n')
            rows.forEachIndexed { rowIndex, row ->
                if (indent != null) append(indent.repeat(depth + 1))
                append('{')
                val entries = row.entries.toList()
                entries.forEachIndexed { i, (k, v) ->
                    append('"').append(escape(k)).append('"').append(':')
                    if (indent != null) append(' ')
                    append(jsonValue(v))
                    if (i != entries.lastIndex) append(',')
                    if (indent != null && i != entries.lastIndex) append(' ')
                }
                append('}')
                if (rowIndex != rows.lastIndex) append(',')
                if (prettyNewline(indent)) append('\n')
            }
            if (indent != null && rows.isNotEmpty()) append(indent.repeat(depth))
            append(']')
        }

    /** Numbers / bools as JSON literals; everything else quoted string. */
    private fun jsonValue(value: String): String {
        when {
            value.equals("true", ignoreCase = true) -> return "true"
            value.equals("false", ignoreCase = true) -> return "false"
            value.isBlank() -> return "\"\""
        }
        val asNum = value.toLongOrNull()
        if (asNum != null) return asNum.toString()
        val asDouble = value.toDoubleOrNull()
        if (asDouble != null && value.matches(Regex("""^-?\d+(\.\d+)?([eE][+-]?\d+)?$"""))) {
            return value
        }
        return "\"${escape(value)}\""
    }

    private fun prettyNewline(indent: String?): Boolean = indent != null

    private fun escape(value: String): String = buildString(value.length + 8) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
    }
}
