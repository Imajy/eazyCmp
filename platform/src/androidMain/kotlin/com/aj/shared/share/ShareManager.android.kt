package com.aj.shared.share

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.aj.shared.api.appContext
import com.aj.shared.api.isAppContextInitialized
import java.io.File
import java.util.concurrent.Executors

actual class ShareManager actual constructor() {

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "eazyCmp-share-io").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    actual fun shareText(text: String) {
        if (!isAppContextInitialized()) return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) {
        if (!isAppContextInitialized()) return
        ioExecutor.execute {
            try {
                val cacheFile = File(appContext.cacheDir, fileName).apply {
                    writeBytes(bytes)
                }
                val uri: Uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.provider",
                    cacheFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "Share File").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                mainHandler.post {
                    try {
                        appContext.startActivity(chooser)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
