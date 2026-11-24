package com.aj.shared.pdf.render

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Handle to an opened PDF document for rendering pages.
 */
interface PdfRenderDocument {
    val pageCount: Int
    fun getPageSize(pageIndex: Int): Pair<Float, Float>
    suspend fun renderPage(pageIndex: Int, scale: Float = 1.0f): ImageBitmap?
    fun close()
}

/**
 * Multiplatform PDF page renderer.
 */
expect class PdfPageRenderer() {
    suspend fun open(bytes: ByteArray, password: String? = null): PdfRenderDocument
}
