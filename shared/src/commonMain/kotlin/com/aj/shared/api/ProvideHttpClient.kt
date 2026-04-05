package com.aj.shared.api

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
expect fun provideHttpClient(): HttpClient


expect fun provideSettings() : Settings


fun initSettingsName(name: String) {
    SETTINGS_NAME = name
}

object HttpClientProvider {
    val client: HttpClient by lazy {

        provideHttpClient()
    }
}