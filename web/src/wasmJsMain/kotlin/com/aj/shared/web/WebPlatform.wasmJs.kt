@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.aj.shared.web

actual val isWeb: Boolean = true
actual val isWasm: Boolean = true
actual val isJvm: Boolean = false

private fun jsOpenUrl(url: String, target: String): Unit = js("window.open(url, target)")
private fun jsCopyText(text: String): Unit = js("navigator.clipboard.writeText(text)")
private fun jsCurrentUrl(): String = js("window.location.href")
private fun jsGetSearch(): String = js("window.location.search")
private fun jsGetHash(): String = js("window.location.hash")
private fun jsSetHash(hash: String): Unit = js("window.location.hash = hash")
private fun jsIsDark(): Boolean = js("window.matchMedia('(prefers-color-scheme: dark)').matches")
private fun jsReload(): Unit = js("window.location.reload()")
private fun jsSetTitle(title: String): Unit = js("document.title = title")
private fun jsGetTitle(): String = js("document.title")
private fun jsSetMeta(name: String, content: String): Unit = js("""{
    var meta = document.querySelector("meta[name='" + name + "']");
    if (!meta) {
        meta = document.createElement("meta");
        meta.setAttribute("name", name);
        document.head.appendChild(meta);
    }
    meta.setAttribute("content", content);
}""")
private fun jsSetFavicon(iconUrl: String): Unit = js("""{
    var link = document.querySelector("link[rel~='icon']");
    if (!link) {
        link = document.createElement("link");
        link.setAttribute("rel", "icon");
        document.head.appendChild(link);
    }
    link.setAttribute("href", iconUrl);
}""")
private fun jsGetLocalStorage(key: String): String? = js("localStorage.getItem(key)")
private fun jsSetLocalStorage(key: String, value: String): Unit = js("localStorage.setItem(key, value)")
private fun jsRemoveLocalStorage(key: String): Unit = js("localStorage.removeItem(key)")
private fun jsClearLocalStorage(): Unit = js("localStorage.clear()")
private fun jsGetSessionStorage(key: String): String? = js("sessionStorage.getItem(key)")
private fun jsSetSessionStorage(key: String, value: String): Unit = js("sessionStorage.setItem(key, value)")
private fun jsRemoveSessionStorage(key: String): Unit = js("sessionStorage.removeItem(key)")
private fun jsClearSessionStorage(): Unit = js("sessionStorage.clear()")
private fun jsDownloadText(filename: String, textContent: String, mimeType: String): Unit = js("""{
    var blob = new Blob([textContent], {type: mimeType});
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}""")

actual object WebBrowser {
    actual fun openUrl(url: String, target: String) {
        jsOpenUrl(url, target)
    }

    actual fun copyToClipboard(text: String, onResult: ((Boolean) -> Unit)?) {
        try {
            jsCopyText(text)
            onResult?.invoke(true)
        } catch (_: Throwable) {
            onResult?.invoke(false)
        }
    }

    actual fun getCurrentUrl(): String {
        return jsCurrentUrl()
    }

    actual fun getQueryParam(key: String): String? {
        val search = jsGetSearch()
        if (search.isBlank()) return null
        return getQueryParams()[key]
    }

    actual fun getQueryParams(): Map<String, String> {
        val search = jsGetSearch()
        if (search.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = search.removePrefix("?").split("&")
        for (pair in pairs) {
            val key = pair.substringBefore("=")
            val value = pair.substringAfter("=", "")
            if (key.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    actual fun getHash(): String {
        return jsGetHash().removePrefix("#")
    }

    actual fun setHash(hash: String) {
        val clean = if (hash.startsWith("#")) hash else "#$hash"
        jsSetHash(clean)
    }

    actual fun downloadFile(filename: String, content: ByteArray, mimeType: String) {
        val text = content.decodeToString()
        jsDownloadText(filename, text, mimeType)
    }

    actual fun downloadText(filename: String, text: String, mimeType: String) {
        jsDownloadText(filename, text, mimeType)
    }

    actual fun isDarkModePreferred(): Boolean {
        return jsIsDark()
    }

    actual fun reloadPage() {
        jsReload()
    }
}

actual object DocumentHead {
    actual fun setTitle(title: String) {
        jsSetTitle(title)
    }

    actual fun getTitle(): String {
        return jsGetTitle()
    }

    actual fun setMetaTag(name: String, content: String) {
        jsSetMeta(name, content)
    }

    actual fun setFavicon(iconUrl: String) {
        jsSetFavicon(iconUrl)
    }
}

actual class WebLocalStorage actual constructor() {
    actual fun getItem(key: String): String? = jsGetLocalStorage(key)

    actual fun setItem(key: String, value: String) {
        jsSetLocalStorage(key, value)
    }

    actual fun removeItem(key: String) {
        jsRemoveLocalStorage(key)
    }

    actual fun clear() {
        jsClearLocalStorage()
    }
}

actual class WebSessionStorage actual constructor() {
    actual fun getItem(key: String): String? = jsGetSessionStorage(key)

    actual fun setItem(key: String, value: String) {
        jsSetSessionStorage(key, value)
    }

    actual fun removeItem(key: String) {
        jsRemoveSessionStorage(key)
    }

    actual fun clear() {
        jsClearSessionStorage()
    }
}
