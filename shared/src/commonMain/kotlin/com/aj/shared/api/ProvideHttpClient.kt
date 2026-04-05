package com.aj.shared.api

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
expect fun provideHttpClient(): HttpClient


expect fun provideSettings(name : String): Settings

object HttpClientProvider {
    val client: HttpClient by lazy {

        provideHttpClient()
    }
}