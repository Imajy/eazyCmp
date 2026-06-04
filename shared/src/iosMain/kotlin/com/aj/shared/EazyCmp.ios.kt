package com.aj.shared

internal actual fun platformInit(context: Any?, settingsName: String) {
    // No context initialization required for iOS
}

internal actual fun getCacheDir(): String {
    val fm = platform.Foundation.NSFileManager.defaultManager
    val cacheUrl = fm.URLsForDirectory(platform.Foundation.NSCachesDirectory, platform.Foundation.NSUserDomainMask).first() as? platform.Foundation.NSURL
    return (cacheUrl?.path ?: "") + "/image_cache"
}
