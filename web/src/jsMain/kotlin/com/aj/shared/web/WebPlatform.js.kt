package com.aj.shared.web

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.get
import org.w3c.dom.set
import org.w3c.dom.url.URL
import org.w3c.dom.url.URLSearchParams

actual val isWeb: Boolean = true
actual val isWasm: Boolean = false
actual val isJvm: Boolean = false

actual object WebBrowser {
    actual fun openUrl(url: String, target: String) {
        window.open(url, target)
    }

    actual fun copyToClipboard(text: String, onResult: ((Boolean) -> Unit)?) {
        try {
            window.navigator.clipboard.writeText(text).then(
                onFulfilled = { onResult?.invoke(true) },
                onRejected = { onResult?.invoke(false) }
            )
        } catch (_: Throwable) {
            onResult?.invoke(false)
        }
    }

    actual fun getCurrentUrl(): String {
        return window.location.href
    }

    actual fun getQueryParam(key: String): String? {
        val search = window.location.search
        if (search.isBlank()) return null
        val params = URLSearchParams(search)
        return params.get(key)
    }

    actual fun getQueryParams(): Map<String, String> {
        val search = window.location.search
        if (search.isBlank()) return emptyMap()
        val params = URLSearchParams(search)
        val result = mutableMapOf<String, String>()
        val pairs = search.removePrefix("?").split("&")
        for (pair in pairs) {
            val key = pair.substringBefore("=")
            val value = pair.substringAfter("=", "")
            if (key.isNotBlank()) {
                result[key] = params.get(key) ?: value
            }
        }
        return result
    }

    actual fun getHash(): String {
        return window.location.hash.removePrefix("#")
    }

    actual fun setHash(hash: String) {
        window.location.hash = if (hash.startsWith("#")) hash else "#$hash"
    }

    actual fun downloadFile(filename: String, content: ByteArray, mimeType: String) {
        downloadText(filename, content.decodeToString(), mimeType)
    }

    actual fun downloadText(filename: String, text: String, mimeType: String) {
        val blob = org.w3c.files.Blob(
            arrayOf(text),
            org.w3c.files.BlobPropertyBag(type = mimeType)
        )
        val url = URL.createObjectURL(blob)
        val a = document.createElement("a") as HTMLAnchorElement
        a.href = url
        a.download = filename
        document.body?.appendChild(a)
        a.click()
        document.body?.removeChild(a)
        URL.revokeObjectURL(url)
    }

    actual fun isDarkModePreferred(): Boolean {
        return window.matchMedia("(prefers-color-scheme: dark)").matches
    }

    actual fun reloadPage() {
        window.location.reload()
    }
}

actual object DocumentHead {
    actual fun setTitle(title: String) {
        document.title = title
    }

    actual fun getTitle(): String {
        return document.title
    }

    actual fun setMetaTag(name: String, content: String) {
        var meta = document.querySelector("meta[name='$name']")
        if (meta == null) {
            meta = document.createElement("meta")
            meta.setAttribute("name", name)
            document.head?.appendChild(meta)
        }
        meta.setAttribute("content", content)
    }

    actual fun setFavicon(iconUrl: String) {
        var link = document.querySelector("link[rel~='icon']")
        if (link == null) {
            link = document.createElement("link")
            link.setAttribute("rel", "icon")
            document.head?.appendChild(link)
        }
        link.setAttribute("href", iconUrl)
    }
}

actual class WebLocalStorage actual constructor() {
    actual fun getItem(key: String): String? = localStorage[key]

    actual fun setItem(key: String, value: String) {
        localStorage[key] = value
    }

    actual fun removeItem(key: String) {
        localStorage.removeItem(key)
    }

    actual fun clear() {
        localStorage.clear()
    }
}

actual class WebSessionStorage actual constructor() {
    actual fun getItem(key: String): String? = sessionStorage[key]

    actual fun setItem(key: String, value: String) {
        sessionStorage[key] = value
    }

    actual fun removeItem(key: String) {
        sessionStorage.removeItem(key)
    }

    actual fun clear() {
        sessionStorage.clear()
    }
}
