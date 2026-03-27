package com.aj.shared.api

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

object ApplicationContext {
    lateinit var INSTANCE: Context
}

fun initCmp(context: Context) {

    ApplicationContext.INSTANCE = context
}

actual fun createSettings(): Settings {
    val context = ApplicationContext.INSTANCE
    return SharedPreferencesSettings(
        context.getSharedPreferences(

            "CMP_STORAGE",

            Context.MODE_PRIVATE
        )
    )
}


actual fun getPlatformEngine(): HttpClientEngine {

    return OkHttp.create {

        preconfigured = OkHttpClient.Builder()

            .retryOnConnectionFailure(true)

            // CRM EOF fix
            .protocols(listOf(Protocol.HTTP_1_1))

            .connectTimeout(30, TimeUnit.SECONDS)

            .readTimeout(60, TimeUnit.SECONDS)

            .writeTimeout(60, TimeUnit.SECONDS)

            .connectionPool(
                ConnectionPool(0, 1, TimeUnit.SECONDS)
            )
            .build()
    }
}