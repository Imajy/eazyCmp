package com.aj.shared.api

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.prefs.Preferences

actual fun provideHttpClient(): HttpClient {

    return io.ktor.client.HttpClient(CIO) {

        install(ContentNegotiation) {

            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }
}

actual fun provideSettings(): Settings {
    val delegate = Preferences.userRoot().node("app_settings")
    return PreferencesSettings(delegate)
}