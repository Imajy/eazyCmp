package com.aj.shared.parser

/** Custom XLSX — lazy ZIP inflate, sparse SST, hidden rows, 1904 dates, formula fallback. */
internal object XlsxParser {

    fun parse(
        bytes: ByteArray,
        options: FileParseOptions,
        sourceName: String?,
    ): ParseResult = parseSource(ByteArraySource(bytes), options, sourceName, closeSource = false)

    fun parseSource(
        source: BinarySource,
        options: FileParseOptions,
        sourceName: String?,
        closeSource: Boolean = true,
    ): ParseResult {
        var zip: ZipArchive? = null
        return try {
            if (!options.isActive()) {
                return ParseResult.Error("Parse cancelled", format = FileParseFormat.XLSX)
            }
            zip = ZipReader.open(source, options.isActive, closeSource = closeSource)
            val warnings = mutableListOf<String>()
            var truncated = false

            val workbookXml = zip.readString("xl/workbook.xml")
            val date1904 = options.date1904 ?: detectDate1904(workbookXml)
            val styles = ExcelValueFormatter.parseStyles(zip.readStringAndEvict("xl/styles.xml"))
            val sheetFiles = resolveSheets(zip, workbookXml)
            zip.evict("xl/workbook.xml")
            zip.evict("xl/_rels/workbook.xml.rels")
            if (sheetFiles.isEmpty()) {
                return ParseResult.Error("No worksheets found in XLSX", format = FileParseFormat.XLSX)
            }

            val selected = if (options.allSheets) {
                sheetFiles
            } else {
                val byName = options.sheetName?.let { name ->
                    sheetFiles.firstOrNull { it.first.equals(name, ignoreCase = true) }
                }
                listOf(
                    byName
                        ?: sheetFiles.getOrNull(options.sheetIndex)
                        ?: sheetFiles.first(),
                )
            }

            // Hybrid two-pass: collect SST indices; retain small sheet XML to avoid
            // 2× inflate; re-inflate only oversized sheets (lower peak RAM).
            data class SheetJob(val name: String, val path: String, var xml: String?)
            val sheetJobs = ArrayList<SheetJob>(selected.size)
            val neededSst = linkedSetOf<Int>()
            val validationHints = mutableListOf<String>()
            var sawComments = zip.names.any { it.contains("comments", ignoreCase = true) }
            var sawSheetLock = false
            var freezeHint: String? = null
            for ((name, path) in selected) {
                if (!options.isActive()) throw ParseCancelledException("Parse cancelled")
                if (!zip.contains(path)) {
                    return ParseResult.Error("Missing sheet part: $path", format = FileParseFormat.XLSX)
                }
                val sheetBytes = zip.read(path)
                    ?: return ParseResult.Error("Missing sheet part: $path", format = FileParseFormat.XLSX)
                if (sheetBytes.size > options.maxSheetXmlBytes) {
                    return ParseResult.Error(
                        message = "Worksheet \"$name\" XML is too large (>${options.maxSheetXmlBytes} bytes). " +
                            "Raise FileParseOptions.maxSheetXmlBytes or export a smaller sheet.",
                        format = FileParseFormat.XLSX,
                        code = "TOO_LARGE",
                    )
                }
                val retain = sheetBytes.size <= options.retainSheetXmlBytes
                if (retain) {
                    val xml = sheetBytes.decodeToString()
                    zip.evict(path)
                    collectSstIndices(xml, neededSst, options.maxSharedStrings)
                    collectValidationHints(xml, validationHints)
                    if (xml.contains("comments", ignoreCase = true)) sawComments = true
                    if (xml.contains("sheetProtection", ignoreCase = true)) sawSheetLock = true
                    if (freezeHint == null) freezeHint = detectFreezePane(xml)
                    sheetJobs += SheetJob(name, path, xml)
                } else {
                    // Large sheet: ASCII byte-scan for SST indices — no UTF-16 String peak in pass 1.
                    collectSstIndicesFromBytes(sheetBytes, neededSst, options.maxSharedStrings)
                    if (containsAscii(sheetBytes, "dataValidation")) {
                        validationHints += "list@large-sheet"
                    }
                    if (containsAscii(sheetBytes, "comments")) sawComments = true
                    if (containsAscii(sheetBytes, "sheetProtection")) sawSheetLock = true
                    if (freezeHint == null && containsAscii(sheetBytes, "state=\"frozen")) {
                        freezeHint = "frozen (large sheet)"
                    }
                    zip.evict(path)
                    sheetJobs += SheetJob(name, path, null)
                }
            }
            if (validationHints.isNotEmpty()) {
                warnings += "Data validation present (values only): " +
                    validationHints.take(6).joinToString("; ") +
                    if (validationHints.size > 6) "…" else ""
            }
            if (sawComments) {
                warnings += "Cell comments are not shown."
            }
            if (sawSheetLock) {
                warnings += "Sheet is locked for editing in Excel; cell values are still readable."
            }
            freezeHint?.let { warnings += "Excel freeze pane detected: $it (viewer freeze is independent)." }

            val sstRaw = zip.read("xl/sharedStrings.xml")
            if (sstRaw != null && sstRaw.size > options.maxSheetXmlBytes) {
                return ParseResult.Error(
                    message = "sharedStrings.xml is too large (>${options.maxSheetXmlBytes} bytes). " +
                        "Raise maxSheetXmlBytes or split the workbook.",
                    format = FileParseFormat.XLSX,
                    code = "TOO_LARGE",
                )
            }
            val sstXml = sstRaw?.decodeToString().orEmpty()
            zip.evict("xl/sharedStrings.xml")
            val sstParsed = parseSharedStringsSparse(sstXml, neededSst, options.maxSharedStrings)
            if (sstParsed.truncated) {
                warnings += "Shared strings truncated at maxSharedStrings=${options.maxSharedStrings}"
                truncated = true
            }

            val sheets = ArrayList<ParsedSheet>(sheetJobs.size)
            for (job in sheetJobs) {
                if (!options.isActive()) throw ParseCancelledException("Parse cancelled")
                val retained = job.xml
                job.xml = null
                val built = if (retained != null) {
                    parseSheetSparse(retained, sstParsed.strings, styles, options, date1904)
                } else {
                    // Oversized sheet: parse UTF-8 bytes without one giant String.
                    val sheetBytes = zip.read(job.path)
                        ?: return ParseResult.Error("Missing sheet part: ${job.path}", format = FileParseFormat.XLSX)
                    zip.evict(job.path)
                    val fromBytes = SheetXmlByteParser.parse(
                        bytes = sheetBytes,
                        sharedStrings = sstParsed.strings,
                        styles = styles,
                        options = options,
                        date1904 = date1904,
                        applyMerges = { xml, grid, maxCols -> applyMerges(xml, grid, maxCols) },
                        parseCellRef = ::parseCellRef,
                    )
                    SheetBuild(fromBytes.matrix, fromBytes.warnings, fromBytes.truncated)
                }
                warnings += built.warnings
                if (built.truncated) truncated = true
                sheets += matrixToSheet(
                    name = job.name,
                    matrix = built.matrix,
                    hasHeader = options.hasHeader,
                    maxRows = options.maxRows,
                    uniquifyHeaders = options.uniquifyHeaders,
                    includeRowId = options.includeRowId,
                    autoDetectHeader = options.autoDetectHeader,
                )
            }

            val chartParts = zip.names.filter {
                val p = it.replace('\\', '/')
                p.contains("/charts/chart", true) || p.contains("pivotCache", true) ||
                    p.contains("/drawings/", true) || p.contains("/media/", true)
            }
            if (chartParts.isNotEmpty()) {
                val sample = chartParts.take(5).joinToString(", ") { it.substringAfterLast('/') }
                warnings += "Charts / pivots / images not rendered ($sample); cell values only."
            }
            if (options.includeWorkbookMeta && (chartParts.isNotEmpty() || validationHints.isNotEmpty())) {
                sheets += buildMetaSheet(chartParts, validationHints, freezeHint)
            }
            val charts = try {
                ChartDataExtractor.extract(zip).map {
                    ParsedChart(it.title, it.categories, it.values)
                }
            } catch (_: Throwable) {
                emptyList()
            }
            if (charts.isNotEmpty()) {
                warnings += "Extracted ${charts.size} chart series for viewer preview."
            }

            ParseResult.Success(
                ParseSuccess(
                    format = FileParseFormat.XLSX,
                    sheets = sheets,
                    sourceName = sourceName,
                    warnings = warnings.distinct(),
                    truncated = truncated,
                    loadedDataRows = sheets.filter { it.name != "_meta" }.maxOfOrNull { it.rows.size },
                    charts = charts,
                ),
            )
        } catch (t: ParseCancelledException) {
            ParseResult.Error(t.message ?: "Parse cancelled", cause = t, format = FileParseFormat.XLSX, code = "CANCELLED")
        } catch (t: EncryptedWorkbookException) {
            ParseResult.Error(t.message ?: "Encrypted workbook", cause = t, format = FileParseFormat.XLSX, code = "ENCRYPTED")
        } catch (t: UnsupportedZipException) {
            ParseResult.Error(t.message ?: "Unsupported ZIP", cause = t, format = FileParseFormat.XLSX, code = "UNSUPPORTED")
        } catch (t: Throwable) {
            val msg = t.message ?: "Failed to parse XLSX: ${t::class.simpleName}"
            ParseResult.Error(
                message = msg,
                cause = t,
                format = FileParseFormat.XLSX,
                code = when {
                    msg.contains("password", true) || msg.contains("encrypt", true) -> "ENCRYPTED"
                    msg.contains("too large", true) || msg.contains(">2GB", true) -> "TOO_LARGE"
                    msg.contains("Unsupported ZIP", true) || msg.contains("compression method", true) -> "UNSUPPORTED"
                    else -> null
                },
            )
        } finally {
            zip?.close()
        }
    }

