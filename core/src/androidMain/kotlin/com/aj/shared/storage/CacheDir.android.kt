package com.aj.shared.storage

import com.aj.shared.api.appContext
import com.aj.shared.api.isAppContextInitialized

actual fun getCacheDir(): String {
    check(isAppContextInitialized()) {
        "Call EazyCmp.init(context) in Application.onCreate() before using cache APIs on Android."
    }
    return appContext.cacheDir.absolutePath + "/image_cache"
}
