package com.aj.shared.parser

/**
 * Standalone **custom** file → JSON parser.
 *
 * No third-party Excel/CSV libraries — Kotlin + system DEFLATE (JDK Inflater / Apple zlib).
 *
 * Works on **Android, iOS, and Desktop** for XLSX / CSV / TSV / vCard
 * (JS/Wasm needs a platform DEFLATE backend — not shipped in this module).
 *
 * ```kotlin
 * // settings.gradle.kts maven("https://imajy.github.io/eazyCmp/maven-repo/")
 * implementation("com.github.Imajy.eazyCmp:parser:<version>")
 *
 * val json = EazyFileParser.parseToJson(bytes, fileName = "data.xlsx")
 * // or
 * val rows = EazyFileParser.parse(bytes, fileName = "people.csv").getOrThrow().rows
 * ```
 *
 * Formats: **XLSX**, **CSV**, **TSV**, **vCard** (.vcf). Legacy **.xls** → convert to xlsx/csv.
 */
object EazyFileParser {

    fun parse(
        bytes: ByteArray,
        fileName: String? = null,
        mimeType: String? = null,
        options: FileParseOptions = FileParseOptions(),
    ): ParseResult {
        if (bytes.isEmpty()) return ParseResult.Error("File is empty")
        val format = FormatDetector.detect(bytes, fileName, mimeType)
        return parseAs(bytes, format, options, fileName)
    }

    /**
     * Parse from a filesystem path.
     * For `.xlsx`, uses a random-access [BinarySource] (JVM/Android) so the whole file
     * need not sit in a [ByteArray]. Other formats still load bytes once.
     */
    fun parseFile(
        path: String,
        fileName: String? = null,
        mimeType: String? = null,
        options: FileParseOptions = FileParseOptions(),
    ): ParseResult {
        val name = fileName ?: path.substringAfterLast('/').substringAfterLast('\\')
        val lower = name.lowercase()
        val size = fileSizeBytes(path)
        if (size != null && size > options.maxSheetXmlBytes.toLong() * 4) {
            return ParseResult.Error(
                message = "File is very large (${size} bytes). Raise caps or split the workbook.",
                format = FileParseFormat.XLSX,
                code = "TOO_LARGE",
            )
        }
        // Prefer path-backed ZIP for OOXML (not OLE .xls).
        if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm") ||
            mimeType?.contains("spreadsheetml", true) == true
        ) {
            val source = PlatformBinarySource.openFile(path)
            if (source != null) {
                return XlsxParser.parseSource(source, options, name, closeSource = true)
            }
        }
        val bytes = readFileBytes(path)
            ?: return ParseResult.Error("Could not read file: $path", code = "IO")
        return parse(bytes, name, mimeType, options)
    }

    /** Raw path → bytes (platform file I/O). Prefer [parseFile] when you only need parsed data. */
    fun readFileBytes(path: String): ByteArray? = PlatformFileBytes.readOrNull(path)

    /** Path → size without loading contents (for preflight / too-large checks). */
    fun fileSizeBytes(path: String): Long? = PlatformFileBytes.lengthOrNull(path)

    /** Spill a large buffer to a temp file so [parseFile] can run without retaining bytes. */
    fun writeTempFile(bytes: ByteArray, suffix: String = ".bin"): String? =
        PlatformFileBytes.writeTemp(bytes, suffix)

    fun deleteTempFile(path: String) = PlatformFileBytes.deleteQuietly(path)

    /**
     * Export parsed table(s) as a minimal `.xlsx` (typed values + `=` formulas + freeze).
     * Pass [sheetIndex] = `-1` to write every sheet.
     */
    fun exportXlsx(
        data: ParseSuccess,
        sheetIndex: Int = 0,
        freezeColumns: Int = 1,
    ): ByteArray = XlsxWriter.write(data, sheetIndex, freezeColumns)

    fun listExcelSheetNamesFromPath(
        path: String,
        isActive: () -> Boolean = { true },
    ): List<String> {
        val lower = path.lowercase()
        if (!lower.endsWith(".xls") && !lower.endsWith(".xlt")) {
            PlatformBinarySource.openFile(path)?.let { src ->
                try {
                    val names = XlsxParser.listSheetNames(src, isActive, closeSource = true)
                    if (names.isNotEmpty()) return names
                } catch (_: Throwable) {
                    // fall through to byte parse
                }
            }
        }
        val bytes = readFileBytes(path) ?: return emptyList()
        return listExcelSheetNames(bytes, isActive)
    }

    fun parseToJson(
        bytes: ByteArray,
        fileName: String? = null,
        mimeType: String? = null,
        options: FileParseOptions = FileParseOptions(),
    ): String = parse(bytes, fileName, mimeType, options)
        .getOrThrow()
        .toJsonString(pretty = options.prettyJson)

    fun parseCsv(
        bytes: ByteArray,
        options: FileParseOptions = FileParseOptions(),
        fileName: String? = null,
    ): ParseResult = parseAs(bytes, FileParseFormat.CSV, options, fileName)

    fun parseTsv(
        bytes: ByteArray,
        options: FileParseOptions = FileParseOptions(),
        fileName: String? = null,
    ): ParseResult = parseAs(bytes, FileParseFormat.TSV, options, fileName)

    fun parseExcel(
        bytes: ByteArray,
        options: FileParseOptions = FileParseOptions(),
        fileName: String? = null,
    ): ParseResult {
        val format = FormatDetector.detect(bytes, fileName, null).let {
            if (it == FileParseFormat.XLS) FileParseFormat.XLS else FileParseFormat.XLSX
        }
        return parseAs(bytes, format, options, fileName)
    }

    fun parseVCard(
        bytes: ByteArray,
        options: FileParseOptions = FileParseOptions(),
        fileName: String? = null,
    ): ParseResult = parseAs(bytes, FileParseFormat.VCARD, options, fileName)

    /** List XLSX sheet names without parsing every worksheet (lazy). */
    fun listExcelSheetNames(
        bytes: ByteArray,
        isActive: () -> Boolean = { true },
    ): List<String> = try {
        if (bytes.size >= 4 &&
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte()
        ) {
            listOf("Sheet1")
        } else {
            XlsxParser.listSheetNames(bytes, isActive)
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun parseAs(
        bytes: ByteArray,
        format: FileParseFormat,
        options: FileParseOptions,
        sourceName: String?,
    ): ParseResult = when (format) {
        FileParseFormat.CSV, FileParseFormat.TSV ->
            CsvTsvParser.parse(bytes, format, options, sourceName)
        FileParseFormat.XLSX, FileParseFormat.XLS ->
            ExcelParser.parse(bytes, format, options, sourceName)
        FileParseFormat.VCARD ->
            VCardParser.parse(bytes, options, sourceName)
        FileParseFormat.UNKNOWN ->
            ParseResult.Error(
                message = "Could not detect format. Pass fileName (.xlsx/.csv/.tsv/.vcf) or call parseCsv/parseExcel/parseVCard.",
            )
    }
}
