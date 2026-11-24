package com.aj.shared.pdf

import com.aj.shared.pdf.model.PdfAnnotation
import com.aj.shared.pdf.model.PdfDocumentInfo
import com.aj.shared.pdf.model.PdfEditAction
import com.aj.shared.pdf.model.PdfFreehandStroke
import com.aj.shared.pdf.model.PdfHighlightAnnotation
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.model.PdfPoint
import com.aj.shared.pdf.model.PdfRect
import com.aj.shared.pdf.model.PdfShapeAnnotation
import com.aj.shared.pdf.model.PdfShapeType
import com.aj.shared.pdf.model.PdfSignatureAnnotation
import com.aj.shared.pdf.model.PdfStampAnnotation
import com.aj.shared.pdf.model.PdfStampType
import com.aj.shared.pdf.model.PdfTextAnnotation
import com.aj.shared.pdf.model.PdfUndoRedoManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfModelTest {

    @Test
    fun testPdfPageInfoAspectAndRotation() {
        val page = PdfPageInfo(pageIndex = 0, width = 612f, height = 792f, rotation = 0)
        assertFalse(page.isLandscape)
        assertEquals(612f / 792f, page.aspectRatio)

        val landscapePage = PdfPageInfo(pageIndex = 1, width = 792f, height = 612f, rotation = 0)
        assertTrue(landscapePage.isLandscape)

        val rotatedPage = PdfPageInfo(pageIndex = 2, width = 612f, height = 792f, rotation = 90)
        assertTrue(rotatedPage.isLandscape)
    }

    @Test
    fun testPdfRectContains() {
        val rect = PdfRect(10f, 20f, 100f, 200f)
        assertEquals(90f, rect.width)
        assertEquals(180f, rect.height)
        assertTrue(rect.contains(50f, 50f))
        assertFalse(rect.contains(5f, 50f))
        assertFalse(rect.contains(50f, 210f))
    }

    @Test
    fun testPdfUndoRedoManager() {
        val manager = PdfUndoRedoManager()
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)

        val stroke = PdfFreehandStroke(
            id = "s1",
            pageIndex = 0,
            points = listOf(PdfPoint(0f, 0f), PdfPoint(10f, 10f))
        )
        manager.pushAction(PdfEditAction.AddAnnotation(stroke))

        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)
        assertEquals(1, manager.undoDepth)

        val undone = manager.popUndo()
        assertNotNull(undone)
        assertTrue(undone is PdfEditAction.AddAnnotation)
        assertEquals("s1", undone.annotation.id)

        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)

        val redone = manager.popRedo()
        assertNotNull(redone)
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)
    }

    @Test
    fun testPdfAnnotationsCreation() {
        val text = PdfTextAnnotation(
            id = "t1",
            pageIndex = 0,
            x = 50f,
            y = 100f,
            text = "Hello EazyCmp PDF",
            fontSize = 16f
        )
        assertEquals("Hello EazyCmp PDF", text.text)
        assertEquals(0, text.pageIndex)

        val remapped = text.withPage(2)
        assertEquals(2, remapped.pageIndex)

        val stamp = PdfStampAnnotation(
            id = "st1",
            pageIndex = 1,
            x = 100f,
            y = 200f,
            stampType = PdfStampType.APPROVED
        )
        assertEquals("APPROVED", stamp.displayLabel)

        val sig = PdfSignatureAnnotation(
            id = "sig1",
            pageIndex = 0,
            x = 10f,
            y = 20f,
            strokes = listOf(
                PdfFreehandStroke("strk1", 0, listOf(PdfPoint(1f, 1f), PdfPoint(2f, 2f)))
            )
        )
        assertEquals(1, sig.strokes.size)
    }
}
