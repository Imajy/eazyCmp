package com.aj.shared

import android.content.Context
import com.aj.shared.api.initEazyCmp
import com.aj.shared.api.appContext
import com.aj.shared.api.isAppContextInitialized

internal actual fun platformInit(context: Any?, settingsName: String) {
    if (context is Context) {
        val isAppDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        com.aj.shared.api.EazyLogger.isDebugEnabled = isAppDebug
        val pkgName = context.packageName ?: ""
        initEazyCmp(context, settingsName)
        com.aj.shared.security.EazyCmpKillSwitch.checkAndStart(pkgName)
    }
}

