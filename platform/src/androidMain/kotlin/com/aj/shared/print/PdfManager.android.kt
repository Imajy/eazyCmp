package com.aj.shared.print

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

private val pdfIoExecutor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "eazyCmp-pdf-io").apply { isDaemon = true }
}
private val mainHandler = Handler(Looper.getMainLooper())

@Composable
actual fun rememberPdfManager(): PdfManager {
    val context = LocalContext.current
    val compositionContext = rememberCompositionContext()
    val rootView = LocalView.current

    return remember(context, compositionContext, rootView) {
        AndroidPdfManager(
            context = context,
            compositionContext = compositionContext,
            rootView = rootView
        )
    }
}

class AndroidPdfManager(
    private val context: Context,
    private val compositionContext: CompositionContext,
    private val rootView: View
) : PdfManager {

    override fun generateAndShare(
        fileName: String,
        onStart: () -> Unit,
        onComplete: () -> Unit,
        content: @Composable () -> Unit
    ) {
        onStart()

        captureFullComposeView(
            context = context,
            compositionContext = compositionContext,
            rootView = rootView,
            fileName = fileName,
            content = content,
            onComplete = { file ->
                if (file != null) {
                    sharePdfFile(context, file)
                }
                onComplete()
            }
        )
    }

    override fun generateAndDownload(
        fileName: String,
        onStart: () -> Unit,
        onComplete: () -> Unit,
        content: @Composable () -> Unit
    ) {
        onStart()

        captureFullComposeView(
            context = context,
            compositionContext = compositionContext,
            rootView = rootView,
            fileName = fileName,
            content = content,
            onComplete = { file ->
                if (file != null) {
                    openPdfFile(context, file)
                }
                onComplete()
            }
        )
    }
}

fun openPdfFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    context.startActivity(intent)
}

fun sharePdfFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share Comparison Plan"))
}

fun captureFullComposeView(
    context: Context,
    compositionContext: CompositionContext,
    rootView: View,
    fileName: String,
    onComplete: (File?) -> Unit,
    content: @Composable () -> Unit
) {
    val parent = rootView as? ViewGroup
    if (parent == null) {
        onComplete(null)
        return
    }

    val composeView = ComposeView(context).apply {
        setParentCompositionContext(compositionContext)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F7FA))
                    .padding(10.dp)
            ) {
                content()
            }
        }
    }

    parent.addView(composeView)

    composeView.post {
        try {
            val maxHeight = (parent.height.coerceAtLeast(1) * 4).coerceAtMost(8000)
            composeView.measure(
                View.MeasureSpec.makeMeasureSpec(parent.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
            )

            val width = composeView.measuredWidth.coerceAtLeast(1)
            val height = composeView.measuredHeight.coerceAtLeast(1).coerceAtMost(maxHeight)

            composeView.layout(0, 0, width, height)

            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            composeView.draw(canvas)

            // Tear down Compose view on main, encode PDF off the UI thread.
            parent.removeView(composeView)

            pdfIoExecutor.execute {
                var outFile: File? = null
                try {
                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)

                    val safeFileName = fileName.ifBlank { "document.pdf" }
                        .substringAfterLast('/')
                        .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
                    val file = File(context.getExternalFilesDir(null), safeFileName)
                    FileOutputStream(file).use { pdfDocument.writeTo(it) }
                    pdfDocument.close()
                    bitmap.recycle()
                    outFile = file
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        bitmap.recycle()
                    } catch (_: Exception) {
                    }
                }
                mainHandler.post { onComplete(outFile) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                parent.removeView(composeView)
            } catch (_: Exception) {
            }
            onComplete(null)
        }
    }
}