    /** Sheet names without inflating worksheet XMLs. */
    fun listSheetNames(bytes: ByteArray, isActive: () -> Boolean = { true }): List<String> =
        listSheetNames(ByteArraySource(bytes), isActive, closeSource = false)

    fun listSheetNames(
        source: BinarySource,
        isActive: () -> Boolean = { true },
        closeSource: Boolean = true,
    ): List<String> {
        val zip = ZipReader.open(source, isActive, closeSource)
        return try {
            resolveSheets(zip, zip.readString("xl/workbook.xml")).map { it.first }
        } finally {
            zip.close()
        }
    }

    private data class SheetBuild(
        val matrix: List<List<String>>,
        val warnings: List<String>,
        val truncated: Boolean,
    )

    private data class SstParse(val strings: Map<Int, String>, val truncated: Boolean)

    private fun detectDate1904(workbookXml: String): Boolean {
        var found = false
        SimpleXml.forEachElement(workbookXml, "workbookPr") { open, _ ->
            val v = SimpleXml.attr(open, "date1904")
            if (v.equals("1", true) || v.equals("true", true)) found = true
        }
        return found
    }

    private fun resolveSheets(zip: ZipArchive, workbookXml: String): List<Pair<String, String>> {
        val rels = zip.readString("xl/_rels/workbook.xml.rels")
        val relMap = linkedMapOf<String, String>()
        SimpleXml.forEachElement(rels, "Relationship") { open, _ ->
            val id = SimpleXml.attr(open, "Id") ?: return@forEachElement
            val target = SimpleXml.attr(open, "Target") ?: return@forEachElement
            val normalized = when {
                target.startsWith("/") -> target.removePrefix("/")
                target.startsWith("xl/") -> target
                else -> "xl/$target"
            }
            relMap[id] = normalized.replace('\\', '/')
        }

        val sheets = mutableListOf<Pair<String, String>>()
        SimpleXml.forEachElement(workbookXml, "sheet") { open, _ ->
            val name = SimpleXml.attr(open, "name") ?: "Sheet${sheets.size + 1}"
            val rId = SimpleXml.attr(open, "r:id") ?: SimpleXml.attr(open, "id")
            val path = rId?.let { relMap[it] } ?: "xl/worksheets/sheet${sheets.size + 1}.xml"
            sheets += name to path
        }
        if (sheets.isNotEmpty()) return sheets

        return zip.names
            .filter { it.replace('\\', '/').contains("xl/worksheets/sheet", ignoreCase = true) }
            .sorted()
            .mapIndexed { index, path -> "Sheet${index + 1}" to path.replace('\\', '/') }
    }

