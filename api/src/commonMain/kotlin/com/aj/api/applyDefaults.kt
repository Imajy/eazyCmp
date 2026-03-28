package com.aj.api

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders


/**
 * applyDefaults
 *
 * Applies default configuration values to every HTTP request.
 *
 * This function automatically attaches:
 *
 * 1. Default headers
 * 2. Default query parameters
 * 3. Authorization token
 *
 * based on the base configuration registered using ApiConfig.registerBaseUrl().
 *
 *
 * Why this exists
 * ---------------
 *
 * Many APIs require certain values to be included in every request.
 *
 * Example headers:
 *
 * Authorization token
 * platform type
 * app version
 * device id
 *
 *
 * Example query params:
 *
 * language
 * region
 * appVersion
 *
 *
 * Instead of manually adding these values in every API call,
 * they can be defined once in ApiConfig.
 *
 *
 * Example configuration:
 *
 * ApiConfig.registerBaseUrl(
 *     name = "main",
 *
 *     baseUrl = "https://api.example.com",
 *
 *     token = "abc123",
 *
 *     defaultHeaders = mapOf(
 *         "platform" to "android",
 *         "appVersion" to "1.0"
 *     ),
 *
 *     defaultQueryParams = mapOf(
 *         "lang" to "en"
 *     )
 * )
 *
 *
 * Now every request automatically includes:
 *
 * Headers:
 * Authorization → Bearer abc123
 * platform → android
 * appVersion → 1.0
 *
 * Query:
 * lang=en
 *
 *
 * Example final request:
 *
 * https://api.example.com/users?lang=en
 *
 * Headers:
 * Authorization: Bearer abc123
 * platform: android
 * appVersion: 1.0
 *
 *
 * Parameters
 * ----------
 *
 * baseName:
 * Name of the registered base configuration.
 *
 *
 * Used internally by:
 *
 * ApiClient.get()
 * ApiClient.post()
 * ApiClient.put()
 * ApiClient.delete()
 *
 *
 * Library users normally DO NOT call this function directly.
 */
fun HttpRequestBuilder.applyDefaults(

    baseName: String
) {

    val config = ApiConfig.getConfig(baseName)


    /**
     * Add default headers
     */
    config.defaultHeaders.forEach { entry ->

        header(

            entry.key,

            entry.value
        )
    }


    /**
     * Add default query parameters
     */
    config.defaultQueryParams.forEach { entry ->

        parameter(

            entry.key,

            entry.value
        )
    }


    /**
     * Add Authorization header if token exists
     */
    config.token?.let { token ->

        header(

            HttpHeaders.Authorization,

            "Bearer $token"
        )
    }
}




/**
 * buildUrl
 *
 * Combines baseUrl and endpoint into a valid full URL.
 *
 *
 * Why this function exists
 * ------------------------
 *
 * Users only need to pass the endpoint path.
 *
 * Example:
 *
 * baseUrl = https://api.example.com
 *
 * endpoint = users/list
 *
 *
 * Final URL:
 *
 * https://api.example.com/users/list
 *
 *
 * This prevents common mistakes such as:
 *
 * double slashes
 *
 * https://api.example.com//users
 *
 *
 * missing slashes
 *
 * https://api.example.comusers
 *
 *
 * This function automatically cleans:
 *
 * trailing slash from baseUrl
 * leading slash from endpoint
 *
 *
 * Example inputs supported:
 *
 * baseUrl = https://api.example.com/
 * endpoint = /users
 *
 * OR
 *
 * baseUrl = https://api.example.com
 * endpoint = users
 *
 *
 * both produce:
 *
 * https://api.example.com/users
 *
 *
 * Parameters
 * ----------
 *
 * baseName:
 * Name of the registered base configuration.
 *
 * endpoint:
 * API path relative to baseUrl.
 *
 *
 * Example usage:
 *
 * apiClient.get(
 *     base = "main",
 *     endpoint = "users"
 * )
 *
 *
 * Result URL:
 *
 * https://api.example.com/users
 *
 *
 * Multiple base URL support
 * -------------------------
 *
 * ApiConfig.registerBaseUrl("main", "https://api.main.com")
 *
 * ApiConfig.registerBaseUrl("payment", "https://pay.api.com")
 *
 *
 * apiClient.get(
 *     base = "payment",
 *     endpoint = "orders"
 * )
 *
 *
 * Result:
 *
 * https://pay.api.com/orders
 *
 *
 * Returns
 * -------
 *
 * Full URL string.
 *
 */
fun buildUrl(

    baseName: String,

    endpoint: String

): String {

    val config = ApiConfig.getConfig(baseName)


    /**
     * remove trailing slash from base url
     */
    val cleanBase = config.baseUrl.trimEnd('/')


    /**
     * remove starting slash from endpoint
     */
    val cleanEndpoint = endpoint.trimStart('/')


    return "$cleanBase/$cleanEndpoint"
}