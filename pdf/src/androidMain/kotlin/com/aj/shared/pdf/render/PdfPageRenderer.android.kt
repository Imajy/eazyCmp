package com.aj.shared.pdf.render

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class PdfPageRenderer actual constructor() {
    actual suspend fun open(bytes: ByteArray, password: String?): PdfRenderDocument =
        withContext(Dispatchers.IO) {
            val tempFile = File.createTempFile("eazy_pdf_", ".pdf")
            tempFile.deleteOnExit()
            FileOutputStream(tempFile).use { it.write(bytes) }

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = AndroidPdfRenderer(pfd)
            AndroidPdfRenderDocument(tempFile, pfd, renderer)
        }
}

private class AndroidPdfRenderDocument(
    private val tempFile: File,
    private val pfd: ParcelFileDescriptor,
    private val renderer: AndroidPdfRenderer
) : PdfRenderDocument {

    private val lock = Any()

    override val pageCount: Int
        get() = renderer.pageCount

    override fun getPageSize(pageIndex: Int): Pair<Float, Float> = synchronized(lock) {
        if (pageIndex !in 0 until pageCount) return Pair(612f, 792f)
        val page = renderer.openPage(pageIndex)
        val w = page.width.toFloat()
        val h = page.height.toFloat()
        page.close()
        Pair(w, h)
    }

    override suspend fun renderPage(pageIndex: Int, scale: Float): ImageBitmap? =
        withContext(Dispatchers.Default) {
            synchronized(lock) {
                if (pageIndex !in 0 until pageCount) return@synchronized null
                try {
                    val page = renderer.openPage(pageIndex)
                    val targetScale = scale.coerceIn(0.2f, 4.0f)
                    val width = (page.width * targetScale).toInt().coerceAtLeast(1)
                    val height = (page.height * targetScale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    bitmap.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

    override fun close() {
        synchronized(lock) {
            try {
                renderer.close()
            } catch (_: Exception) {}
            try {
                pfd.close()
            } catch (_: Exception) {}
            try {
                tempFile.delete()
            } catch (_: Exception) {}
        }
    }
}
