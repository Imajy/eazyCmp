package com.aj.shared.print

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

@Composable
actual fun rememberPdfManager(): PdfManager {
    return remember { JsPdfManager() }
}

class JsPdfManager : PdfManager {

    override fun generateAndShare(
        fileName: String,
        onStart: () -> Unit,
        onComplete: () -> Unit,
        content: @Composable () -> Unit
    ) {
        onStart()


        val navigator = window.navigator.asDynamic()
        if (navigator.share != null) {
            window.print()
        } else {
            downloadPdf(fileName)
        }
        onComplete()
    }

    override fun generateAndDownload(
        fileName: String,
        onStart: () -> Unit,
        onComplete: () -> Unit,
        content: @Composable () -> Unit
    ) {
        onStart()
        window.print()
        onComplete()
    }

    private fun downloadPdf(fileName: String) {
        window.print()
    }
}