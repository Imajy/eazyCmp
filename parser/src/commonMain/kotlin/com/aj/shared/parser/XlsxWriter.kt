package com.aj.shared.parser

/**
 * Minimal XLSX writer (typed cells + formula strings, store ZIP) — no third-party libs.
 * Supports multi-sheet export of table values / `=` formulas (not full Excel fidelity).
 */
internal object XlsxWriter {

    fun write(success: ParseSuccess, sheetIndex: Int = 0, freezeColumns: Int = 1): ByteArray {
        val sheets = if (sheetIndex < 0) {
            success.sheets.ifEmpty { listOf(ParsedSheet("Sheet1", emptyList(), emptyList())) }
        } else {
            listOf(
                success.sheets.getOrNull(sheetIndex)
                    ?: success.sheets.firstOrNull()
                    ?: ParsedSheet("Sheet1", emptyList(), emptyList()),
            )
        }
        return writeSheets(sheets, freezeColumns)
    }

    fun writeSheets(sheets: List<ParsedSheet>, freezeColumns: Int = 1): ByteArray {
        val safe = sheets.ifEmpty { listOf(ParsedSheet("Sheet1", emptyList(), emptyList())) }
        val names = mutableListOf<String>()
        val used = hashSetOf<String>()
        safe.forEachIndexed { i, s ->
            var n = sanitizeSheetName(s.name.ifBlank { "Sheet${i + 1}" })
            var k = 2
            while (n in used) {
                val base = n.take(28)
                n = sanitizeSheetName("$base$k")
                k++
            }
            used += n
            names += n
        }

        val sst = buildSharedStrings(safe)
        val entries = linkedMapOf<String, ByteArray>()
        entries["[Content_Types].xml"] = contentTypesXml(safe.size, hasSst = sst.isNotEmpty()).encodeToByteArray()
        entries["_rels/.rels"] = rootRelsXml.encodeToByteArray()
        entries["xl/workbook.xml"] = workbookXml(names).encodeToByteArray()
        entries["xl/_rels/workbook.xml.rels"] =
            workbookRelsXml(safe.size, hasSst = sst.isNotEmpty()).encodeToByteArray()
        entries["xl/styles.xml"] = stylesXml.encodeToByteArray()
        if (sst.isNotEmpty()) {
            entries["xl/sharedStrings.xml"] = sharedStringsXml(sst).encodeToByteArray()
        }
        val freeze = freezeColumns.coerceIn(0, 5)
        val indexOf = sst.withIndex().associate { (i, s) -> s to i }
        safe.forEachIndexed { i, sheet ->
            val cols = sheet.columns.ifEmpty { listOf("column_1") }
            entries["xl/worksheets/sheet${i + 1}.xml"] =
                buildSheetXml(cols, sheet.rows, freeze, indexOf).encodeToByteArray()
        }
        return ZipStoreWriter.write(entries)
    }

    /** Strings that appear 2+ times → sharedStrings (smaller + more Excel-native). */
    private fun buildSharedStrings(sheets: List<ParsedSheet>): List<String> {
        val counts = linkedMapOf<String, Int>()
        for (sheet in sheets) {
            for (col in sheet.columns) {
                if (col.isNotEmpty()) counts[col] = (counts[col] ?: 0) + 1
            }
            for (row in sheet.rows) {
                for (col in sheet.columns) {
                    val v = row[col].orEmpty()
                    if (v.isEmpty() || v.startsWith("=")) continue
                    if (v.equals("TRUE", true) || v.equals("FALSE", true)) continue
                    if (v.toDoubleOrNull() != null) continue
                    counts[v] = (counts[v] ?: 0) + 1
                }
            }
        }
        return counts.filter { it.value >= 2 }.keys.toList()
    }

