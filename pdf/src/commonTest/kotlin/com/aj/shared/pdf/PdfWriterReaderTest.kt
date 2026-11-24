package com.aj.shared.pdf

import com.aj.shared.pdf.model.PdfFreehandStroke
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.model.PdfPoint
import com.aj.shared.pdf.model.PdfStampAnnotation
import com.aj.shared.pdf.model.PdfStampType
import com.aj.shared.pdf.model.PdfTextAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfWriterReaderTest {

    @Test
    fun testCreateBlankPdfAndRead() {
        val bytes = EazyPdf.createBlank(width = 612f, height = 792f)
        assertTrue(bytes.isNotEmpty())

        val text = bytes.decodeToString()
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("%%EOF"))
        assertTrue(text.contains("/Type /Catalog"))
        assertTrue(text.contains("/Type /Pages"))
        assertTrue(text.contains("/Type /Page"))

        val info = EazyPdf.readInfo(bytes)
        assertEquals(1, info.pageCount)
        assertEquals("1.4", info.version)
        assertFalse(info.isEncrypted)

        val pages = EazyPdf.readPages(bytes)
        assertEquals(1, pages.size)
        assertEquals(612f, pages[0].width)
        assertEquals(792f, pages[0].height)
    }

    @Test
    fun testExportPdfWithAnnotations() {
        val page = PdfPageInfo(pageIndex = 0, width = 600f, height = 800f, rotation = 90)
        val stroke = PdfFreehandStroke(
            id = "s1",
            pageIndex = 0,
            points = listOf(PdfPoint(10f, 10f), PdfPoint(20f, 20f), PdfPoint(30f, 15f)),
            strokeWidth = 3f,
            colorArgb = 0xFF1E88E5
        )
        val textAnnot = PdfTextAnnotation(
            id = "t1",
            pageIndex = 0,
            x = 50f,
            y = 100f,
            text = "Confidential EazyCmp Report"
        )
        val stamp = PdfStampAnnotation(
            id = "st1",
            pageIndex = 0,
            x = 200f,
            y = 300f,
            stampType = PdfStampType.CONFIDENTIAL
        )

        val exportedBytes = EazyPdf.export(
            originalBytes = null,
            pages = listOf(page),
            annotationsByPage = mapOf(0 to listOf(stroke, textAnnot, stamp))
        )

        assertTrue(exportedBytes.isNotEmpty())
        val pdfStr = exportedBytes.decodeToString()
        assertTrue(pdfStr.contains("Confidential EazyCmp Report"))
        assertTrue(pdfStr.contains("CONFIDENTIAL"))
        assertTrue(pdfStr.contains("/Rotate 90"))

        // Search text in exported PDF
        val matches = EazyPdf.searchText(exportedBytes, "Confidential")
        assertTrue(matches.isNotEmpty())
        assertEquals(0, matches[0].pageIndex)
    }

    @Test
    fun testMultiPageExport() {
        val p1 = PdfPageInfo(pageIndex = 0, width = 612f, height = 792f, rotation = 0)
        val p2 = PdfPageInfo(pageIndex = 1, width = 595f, height = 842f, rotation = 0) // A4
        val p3 = PdfPageInfo(pageIndex = 2, width = 612f, height = 792f, rotation = 0, isDeleted = true)

        val exported = EazyPdf.export(
            originalBytes = null,
            pages = listOf(p1, p2, p3),
            annotationsByPage = emptyMap()
        )

        val pages = EazyPdf.readPages(exported)
        // Deleted page is excluded
        assertEquals(2, pages.size)
        assertEquals(612f, pages[0].width)
        assertEquals(595f, pages[1].width)
    }
}
