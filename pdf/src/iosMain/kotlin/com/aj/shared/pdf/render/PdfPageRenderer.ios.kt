package com.aj.shared.pdf.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformTranslate
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextDrawPDFPage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGPDFDocumentCreateWithProvider
import platform.CoreGraphics.CGPDFDocumentGetNumberOfPages
import platform.CoreGraphics.CGPDFDocumentGetPage
import platform.CoreGraphics.CGPDFDocumentRef
import platform.CoreGraphics.CGPDFDocumentRelease
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGPDFMediaBox
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

actual class PdfPageRenderer actual constructor() {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual suspend fun open(bytes: ByteArray, password: String?): PdfRenderDocument =
        withContext(Dispatchers.Default) {
            val dir = NSTemporaryDirectory()
            val tempPath = dir.trimEnd('/') + "/eazy_pdf_${NSUUID().UUIDString}.pdf"
            val data = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            data.writeToFile(tempPath, atomically = true)
            val provider = platform.CoreGraphics.CGDataProviderCreateWithFilename(tempPath)
            val doc = platform.CoreGraphics.CGPDFDocumentCreateWithProvider(provider)
            if (provider != null) {
                platform.CoreGraphics.CGDataProviderRelease(provider)
            }
            IosPdfRenderDocument(doc, tempPath)
        }
}

@OptIn(ExperimentalForeignApi::class)
private class IosPdfRenderDocument(
    private val doc: CGPDFDocumentRef?,
    private val tempPath: String
) : PdfRenderDocument {

    override val pageCount: Int
        get() = doc?.let { CGPDFDocumentGetNumberOfPages(it).toInt() } ?: 0

    override fun getPageSize(pageIndex: Int): Pair<Float, Float> {
        val document = doc ?: return Pair(612f, 792f)
        if (pageIndex !in 0 until pageCount) return Pair(612f, 792f)
        val page = CGPDFDocumentGetPage(document, (pageIndex + 1).toULong()) ?: return Pair(612f, 792f)
        val rect = CGPDFPageGetBoxRect(page, kCGPDFMediaBox)
        val (w, h) = rect.useContents { Pair(size.width.toFloat(), size.height.toFloat()) }
        return Pair(w, h)
    }

    override suspend fun renderPage(pageIndex: Int, scale: Float): ImageBitmap? =
        withContext(Dispatchers.Default) {
            val document = doc ?: return@withContext null
            if (pageIndex !in 0 until pageCount) return@withContext null

            val page = CGPDFDocumentGetPage(document, (pageIndex + 1).toULong()) ?: return@withContext null
            val box = CGPDFPageGetBoxRect(page, kCGPDFMediaBox)
            val (pageW, pageH) = box.useContents { Pair(size.width, size.height) }

            val targetScale = scale.coerceIn(0.2f, 4.0f).toDouble()
            val targetW = pageW * targetScale
            val targetH = pageH * targetScale

            UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetW, targetH), true, 1.0)
            val ctx = UIGraphicsGetCurrentContext()
            if (ctx == null) {
                UIGraphicsEndImageContext()
                return@withContext null
            }

            // White background
            CGContextSetRGBFillColor(ctx, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(ctx, CGRectMake(0.0, 0.0, targetW, targetH))

            // PDF drawing coordinate system is flipped (y=0 at bottom)
            CGContextTranslateCTM(ctx, 0.0, targetH)
            CGContextScaleCTM(ctx, targetScale, -targetScale)

            CGContextDrawPDFPage(ctx, page)
            val uiImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            if (uiImage == null) return@withContext null
            val pngData = UIImagePNGRepresentation(uiImage) ?: return@withContext null
            val rawBytes = pngData.toByteArray()

            try {
                Image.makeFromEncoded(rawBytes).toComposeImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    override fun close() {
        doc?.let {
            CGPDFDocumentRelease(it)
        }
        try {
            platform.Foundation.NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
        } catch (_: Exception) {}
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = ByteArray(size)
        bytes.usePinned {
            memcpy(it.addressOf(0), this.bytes, size.convert())
        }
        return bytes
    }
}
