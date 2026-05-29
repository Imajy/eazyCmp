package com.aj.shared.api

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

fun HttpRequestBuilder.applyDefaults(
    baseName: String
) {

    val config = ApiConfig.getConfig(baseName)

    config.defaultHeaders.forEach { entry ->
        header(
            entry.key,
            entry.value
        )
    }

    config.defaultQueryParams.forEach { entry ->
        parameter(
            entry.key,
            entry.value
        )
    }

    config.token?.let { token ->

        header(
            HttpHeaders.Authorization,
            "Bearer $token"
        )
    }
}

fun buildUrl(
    baseName: String,
    endpoint: String
): String {
    val config = ApiConfig.getConfig(baseName)

    val cleanBase = config.baseUrl.trimEnd('/')

    val cleanEndpoint = endpoint.trimStart('/')

    return "$cleanBase/$cleanEndpoint"
}