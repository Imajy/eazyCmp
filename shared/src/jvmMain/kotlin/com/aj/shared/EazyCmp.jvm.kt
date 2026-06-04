package com.aj.shared

internal actual fun platformInit(context: Any?, settingsName: String) {
    // No context initialization required for JVM
}

internal actual fun getCacheDir(): String {
    val tmp = System.getProperty("java.io.tmpdir")
    return "$tmp/eazycmp_image_cache"
}