    private fun sharedStringsXml(strings: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""count="${strings.size}" uniqueCount="${strings.size}">""")
        for (s in strings) {
            append("""<si><t xml:space="preserve">${xmlEscape(s)}</t></si>""")
        }
        append("</sst>")
    }

    private fun buildSheetXml(
        columns: List<String>,
        rows: List<Map<String, String>>,
        freezeColumns: Int,
        sstIndex: Map<String, Int>,
    ): String =
        buildString(columns.size * (rows.size + 1) * 48) {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            if (freezeColumns > 0) {
                val topLeft = cellRef(2, freezeColumns)
                append("<sheetViews><sheetView workbookViewId=\"0\">")
                append(
                    """<pane xSplit="$freezeColumns" ySplit="1" topLeftCell="$topLeft" """ +
                        """activePane="bottomRight" state="frozen"/>""",
                )
                append("</sheetView></sheetViews>")
            }
            append("<cols>")
            columns.forEachIndexed { i, col ->
                val width = (col.length + 4).coerceIn(8, 40)
                append("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""")
            }
            append("</cols>")
            append("<sheetData>")
            append("""<row r="1">""")
            columns.forEachIndexed { i, col ->
                append(typedCell(1, i, col, forceText = true, style = 1, sstIndex = sstIndex))
            }
            append("</row>")
            rows.forEachIndexed { ri, row ->
                val r = ri + 2
                append("""<row r="$r">""")
                columns.forEachIndexed { ci, col ->
                    append(
                        typedCell(
                            r, ci, row[col].orEmpty(),
                            forceText = false, style = 0, sstIndex = sstIndex,
                        ),
                    )
                }
                append("</row>")
            }
            append("</sheetData>")
            append("""<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""")
            append("</worksheet>")
        }

    /** Numbers/bools/formulas as native Excel types; everything else inlineStr. */
    private fun typedCell(
        row1: Int,
        col0: Int,
        value: String,
        forceText: Boolean,
        style: Int = 0,
        sstIndex: Map<String, Int> = emptyMap(),
    ): String {
        val ref = cellRef(row1, col0)
        val sAttr = if (style > 0) """ s="$style"""" else ""
        if (value.isEmpty()) return """<c r="$ref"$sAttr/>"""
        if (!forceText && value.startsWith("=") && value.length > 1) {
            val body = xmlEscape(value.removePrefix("="))
            return """<c r="$ref"$sAttr><f>$body</f></c>"""
        }
        if (!forceText) {
            when {
                value.equals("TRUE", true) -> return """<c r="$ref"$sAttr t="b"><v>1</v></c>"""
                value.equals("FALSE", true) -> return """<c r="$ref"$sAttr t="b"><v>0</v></c>"""
                value.toLongOrNull() != null -> return """<c r="$ref"$sAttr><v>$value</v></c>"""
                value.toDoubleOrNull() != null &&
                    value.matches(Regex("""^-?\d+(\.\d+)?([eE][+-]?\d+)?$""")) ->
                    return """<c r="$ref"$sAttr><v>$value</v></c>"""
            }
        }
        val si = sstIndex[value]
        if (si != null) {
            return """<c r="$ref"$sAttr t="s"><v>$si</v></c>"""
        }
        return """<c r="$ref"$sAttr t="inlineStr"><is><t xml:space="preserve">${xmlEscape(value)}</t></is></c>"""
    }

    private fun cellRef(row1: Int, col0: Int): String {
        var n = col0 + 1
        val letters = StringBuilder()
        while (n > 0) {
            n--
            letters.insert(0, ('A'.code + n % 26).toChar())
            n /= 26
        }
        return "$letters$row1"
    }

    private fun sanitizeSheetName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/*?:\[\]]"""), "_").take(31)
        return cleaned.ifBlank { "Sheet1" }
    }

    private fun xmlEscape(s: String): String = buildString(s.length + 8) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code in 0x20..0xD7FF || ch == '\t' || ch == '\n' || ch == '\r') {
                    append(ch)
                }
            }
        }
    }

    private fun workbookXml(names: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { i, name ->
            append("""<sheet name="${xmlEscape(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun contentTypesXml(sheetCount: Int, hasSst: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        if (hasSst) {
            append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        }
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbookRelsXml(sheetCount: Int, hasSst: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        var next = sheetCount + 1
        append("""<Relationship Id="rId$next" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        next++
        if (hasSst) {
            append("""<Relationship Id="rId$next" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        }
        append("</Relationships>")
    }

    private const val rootRelsXml =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private const val stylesXml =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2">""" +
            """<font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font>""" +
            """</fonts>""" +
            """<fills count="2">""" +
            """<fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """</fills>""" +
            """<borders count="1"><border/></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="2">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """</cellXfs>""" +
            """</styleSheet>"""
}
