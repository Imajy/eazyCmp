package com.aj.shared.pdf.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer

actual class PdfPageRenderer actual constructor() {
    actual suspend fun open(bytes: ByteArray, password: String?): PdfRenderDocument =
        withContext(Dispatchers.IO) {
            val buffer = RandomAccessReadBuffer(bytes)
            val document = if (password.isNullOrEmpty()) {
                Loader.loadPDF(buffer)
            } else {
                Loader.loadPDF(buffer, password)
            }
            JvmPdfRenderDocument(document)
        }
}

private class JvmPdfRenderDocument(
    private val document: PDDocument
) : PdfRenderDocument {

    private val renderer = PDFRenderer(document)

    override val pageCount: Int
        get() = document.numberOfPages

    override fun getPageSize(pageIndex: Int): Pair<Float, Float> {
        if (pageIndex !in 0 until pageCount) return Pair(612f, 792f)
        val page = document.getPage(pageIndex)
        val box = page.cropBox ?: page.mediaBox
        return Pair(box.width, box.height)
    }

    override suspend fun renderPage(pageIndex: Int, scale: Float): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (pageIndex !in 0 until pageCount) return@withContext null
            try {
                val dpi = (72f * scale).coerceIn(36f, 300f)
                val bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)
                bufferedImage.toComposeImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    override fun close() {
        try {
            document.close()
        } catch (_: Exception) {}
    }
}
