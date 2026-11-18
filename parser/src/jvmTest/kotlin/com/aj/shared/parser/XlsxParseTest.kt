package com.aj.shared.parser

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XlsxParseTest {

    @Test
    fun xlsx_columns_to_json() {
        val xlsx = buildMinimalXlsx(
            sheetName = "People",
            rows = listOf(
                listOf("name", "age", "city"),
                listOf("Ajay", "28", "Jaipur"),
                listOf("Riya", "24", "Delhi"),
            ),
        )
        val result = EazyFileParser.parseExcel(xlsx, fileName = "people.xlsx").getOrThrow()
        assertEquals(FileParseFormat.XLSX, result.format)
        assertEquals(2, result.rows.size)
        assertEquals("Ajay", result.rows[0]["name"])
        assertEquals("28", result.rows[0]["age"])
        assertEquals("Jaipur", result.rows[0]["city"])
        assertTrue(result.toJsonString().contains("\"name\":\"Ajay\""))
    }

    @Test
    fun zip_roundtrip_custom_reader() {
        val payload = "<hello/>".encodeToByteArray()
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                zos.write(payload)
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        val entries = ZipReader.readEntries(zipBytes)
        assertEquals("<hello/>", entries["xl/workbook.xml"]?.decodeToString())
    }

    private fun buildMinimalXlsx(sheetName: String, rows: List<List<String>>): ByteArray {
        val shared = rows.flatten().distinct()
        val sharedXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${shared.size}" uniqueCount="${shared.size}">""")
            shared.forEach { s ->
                append("<si><t>")
                append(xmlEscape(s))
                append("</t></si>")
            }
            append("</sst>")
        }
        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { rIdx, row ->
                val rowNum = rIdx + 1
                append("""<row r="$rowNum">""")
                row.forEachIndexed { cIdx, value ->
                    val ref = cellRef(cIdx, rowNum)
                    val si = shared.indexOf(value)
                    append("""<c r="$ref" t="s"><v>$si</v></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="${xmlEscape(sheetName)}" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
        """.trimIndent()
        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
            </Types>
        """.trimIndent()

        return ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                fun put(path: String, body: String) {
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(body.encodeToByteArray())
                    zos.closeEntry()
                }
                put("[Content_Types].xml", contentTypes)
                put("xl/workbook.xml", workbookXml)
                put("xl/_rels/workbook.xml.rels", relsXml)
                put("xl/worksheets/sheet1.xml", sheetXml)
                put("xl/sharedStrings.xml", sharedXml)
            }
            baos.toByteArray()
        }
    }

    private fun cellRef(colIndex: Int, rowNum: Int): String {
        var n = colIndex + 1
        val letters = StringBuilder()
        while (n > 0) {
            n--
            letters.insert(0, ('A'.code + n % 26).toChar())
            n /= 26
        }
        return "$letters$rowNum"
    }

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
