package com.aj.shared.web

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

actual val isWeb: Boolean = false
actual val isWasm: Boolean = false
actual val isJvm: Boolean = true

actual object WebBrowser {
    private var currentHash: String = ""

    actual fun openUrl(url: String, target: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
            }
        }
    }

    actual fun copyToClipboard(text: String, onResult: ((Boolean) -> Unit)?) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            onResult?.invoke(true)
        } catch (_: Exception) {
            onResult?.invoke(false)
        }
    }

    actual fun getCurrentUrl(): String = "http://localhost/"

    actual fun getQueryParam(key: String): String? = null

    actual fun getQueryParams(): Map<String, String> = emptyMap()

    actual fun getHash(): String = currentHash

    actual fun setHash(hash: String) {
        currentHash = hash
    }

    actual fun downloadFile(filename: String, content: ByteArray, mimeType: String) {
        runCatching {
            val downloadDir = File(System.getProperty("user.home"), "Downloads")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val targetFile = File(downloadDir, filename)
            targetFile.writeBytes(content)
        }
    }

    actual fun downloadText(filename: String, text: String, mimeType: String) {
        downloadFile(filename, text.encodeToByteArray(), mimeType)
    }

    actual fun isDarkModePreferred(): Boolean = false

    actual fun reloadPage() {}
}

actual object DocumentHead {
    private var titleText: String = ""

    actual fun setTitle(title: String) {
        titleText = title
    }

    actual fun getTitle(): String = titleText

    actual fun setMetaTag(name: String, content: String) {}

    actual fun setFavicon(iconUrl: String) {}
}

actual class WebLocalStorage actual constructor() {
    private val memoryMap = mutableMapOf<String, String>()

    actual fun getItem(key: String): String? = memoryMap[key]

    actual fun setItem(key: String, value: String) {
        memoryMap[key] = value
    }

    actual fun removeItem(key: String) {
        memoryMap.remove(key)
    }

    actual fun clear() {
        memoryMap.clear()
    }
}

actual class WebSessionStorage actual constructor() {
    private val memoryMap = mutableMapOf<String, String>()

    actual fun getItem(key: String): String? = memoryMap[key]

    actual fun setItem(key: String, value: String) {
        memoryMap[key] = value
    }

    actual fun removeItem(key: String) {
        memoryMap.remove(key)
    }

    actual fun clear() {
        memoryMap.clear()
    }
}