    /** Collect shared-string indices referenced by `t="s"` cells (no full SST load). */
    private fun collectSstIndices(xml: String, out: MutableSet<Int>, maxSharedStrings: Int) {
        SimpleXml.forEachElement(xml, "c") { open, inner ->
            if (!SimpleXml.attr(open, "t").equals("s", true)) return@forEachElement
            val idx = SimpleXml.firstText(inner, "v")?.toIntOrNull() ?: return@forEachElement
            if (idx in 0 until maxSharedStrings) out += idx
        }
    }

    /**
     * Pass-1 SST discovery without building a full [String] for huge sheets.
     * Scans ASCII for `t="s"` / `t='s'` then the next `<v>digits</v>`.
     */
    private fun collectSstIndicesFromBytes(bytes: ByteArray, out: MutableSet<Int>, maxSharedStrings: Int) {
        var i = 0
        while (i + 6 < bytes.size) {
            // Match t="s" or t='s'
            if ((bytes[i] == 't'.code.toByte()) &&
                bytes[i + 1] == '='.code.toByte() &&
                (bytes[i + 2] == '"'.code.toByte() || bytes[i + 2] == '\''.code.toByte()) &&
                (bytes[i + 3] == 's'.code.toByte() || bytes[i + 3] == 'S'.code.toByte()) &&
                bytes[i + 4] == bytes[i + 2]
            ) {
                var j = i + 5
                val limit = minOf(bytes.size, i + 256)
                while (j + 4 < limit) {
                    if (bytes[j] == '<'.code.toByte() &&
                        bytes[j + 1] == 'v'.code.toByte() &&
                        bytes[j + 2] == '>'.code.toByte()
                    ) {
                        j += 3
                        var n = 0
                        var digits = 0
                        while (j < limit && bytes[j] in '0'.code.toByte()..'9'.code.toByte() && digits < 9) {
                            n = n * 10 + (bytes[j] - '0'.code.toByte())
                            digits++
                            j++
                        }
                        if (digits > 0 && n in 0 until maxSharedStrings) out += n
                        break
                    }
                    j++
                }
                i += 5
                continue
            }
            i++
        }
    }

