package com.aj.api

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.dsl.module

val coreModule = module {
    single {
        json
    }


    single {

        Json {

            ignoreUnknownKeys = true

        }

    }


    single {provideSettings()}


    single {

        SharedViewModel(

            settings = get(),

            json = get()

        )

    }


    single {

        ApiClient()

    }

}



fun initAjay() {
    startKoin {
        modules(
            coreModule
        )
    }
}

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = true
    explicitNulls = false
    coerceInputValues = true
}