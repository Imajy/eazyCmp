package com.aj.shared

import android.content.Context
import com.aj.shared.api.initEazyCmp
import com.aj.shared.api.appContext
import com.aj.shared.api.isAppContextInitialized

internal actual fun platformInit(context: Any?, settingsName: String) {
    if (context is Context) {
        initEazyCmp(context, settingsName)
    }
}

internal actual fun getCacheDir(): String {
    check(isAppContextInitialized()) {
        "Call EazyCmp.init(context) in Application.onCreate() before using cache APIs on Android."
    }
    return appContext.cacheDir.absolutePath + "/image_cache"
}
