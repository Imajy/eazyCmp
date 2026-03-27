package com.aj.shared.api

import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual fun createSettings(): Settings {
    return Settings()
}

actual fun getPlatformEngine(): HttpClientEngine {
    return CIO.create()
}