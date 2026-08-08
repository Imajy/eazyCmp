package com.aj.shared.api

import com.aj.shared.EazyCmp
import com.aj.shared.network.EazySocketManager
import com.aj.shared.storage.SocketLogStorage
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

val coreModule = module {
    single { json }
    single { provideSettings() }
    single { SharedViewModel(settings = get(), json = get()) }

    single { ApiClient() }
    single { SocketLogStorage() }
    single { EazyCmp.socket }
    single { com.aj.shared.db.EazyCmpDatabase(provideSqlDriver()) }
}
var SETTINGS_NAME = "app_settings"


fun eazyModule(): Module = coreModule

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = true
    explicitNulls = false
    coerceInputValues = true
}