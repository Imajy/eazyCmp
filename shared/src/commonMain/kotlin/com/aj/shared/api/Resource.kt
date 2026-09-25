package com.aj.shared.api

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(
        data: T
    ) : Resource<T>(data)

    class Error<T>(
        message: String,
        data: T? = null
    ) : Resource<T>(data, message)

    class Loading<T>(
        data: T? = null
    ) : Resource<T>(data)
}

/**
 * Awaits the terminal result of the resource flow, suspending until Success or Error.
 * Throws IllegalStateException if the request failed.
 */
suspend fun <T> kotlinx.coroutines.flow.Flow<Resource<T>>.await(): T {
    var lastError: String? = null
    var resultData: T? = null
    this.collect { res ->
        when (res) {
            is Resource.Success -> {
                resultData = res.data
            }
            is Resource.Error -> {
                lastError = res.message ?: "Unknown API error"
            }
            is Resource.Loading -> {}
        }
    }
    if (resultData != null) return resultData!!
    throw IllegalStateException(lastError ?: "Request completed without data")
}

/**
 * Awaits the terminal result of the resource flow, returning null if it errors.
 */
suspend fun <T> kotlinx.coroutines.flow.Flow<Resource<T>>.awaitOrNull(): T? {
    return runCatching { await() }.getOrNull()
}