package com.aj.shared.api

import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

val coreModule = module {
    single { json }
    single { provideSettings(SETTINGS_NAME) }
    single { SharedViewModel(settings = get(), json = get()) }

    single { ApiClient() }
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