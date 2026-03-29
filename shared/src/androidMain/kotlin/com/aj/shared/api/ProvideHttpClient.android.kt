package com.aj.shared.api

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

actual fun provideHttpClient(): HttpClient {

    return HttpClient(OkHttp) {

        install(ContentNegotiation) {

            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
        install(Logging) {

            logger = Logger.DEFAULT

            level = LogLevel.ALL

        }
    }
}

lateinit var appContext: Context

fun initSettings(context: Context) {
    appContext = context
}

actual fun provideSettings(): Settings {

    val pref = appContext.getSharedPreferences(
        "app_settings",
        Context.MODE_PRIVATE
    )

    return SharedPreferencesSettings(pref)
}