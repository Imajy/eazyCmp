package com.aj.shared.pdf.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.aj.shared.pdf.model.PdfDocumentInfo
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.model.PdfSearchMatch
import com.aj.shared.pdf.parser.PdfReader
import com.aj.shared.pdf.render.PdfPageRenderer
import com.aj.shared.pdf.render.PdfRenderDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder for viewing a PDF document.
 */
class PdfViewerState(
    private val coroutineScope: CoroutineScope
) {
    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var documentInfo by mutableStateOf<PdfDocumentInfo?>(null)
        private set

    var pageInfos by mutableStateOf<List<PdfPageInfo>>(emptyList())
        private set

    val pageBitmaps = mutableStateMapOf<Int, ImageBitmap>()

    var currentPage by mutableIntStateOf(0)
    var zoomScale by mutableFloatStateOf(1.0f)
    var panOffset by mutableStateOf(Offset.Zero)
    var isContinuousScroll by mutableStateOf(true)
    var isNightMode by mutableStateOf(false)
    var rotationOffset by mutableIntStateOf(0)

    // Search state
    var isSearchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchMatches by mutableStateOf<List<PdfSearchMatch>>(emptyList())
    var currentMatchIndex by mutableIntStateOf(0)

    // Thumbnails state
    var showThumbnails by mutableStateOf(false)

    private var activeDocument: PdfRenderDocument? = null
    private var pdfReader: PdfReader? = null

    val totalPages: Int
        get() = pageInfos.size

    val currentMatch: PdfSearchMatch?
        get() = searchMatches.getOrNull(currentMatchIndex)

    fun load(bytes: ByteArray, password: String? = null) {
        isLoading = true
        errorMessage = null
        pageBitmaps.clear()

        coroutineScope.launch {
            try {
                val reader = PdfReader(bytes)
                pdfReader = reader
                val info = withContext(Dispatchers.Default) { reader.readDocumentInfo() }
                documentInfo = info
                val pages = withContext(Dispatchers.Default) { reader.readPageInfos() }
                pageInfos = pages

                val renderer = PdfPageRenderer()
                val doc = renderer.open(bytes, password)
                activeDocument?.close()
                activeDocument = doc

                // Render initial page
                renderPageBitmap(0)
                if (pages.size > 1) {
                    renderPageBitmap(1)
                }

                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: "Failed to open PDF document"
                isLoading = false
            }
        }
    }

    fun renderPageBitmap(pageIndex: Int, scale: Float = 1.5f) {
        if (pageBitmaps.containsKey(pageIndex)) return
        val doc = activeDocument ?: return
        if (pageIndex !in 0 until doc.pageCount) return

        coroutineScope.launch {
            try {
                val bitmap = doc.renderPage(pageIndex, scale)
                if (bitmap != null) {
                    pageBitmaps[pageIndex] = bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun zoomIn() {
        zoomScale = (zoomScale + 0.25f).coerceAtMost(4.0f)
    }

    fun zoomOut() {
        zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f)
        if (zoomScale <= 1.0f) {
            panOffset = Offset.Zero
        }
    }

    fun resetZoom() {
        zoomScale = 1.0f
        panOffset = Offset.Zero
    }

    fun jumpToPage(index: Int) {
        if (index in pageInfos.indices) {
            currentPage = index
            renderPageBitmap(index)
            if (index + 1 < pageInfos.size) renderPageBitmap(index + 1)
            if (index - 1 >= 0) renderPageBitmap(index - 1)
        }
    }

    fun nextPage() {
        if (currentPage < pageInfos.size - 1) {
            jumpToPage(currentPage + 1)
        }
    }

    fun prevPage() {
        if (currentPage > 0) {
            jumpToPage(currentPage - 1)
        }
    }

    fun rotateClockwise() {
        rotationOffset = (rotationOffset + 90) % 360
    }

    fun toggleNightMode() {
        isNightMode = !isNightMode
    }

    fun toggleContinuousScroll() {
        isContinuousScroll = !isContinuousScroll
    }

    fun toggleSearch() {
        isSearchActive = !isSearchActive
        if (!isSearchActive) {
            searchQuery = ""
            searchMatches = emptyList()
            currentMatchIndex = 0
        }
    }

    fun performSearch(query: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchMatches = emptyList()
            currentMatchIndex = 0
            return
        }
        val reader = pdfReader ?: return
        coroutineScope.launch {
            val matches = withContext(Dispatchers.Default) {
                reader.searchText(query)
            }
            searchMatches = matches
            currentMatchIndex = 0
            if (matches.isNotEmpty()) {
                jumpToPage(matches[0].pageIndex)
            }
        }
    }

    fun nextSearchMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
        jumpToPage(searchMatches[currentMatchIndex].pageIndex)
    }

    fun prevSearchMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = if (currentMatchIndex - 1 < 0) searchMatches.size - 1 else currentMatchIndex - 1
        jumpToPage(searchMatches[currentMatchIndex].pageIndex)
    }

    fun release() {
        activeDocument?.close()
        activeDocument = null
        pageBitmaps.clear()
    }
}

@Composable
fun rememberPdfViewerState(): PdfViewerState {
    val scope = rememberCoroutineScope()
    return remember { PdfViewerState(scope) }
}
