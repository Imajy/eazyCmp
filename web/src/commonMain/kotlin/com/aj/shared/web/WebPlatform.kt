package com.aj.shared.web

/**
 * Indicates if the current runtime is a Web browser (WasmJs or JS).
 */
expect val isWeb: Boolean

/**
 * Indicates if the current runtime is WebAssembly (WasmJs).
 */
expect val isWasm: Boolean

/**
 * Indicates if the current runtime is JVM Desktop/Server.
 */
expect val isJvm: Boolean

/**
 * Multiplatform Web Browser utility for opening links, clipboard, downloads, and URL manipulation.
 */
expect object WebBrowser {
    fun openUrl(url: String, target: String = "_blank")
    fun copyToClipboard(text: String, onResult: ((Boolean) -> Unit)? = null)
    fun getCurrentUrl(): String
    fun getQueryParam(key: String): String?
    fun getQueryParams(): Map<String, String>
    fun getHash(): String
    fun setHash(hash: String)
    fun downloadFile(filename: String, content: ByteArray, mimeType: String = "application/octet-stream")
    fun downloadText(filename: String, text: String, mimeType: String = "text/plain")
    fun isDarkModePreferred(): Boolean
    fun reloadPage()
}
