package com.aj.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EazyFileParserTest {

    @Test
    fun csv_toJson() {
        val csv = """
            name,age,city
            Ajay,28,Jaipur
            Riya,24,Delhi
        """.trimIndent()
        val result = EazyFileParser.parseCsv(csv.encodeToByteArray()).getOrThrow()
        assertEquals(2, result.rows.size)
        assertEquals("Ajay", result.rows[0]["name"])
        assertEquals("28", result.rows[0]["age"])
        val json = result.toJsonString()
        assertTrue(json.contains("\"name\":\"Ajay\""))
        assertTrue(json.startsWith("["))
    }

    @Test
    fun csv_quotedCommas() {
        val csv = "name,note\n\"Kumar, Ajay\",\"hello, world\"\n"
        val rows = EazyFileParser.parseCsv(csv.encodeToByteArray()).getOrThrow().rows
        assertEquals("Kumar, Ajay", rows[0]["name"])
        assertEquals("hello, world", rows[0]["note"])
    }

    @Test
    fun tsv_auto() {
        val tsv = "a\tb\n1\t2\n"
        val result = EazyFileParser.parse(tsv.encodeToByteArray(), fileName = "data.tsv").getOrThrow()
        assertEquals(FileParseFormat.TSV, result.format)
        assertEquals("1", result.rows[0]["a"])
    }

    @Test
    fun vcard_toJson() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ajay Kumar
            TEL:+91-9999999999
            EMAIL:ajay@example.com
            ORG:EazyCmp
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Riya
            EMAIL:riya@example.com
            END:VCARD
        """.trimIndent()
        val result = EazyFileParser.parseVCard(vcf.encodeToByteArray()).getOrThrow()
        assertEquals(2, result.rows.size)
        assertEquals("Ajay Kumar", result.rows[0]["fn"])
        assertTrue(result.toJsonString().contains("Ajay Kumar"))
    }

    @Test
    fun empty_file_error() {
        val err = EazyFileParser.parse(ByteArray(0))
        assertTrue(err is ParseResult.Error)
    }

    @Test
    fun duplicate_headers_uniquified() {
        val csv = "name,name,age\nA,B,1\n"
        val sheet = EazyFileParser.parseCsv(csv.encodeToByteArray()).getOrThrow()
        assertEquals(listOf("name", "name_2", "age"), sheet.columns)
        assertEquals("A", sheet.rows[0]["name"])
        assertEquals("B", sheet.rows[0]["name_2"])
    }

    @Test
    fun unclosed_quote_recovers() {
        val csv = "a,b\n\"hello,world\n"
        val rows = EazyFileParser.parseCsv(csv.encodeToByteArray()).getOrThrow().rows
        assertEquals(1, rows.size)
        assertTrue(rows[0]["a"]!!.contains("hello"))
    }

    @Test
    fun legacy_xls_too_small_errors() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0, 0, 0, 0)
        val err = EazyFileParser.parse(ole, fileName = "old.xls")
        assertTrue(err is ParseResult.Error)
    }

    @Test
    fun crc_fingerprint_stable() {
        val a = "hello".encodeToByteArray()
        assertEquals(Crc32.fingerprint(a), Crc32.fingerprint(a.copyOf()))
    }

    @Test
    fun include_row_id() {
        val csv = "name\nAjay\n"
        val result = EazyFileParser.parseCsv(
            csv.encodeToByteArray(),
            options = FileParseOptions(includeRowId = true),
        ).getOrThrow()
        assertEquals("1", result.rows[0]["id"])
        assertEquals("Ajay", result.rows[0]["name"])
    }

    @Test
    fun json_numbers_unquoted() {
        val csv = "n,ok\n28,true\n"
        val json = EazyFileParser.parseCsv(csv.encodeToByteArray()).getOrThrow().toJsonString()
        assertTrue(json.contains("\"n\":28") || json.contains("\"n\": 28"))
        assertTrue(json.contains("true"))
    }

    @Test
    fun simple_formula_sum() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "1", 1 to "2"),
            1 to mutableMapOf(0 to "=SUM(A1:B1)"),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("3", grid[1]!![0])
    }

    @Test
    fun formula_if_vlookup_iferror() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "apple", 1 to "10"),
            1 to mutableMapOf(0 to "banana", 1 to "20"),
            2 to mutableMapOf(0 to "10", 1 to "0"),
            3 to mutableMapOf(0 to """=IF(B1>5,"big","small")"""),
            4 to mutableMapOf(0 to """=VLOOKUP("banana",A1:B2,2,FALSE)"""),
            5 to mutableMapOf(0 to """=IFERROR(A3/B3,"err")"""),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("big", grid[3]!![0])
        assertEquals("20", grid[4]!![0])
        assertEquals("err", grid[5]!![0])
    }

    @Test
    fun formula_index_match_xlookup() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "a", 1 to "100"),
            1 to mutableMapOf(0 to "b", 1 to "200"),
            2 to mutableMapOf(0 to """=MATCH("b",A1:A2,0)"""),
            3 to mutableMapOf(0 to "=INDEX(B1:B2,2,1)"),
            4 to mutableMapOf(0 to """=XLOOKUP("a",A1:A2,B1:B2)"""),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("2", grid[2]!![0])
        assertEquals("200", grid[3]!![0])
        assertEquals("100", grid[4]!![0])
    }

    @Test
    fun formula_countif_sumif() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "a", 1 to "10"),
            1 to mutableMapOf(0 to "b", 1 to "20"),
            2 to mutableMapOf(0 to "a", 1 to "5"),
            3 to mutableMapOf(0 to """=COUNTIF(A1:A3,"a")"""),
            4 to mutableMapOf(0 to """=SUMIF(A1:A3,"a",B1:B3)"""),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("2", grid[3]!![0])
        assertEquals("15", grid[4]!![0])
    }

    @Test
    fun export_xlsx_roundtrip() {
        val data = ParseSuccess(
            format = FileParseFormat.CSV,
            sheets = listOf(
                ParsedSheet(
                    name = "People",
                    columns = listOf("name", "age"),
                    rows = listOf(mapOf("name" to "Ajay", "age" to "28")),
                ),
            ),
            sourceName = "people.csv",
        )
        val bytes = EazyFileParser.exportXlsx(data)
        val parsed = EazyFileParser.parseExcel(bytes, fileName = "people.xlsx").getOrThrow()
        assertEquals("Ajay", parsed.rows[0]["name"])
        assertEquals("28", parsed.rows[0]["age"])
    }

    @Test
    fun withCellEdits_and_multi_sheet_export() {
        val data = ParseSuccess(
            format = FileParseFormat.XLSX,
            sheets = listOf(
                ParsedSheet("A", listOf("n"), listOf(mapOf("n" to "1"), mapOf("n" to "2"))),
                ParsedSheet("B", listOf("x"), listOf(mapOf("x" to "z"))),
            ),
        )
        val edited = data.withCellEdits(0, mapOf("1|n" to "99"))
        assertEquals("1", edited.sheets[0].rows[0]["n"])
        assertEquals("99", edited.sheets[0].rows[1]["n"])
        val bytes = EazyFileParser.exportXlsx(edited, sheetIndex = -1)
        val parsed = EazyFileParser.parseExcel(
            bytes,
            fileName = "multi.xlsx",
            options = FileParseOptions(allSheets = true, hasHeader = true),
        ).getOrThrow()
        assertTrue(parsed.sheets.size >= 2)
        assertEquals("99", parsed.sheets[0].rows[1]["n"])
    }

    @Test
    fun formula_unique_filter() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "a", 1 to "1"),
            1 to mutableMapOf(0 to "b", 1 to "0"),
            2 to mutableMapOf(0 to "a", 1 to "1"),
            3 to mutableMapOf(0 to "=UNIQUE(A1:A3)"),
            4 to mutableMapOf(0 to """=FILTER(A1:A3,B1:B3=1)"""),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("a, b", grid[3]!![0])
        assertEquals("a, a", grid[4]!![0])
    }

    @Test
    fun formula_sort_isblank() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "3", 1 to ""),
            1 to mutableMapOf(0 to "1"),
            2 to mutableMapOf(0 to "2"),
            3 to mutableMapOf(0 to "=SORT(A1:A3)"),
            4 to mutableMapOf(0 to "=ISBLANK(B1)"),
            5 to mutableMapOf(0 to "=ISBLANK(A1)"),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("1, 2, 3", grid[3]!![0])
        assertEquals("TRUE", grid[4]!![0])
        assertEquals("FALSE", grid[5]!![0])
    }

    @Test
    fun formula_xmatch_ifblank() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "a"),
            1 to mutableMapOf(0 to "b"),
            2 to mutableMapOf(0 to """=XMATCH("b",A1:A2)"""),
            3 to mutableMapOf(0 to """=IFBLANK(,"x")"""),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("2", grid[2]!![0])
        assertEquals("x", grid[3]!![0])
    }

    @Test
    fun formula_let() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "10", 1 to "2"),
            1 to mutableMapOf(0 to "=LET(x,A1,y,B1,x*y)"),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("20", grid[1]!![0])
    }

    @Test
    fun formula_lambda_apply() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "=LAMBDA(x,y,x*y)(3,4)"),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("12", grid[0]!![0])
    }

    @Test
    fun base64_roundtrip_alphabet() {
        val decoded = SimpleBase64.decode("SGVsbG8=")
        assertEquals("Hello", decoded.decodeToString())
    }

    @Test
    fun sheet_byte_parser_reads_inline_and_number() {
        val xml = """
            <?xml version="1.0"?>
            <worksheet><sheetData>
            <row r="1"><c r="A1" t="inlineStr"><is><t>Hi</t></is></c><c r="B1"><v>42</v></c></row>
            </sheetData></worksheet>
        """.trimIndent().encodeToByteArray()
        val built = SheetXmlByteParser.parse(
            bytes = xml,
            sharedStrings = emptyMap(),
            styles = ExcelValueFormatter.StyleInfo(emptyList(), emptyMap()),
            options = FileParseOptions(hasHeader = false, formatExcelValues = false),
            date1904 = false,
            applyMerges = { _, _, _ -> },
            parseCellRef = fun(ref: String): Pair<Int, Int>? {
                val m = Regex("""^([A-Za-z]+)(\d+)$""").matchEntire(ref) ?: return null
                var col = 0
                for (ch in m.groupValues[1].uppercase()) col = col * 26 + (ch.code - 'A'.code + 1)
                return (m.groupValues[2].toInt() - 1) to (col - 1)
            },
        )
        assertEquals(1, built.matrix.size)
        assertEquals("Hi", built.matrix[0][0])
        assertEquals("42", built.matrix[0][1])
    }

    @Test
    fun formula_row_hyperlink_indirect() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "hello", 1 to "A1"),
            1 to mutableMapOf(0 to "=ROW(A1)"),
            2 to mutableMapOf(0 to "=COLUMN(B1)"),
            3 to mutableMapOf(0 to """=HYPERLINK("http://x","link")"""),
            4 to mutableMapOf(0 to """=INDIRECT(B1)"""),
            5 to mutableMapOf(0 to "=N(A1)", 1 to "=T(A1)"),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("1", grid[1]!![0])
        assertEquals("2", grid[2]!![0])
        assertEquals("link", grid[3]!![0])
        assertEquals("hello", grid[4]!![0])
        assertEquals("0", grid[5]!![0])
        assertEquals("hello", grid[5]!![1])
    }

    @Test
    fun formula_countifs_sumifs() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "a", 1 to "x", 2 to "10"),
            1 to mutableMapOf(0 to "a", 1 to "y", 2 to "20"),
            2 to mutableMapOf(0 to "b", 1 to "x", 2 to "5"),
            3 to mutableMapOf(0 to """=COUNTIFS(A1:A3,"a",B1:B3,"x")"""),
            4 to mutableMapOf(0 to """=SUMIFS(C1:C3,A1:A3,"a")"""),
            5 to mutableMapOf(0 to """=AVERAGEIF(A1:A3,"a",C1:C3)"""),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("1", grid[3]!![0])
        assertEquals("30", grid[4]!![0])
        assertEquals("15", grid[5]!![0])
    }

    @Test
    fun formula_ifs_switch_round_textjoin() {
        val grid = mutableMapOf(
            0 to mutableMapOf(0 to "3", 1 to "a", 2 to "b"),
            1 to mutableMapOf(0 to """=IFS(A1>5,"big",A1>0,"ok","no")"""),
            2 to mutableMapOf(0 to """=SWITCH(B1,"a",1,"b",2,0)"""),
            3 to mutableMapOf(0 to "=ROUND(3.14159,2)"),
            4 to mutableMapOf(0 to """=TEXTJOIN("-",TRUE,B1:C1)"""),
            5 to mutableMapOf(0 to """=CHOOSE(2,"x","y","z")"""),
            6 to mutableMapOf(0 to "=PRODUCT(A1,2)"),
        )
        SimpleFormulaEvaluator.evaluateGrid(grid)
        assertEquals("ok", grid[1]!![0])
        assertEquals("1", grid[2]!![0])
        assertEquals("3.14", grid[3]!![0])
        assertEquals("a-b", grid[4]!![0])
        assertEquals("y", grid[5]!![0])
        assertEquals("6", grid[6]!![0])
    }

    @Test
    fun vcard_photo_uri_meta() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ajay
            PHOTO;VALUE=URI:https://example.com/a.jpg
            END:VCARD
        """.trimIndent()
        val row = EazyFileParser.parseVCard(vcf.encodeToByteArray()).getOrThrow().rows[0]
        assertEquals("https://example.com/a.jpg", row["photo_url"])
    }

    @Test
    fun export_xlsx_preserves_formula() {
        val data = ParseSuccess(
            format = FileParseFormat.CSV,
            sheets = listOf(
                ParsedSheet(
                    "S",
                    listOf("a", "b"),
                    listOf(mapOf("a" to "1", "b" to "=A2")),
                ),
            ),
        )
        val bytes = EazyFileParser.exportXlsx(data)
        val xml = bytes.decodeToString()
        // store ZIP keeps XML plaintext — formula tag should appear
        assertTrue(xml.contains("<f>") || xml.contains("=A2") || xml.contains(">A2<"))
    }

    @Test
    fun export_xlsx_types_numbers() {
        val data = ParseSuccess(
            format = FileParseFormat.CSV,
            sheets = listOf(
                ParsedSheet(
                    "S",
                    listOf("n", "ok"),
                    listOf(mapOf("n" to "28", "ok" to "true")),
                ),
            ),
        )
        val bytes = EazyFileParser.exportXlsx(data)
        val parsed = EazyFileParser.parseExcel(bytes, fileName = "t.xlsx").getOrThrow()
        assertEquals("28", parsed.rows[0]["n"])
        assertEquals("TRUE", parsed.rows[0]["ok"]?.uppercase())
    }

    @Test
    fun takeDataRows_windows_without_losing_loaded_count() {
        val full = ParseSuccess(
            format = FileParseFormat.CSV,
            sheets = listOf(
                ParsedSheet(
                    name = "Sheet1",
                    columns = listOf("n"),
                    rows = (1..5).map { mapOf("n" to "$it") },
                ),
            ),
            loadedDataRows = 5,
        )
        val window = full.takeDataRows(2)
        assertEquals(2, window.rows.size)
        assertEquals(5, window.loadedDataRows)
        assertTrue(window.truncated)
    }
}
