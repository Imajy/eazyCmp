package com.aj.shared

import com.aj.shared.api.SETTINGS_NAME

internal actual fun platformInit(context: Any?, settingsName: String) {
    SETTINGS_NAME = settingsName
}

