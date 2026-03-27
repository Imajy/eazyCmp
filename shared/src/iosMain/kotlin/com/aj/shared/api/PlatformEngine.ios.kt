package com.aj.shared.api

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSUserDefaults

actual fun getPlatformEngine(): HttpClientEngine {

    return Darwin.create()
}

actual fun createSettings(): Settings {

    return NSUserDefaultsSettings(

        NSUserDefaults.standardUserDefaults
    )
}