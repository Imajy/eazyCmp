package com.aj.shared.api

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.prefs.Preferences

actual fun provideHttpClient(): HttpClient {

    return HttpClient(CIO) {

        install(ContentNegotiation) {

            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }

        install(Logging) {

            level = LogLevel.ALL

            logger = object : Logger {

                override fun log(message: String) {

                    println("KTOR → $message")

                }
            }

        }
    }

}

actual fun provideSettings(name : String): Settings {
    val delegate = Preferences.userRoot().node(name)
    return PreferencesSettings(delegate)
}