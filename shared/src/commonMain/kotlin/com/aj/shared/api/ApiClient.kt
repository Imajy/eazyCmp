package com.aj.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.*

class ApiClient(

    val client: HttpClient = HttpClientProvider.client

) {

    inline fun <reified Res> get(

        base: String,

        endpoint: String,

        query: Map<String, String> = emptyMap(),

        options: RequestOptions = RequestOptions()

    ): Flow<Resource<Res>> {

        return flow {

            emit(Resource.Loading())

            emitAll(

                requestFlow(

                    base = base,

                    endpoint = endpoint,

                    query = query,

                    body = null,

                    options = options

                )

            )

        }

    }



    inline fun <reified Res> post(

        base: String,

        endpoint: String,

        body: Map<String, Any?> = emptyMap(),

        options: RequestOptions = RequestOptions()

    ): Flow<Resource<Res>> {

        val finalBody = mergeBody(base, body)

        return flow {

            emit(Resource.Loading())

            emitAll(

                requestFlow(

                    base = base,

                    endpoint = endpoint,

                    query = emptyMap(),

                    body = finalBody,

                    options = options

                )

            )

        }

    }



    @PublishedApi
    internal inline fun <reified Res> requestFlow(

        base: String,

        endpoint: String,

        query: Map<String, String>,

        body: Map<String, Any?>?,

        options: RequestOptions

    ): Flow<Resource<Res>> {

        return flow {

            try {

                /**
                 * wait for internet if required
                 */
                if (!NetworkMonitor.connected.value) {

                    if (options.retryOnConnection) {

                        NetworkMonitor.connected

                            .filter { it }

                            .first()

                    } else {

                        emit(Resource.Error("No internet"))

                        return@flow

                    }

                }


                val response = if (body == null) {

                    client.get(buildUrl(base, endpoint)) {

                        applyDefaults(base)

                        query.forEach {

                            parameter(it.key, it.value)

                        }

                    }

                } else {

                    client.post(buildUrl(base, endpoint)) {

                        contentType(ContentType.Application.Json)

                        applyDefaults(base)

                        setBody(body)

                    }

                }


                val data: Res = response.body()

                emit(Resource.Success(data))

            } catch (e: Exception) {

                emit(

                    Resource.Error(

                        e.message ?: "unknown error"

                    )

                )

            }

        }.let { flow ->

            priorityWrapper(flow, options.priority)

        }

    }



    @PublishedApi
    internal fun <T> priorityWrapper(

        upstream: Flow<Resource<T>>,

        priority: ApiPriority

    ): Flow<Resource<T>> = channelFlow {
        ApiDispatcher.dispatch(priority) {
            upstream.collect {
                send(it)
            }
        }
    }
}