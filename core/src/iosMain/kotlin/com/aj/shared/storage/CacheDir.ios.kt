package com.aj.shared.storage

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun getCacheDir(): String {
    val fm = NSFileManager.defaultManager
    val cacheUrl = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first() as? NSURL
    return (cacheUrl?.path ?: "") + "/image_cache"
}