    private fun containsAscii(bytes: ByteArray, needle: String): Boolean {
        if (needle.isEmpty() || bytes.size < needle.length) return false
        val n = ByteArray(needle.length) { idx ->
            val c = needle[idx].code
            if (c in 'A'.code..'Z'.code) (c + 32).toByte() else c.toByte()
        }
        fun lower(b: Byte): Int {
            val v = b.toInt() and 0xff
            return if (v in 'A'.code..'Z'.code) v + 32 else v
        }
        outer@ for (i in 0..bytes.size - n.size) {
            for (k in n.indices) {
                if (lower(bytes[i + k]) != (n[k].toInt() and 0xff)) continue@outer
            }
            return true
        }
        return false
    }

    private fun buildMetaSheet(
        chartParts: List<String>,
        validationHints: List<String>,
        freezeHint: String?,
    ): ParsedSheet {
        val cols = listOf("kind", "detail")
        val rows = ArrayList<Map<String, String>>()
        for (p in chartParts.take(40)) {
            val kind = when {
                p.contains("pivot", true) -> "pivot"
                p.contains("chart", true) -> "chart"
                p.contains("drawing", true) -> "drawing"
                p.contains("media", true) -> "media"
                else -> "part"
            }
            rows += mapOf("kind" to kind, "detail" to p.substringAfterLast('/'))
        }
        for (v in validationHints.take(40)) {
            rows += mapOf("kind" to "validation", "detail" to v)
        }
        freezeHint?.let { rows += mapOf("kind" to "freeze", "detail" to it) }
        if (rows.isEmpty()) {
            rows += mapOf("kind" to "info", "detail" to "no extra workbook parts")
        }
        return ParsedSheet("_meta", cols, rows)
    }

    /**
     * Load only [needed] SST entries (plus a sequential scan cap).
     * Avoids holding every unique string when a sheet uses a small subset.
     */
    private fun parseSharedStringsSparse(
        xml: String,
        needed: Set<Int>,
        maxSharedStrings: Int,
    ): SstParse {
        if (xml.isBlank() || needed.isEmpty()) return SstParse(emptyMap(), false)
        val out = linkedMapOf<Int, String>()
        var truncated = false
        var index = 0
        val want = needed.filter { it < maxSharedStrings }.toHashSet()
        if (want.isEmpty()) {
            return SstParse(emptyMap(), needed.any { it >= maxSharedStrings })
        }
        SimpleXml.forEachElement(xml, "si") { _, inner ->
            if (index >= maxSharedStrings) {
                truncated = true
                return@forEachElement
            }
            if (index in want) {
                val texts = SimpleXml.allTexts(inner, "t")
                out[index] = if (texts.isNotEmpty()) {
                    texts.joinToString("")
                } else {
                    SimpleXml.decodeEntities(inner.replace(Regex("<[^>]+>"), ""))
                }
                want.remove(index)
                if (want.isEmpty()) return@forEachElement
            }
            index++
        }
        // Early-exit above only skips remaining callbacks once want is empty on that element;
        // if SST ended early, leftover want is fine (missing → "").
        if (needed.any { it >= maxSharedStrings }) truncated = true
        return SstParse(out, truncated)
    }

