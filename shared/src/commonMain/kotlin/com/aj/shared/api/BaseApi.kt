package com.aj.shared.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

object BaseApi {

    private val client by lazy {

        HttpClient(getPlatformEngine()) {

            install(ContentNegotiation) {

                json(

                    Json {

                        ignoreUnknownKeys = true

                        isLenient = true

                        encodeDefaults = true
                    }
                )
            }

            expectSuccess = false
        }
    }

    internal inline fun <reified T> call(

        url: String,

        method: HttpMethod,

        body: Any? = null,

        query: Map<String, Any?> = emptyMap(),

        headers: Map<String, String> = emptyMap()

    ): Flow<Resource<T>> = flow {

        emit(Resource.Loading())

        try {

            val response: HttpResponse = client.request {

                url(url)

                this.method = method

                // query
                query.forEach {

                    if (it.value != null) {

                        parameter(

                            it.key,

                            it.value.toString()
                        )
                    }
                }

                // headers
                headers.forEach {

                    this.headers.append(

                        it.key,

                        it.value
                    )
                }

                // body
                if (body != null) {

                    contentType(ContentType.Application.Json)

                    setBody(body)
                }
            }

            val result: T = response.body()

            emit(

                Resource.Success(result)
            )

        } catch (e: Exception) {

            emit(

                Resource.Error(

                    message = e.message ?: "Unknown error"
                )
            )
        }
    }
}
