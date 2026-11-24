package com.aj.shared.pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aj.shared.pdf.model.PdfAnnotation
import com.aj.shared.pdf.model.PdfDocumentInfo
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.model.PdfSearchMatch
import com.aj.shared.pdf.parser.PdfReader
import com.aj.shared.pdf.parser.PdfWriter
import com.aj.shared.pdf.state.PdfEditorState
import com.aj.shared.pdf.state.PdfViewerState
import com.aj.shared.pdf.state.rememberPdfEditorState
import com.aj.shared.pdf.state.rememberPdfViewerState
import com.aj.shared.pdf.ui.PdfEditor
import com.aj.shared.pdf.ui.PdfViewer

/**
 * Main public entry point for EazyCmp PDF functionality.
 * Provides programmatic inspection, searching, blank creation, exporting,
 * as well as Compose Multiplatform viewer and editor components.
 */
object EazyPdf {

    /**
     * Reads metadata and summary info from raw PDF bytes.
     */
    fun readInfo(bytes: ByteArray): PdfDocumentInfo =
        PdfReader(bytes).readDocumentInfo()

    /**
     * Reads all page dimensions, rotations, and counts.
     */
    fun readPages(bytes: ByteArray): List<PdfPageInfo> =
        PdfReader(bytes).readPageInfos()

    /**
     * Searches for keywords across all pages in the PDF document.
     */
    fun searchText(bytes: ByteArray, query: String, maxResults: Int = 100): List<PdfSearchMatch> =
        PdfReader(bytes).searchText(query, maxResults)

    /**
     * Extracts text content page by page.
     */
    fun extractText(bytes: ByteArray): List<String> =
        PdfReader(bytes).extractPagesText()

    /**
     * Creates a minimal blank PDF document with the given dimensions in points.
     */
    fun createBlank(width: Float = 612f, height: Float = 792f): ByteArray =
        PdfWriter.exportPdf(
            originalBytes = null,
            pages = listOf(PdfPageInfo(0, width, height, 0)),
            annotationsByPage = emptyMap()
        )

    /**
     * Exports a document with page modifications and overlay annotations.
     */
    fun export(
        originalBytes: ByteArray?,
        pages: List<PdfPageInfo>,
        annotationsByPage: Map<Int, List<PdfAnnotation>>
    ): ByteArray =
        PdfWriter.exportPdf(originalBytes, pages, annotationsByPage)
}

/**
 * Convenient shortcut for displaying the PDF Viewer.
 */
@Composable
fun EazyPdfViewer(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    password: String? = null,
    state: PdfViewerState = rememberPdfViewerState(),
    onPageChanged: ((page: Int, totalPages: Int) -> Unit)? = null
) {
    PdfViewer(
        bytes = bytes,
        modifier = modifier,
        fileName = fileName,
        password = password,
        state = state,
        onPageChanged = onPageChanged
    )
}

/**
 * Convenient shortcut for displaying the PDF Editor.
 */
@Composable
fun EazyPdfEditor(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    password: String? = null,
    state: PdfEditorState = rememberPdfEditorState(),
    onSave: ((ByteArray) -> Unit)? = null,
    onExport: ((fileBytes: ByteArray, fileName: String) -> Unit)? = null
) {
    PdfEditor(
        bytes = bytes,
        modifier = modifier,
        fileName = fileName,
        password = password,
        state = state,
        onSave = onSave,
        onExport = onExport
    )
}
