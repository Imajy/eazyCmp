package com.aj.shared.api


sealed class ApiResult<T> {

    data class Success<T>(
        val data: T
    ) : ApiResult<T>()

    data class Error<T>(
        val message: String,
        val code: Int? = null
    ) : ApiResult<T>()
}