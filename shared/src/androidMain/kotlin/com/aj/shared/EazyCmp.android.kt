package com.aj.shared

import android.content.Context
import com.aj.shared.api.initEazyCmp

internal actual fun platformInit(context: Any?, settingsName: String) {
    if (context is Context) {
        initEazyCmp(context, settingsName)
    }
}
