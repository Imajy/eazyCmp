package com.aj.shared.storage

actual fun getCacheDir(): String {
    val tmp = System.getProperty("java.io.tmpdir")
    return "$tmp/eazycmp_image_cache"
}
