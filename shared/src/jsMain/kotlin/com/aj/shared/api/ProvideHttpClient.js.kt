package com.aj.shared.api

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window

actual fun provideHttpClient(): HttpClient {
    return HttpClient(Js) {
        install(ContentNegotiation) {
            json(json)
        }
    }
}

actual fun provideSettings(): Settings {
    val delegate = window.localStorage
    return StorageSettings(delegate)
}