    private fun parseSheetSparse(
        xml: String,
        sharedStrings: Map<Int, String>,
        styles: ExcelValueFormatter.StyleInfo,
        options: FileParseOptions,
        date1904: Boolean,
    ): SheetBuild {
        val warnings = mutableListOf<String>()
        val grid = mutableMapOf<Int, MutableMap<Int, String>>()
        val hiddenRows = hashSetOf<Int>()
        var maxCol = -1
        var cellCount = 0
        var truncated = false
        var hitCellCap = false

        if (options.skipHiddenRows) {
            SimpleXml.forEachElement(xml, "row") { open, _ ->
                val r = SimpleXml.attr(open, "r")?.toIntOrNull()?.minus(1) ?: return@forEachElement
                val hidden = SimpleXml.attr(open, "hidden")
                if (hidden == "1" || hidden.equals("true", true)) hiddenRows += r
            }
        }

        SimpleXml.forEachElement(xml, "c") { open, inner ->
            if (hitCellCap) return@forEachElement
            if (!options.isActive()) throw ParseCancelledException("Parse cancelled")
            val ref = SimpleXml.attr(open, "r") ?: return@forEachElement
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
            val type = SimpleXml.attr(open, "t")
            val styleIdx = SimpleXml.attr(open, "s")?.toIntOrNull()
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
            val rowMap = grid.getOrPut(row) { mutableMapOf() }
            rowMap[col] = value
            if (col > maxCol) maxCol = col
            cellCount++
            if (cellCount > options.maxCells) {
                truncated = true
                hitCellCap = true
                warnings += "Sheet truncated at maxCells=${options.maxCells}"
            }
        }

        if (options.fillMergedCells) {
            applyMerges(xml, grid, options.maxColumns)
        }
        if (grid.isEmpty()) return SheetBuild(emptyList(), warnings, truncated)

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
        return SheetBuild(matrix, warnings, truncated)
    }

    private fun applyMerges(
        xml: String,
        grid: MutableMap<Int, MutableMap<Int, String>>,
        maxColumns: Int,
    ) {
        SimpleXml.forEachElement(xml, "mergeCell") { open, _ ->
            val ref = SimpleXml.attr(open, "ref") ?: return@forEachElement
            val parts = ref.split(':')
            if (parts.size != 2) return@forEachElement
            val a = parseCellRef(parts[0]) ?: return@forEachElement
            val b = parseCellRef(parts[1]) ?: return@forEachElement
            val r1 = minOf(a.first, b.first)
            val r2 = maxOf(a.first, b.first)
            val c1 = minOf(a.second, b.second)
            val c2 = maxOf(a.second, b.second)
            if (c2 >= maxColumns) return@forEachElement
            if ((r2 - r1 + 1).toLong() * (c2 - c1 + 1) > 10_000) return@forEachElement
            val master = grid[r1]?.get(c1).orEmpty()
            if (master.isEmpty()) return@forEachElement
            for (r in r1..r2) {
                val row = grid.getOrPut(r) { mutableMapOf() }
                for (c in c1..c2) {
                    if (row[c].isNullOrEmpty()) row[c] = master
                }
            }
        }
    }

    private fun parseCellRef(ref: String): Pair<Int, Int>? {
        val match = Regex("""^([A-Za-z]+)(\d+)$""").matchEntire(ref.trim()) ?: return null
        val colLetters = match.groupValues[1].uppercase()
        val rowNum = match.groupValues[2].toIntOrNull() ?: return null
        var col = 0
        for (ch in colLetters) {
            col = col * 26 + (ch.code - 'A'.code + 1)
        }
        return (rowNum - 1) to (col - 1)
    }

    private fun detectFreezePane(xml: String): String? {
        var hint: String? = null
        SimpleXml.forEachElement(xml, "pane") { open, _ ->
            if (hint != null) return@forEachElement
            val state = SimpleXml.attr(open, "state")
            if (!state.equals("frozen", true) && !state.equals("frozenSplit", true)) return@forEachElement
            val col = SimpleXml.attr(open, "xSplit")?.toIntOrNull() ?: 0
            val row = SimpleXml.attr(open, "ySplit")?.toIntOrNull() ?: 0
            val topLeft = SimpleXml.attr(open, "topLeftCell")
            hint = buildString {
                if (col > 0) append("${col} col")
                if (row > 0) {
                    if (isNotEmpty()) append(" + ")
                    append("${row} row")
                }
                if (topLeft != null) {
                    if (isNotEmpty()) append(", ")
                    append("topLeft=$topLeft")
                }
            }.ifBlank { "frozen" }
        }
        return hint
    }

    private fun collectValidationHints(xml: String, out: MutableList<String>) {
        SimpleXml.forEachElement(xml, "dataValidation") { open, inner ->
            if (out.size >= 12) return@forEachElement
            val type = SimpleXml.attr(open, "type") ?: "list"
            val sqref = SimpleXml.attr(open, "sqref") ?: "?"
            val formula = SimpleXml.firstText(inner, "formula1")?.take(40)
            out += buildString {
                append(type)
                append('@')
                append(sqref)
                if (!formula.isNullOrBlank()) append("=$formula")
            }
        }
    }
}
