package com.aj.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
class ApiClient(val client: HttpClient = HttpClientProvider.client) {
    inline fun <reified Req : Any, reified Res> request(
        base: String,
        endpoint: String,
        method: HttpMethod = HttpMethod.Get,
        body: Req? = null,
        query: Map<String, String> = emptyMap(),
        files: List<FilePart> = emptyList(),
        bodyType: BodyType = BodyType.JSON,
        options: RequestOptions = RequestOptions()
    ): Flow<Resource<Res>> = flow {
        emit(Resource.Loading())
        emitAll(
            priorityWrapper(
                requestFlow(
                    base,
                    endpoint,
                    method,
                    body,
                    query,
                    files,
                    bodyType,
                    options
                ),
                options.priority
            )
        )
    }

    @PublishedApi
    internal inline fun <reified Req : Any, reified Res> requestFlow(
        base: String,
        endpoint: String,
        method: HttpMethod,
        body: Req?,
        query: Map<String, String>,
        files: List<FilePart>,
        bodyType: BodyType,
        options: RequestOptions
    ): Flow<Resource<Res>> = flow {

        try {
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

            val response = client.request(buildUrl(base, endpoint)) {
                this.method = method
                applyDefaults(base)

                /**
                 * query params
                 */
                query.forEach { parameter(it.key, it.value) }

                /**
                 * multipart
                 */
                if (files.isNotEmpty()) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                body?.let { appendFields(it) }
                                files.forEach { part ->
                                    part.file?.let { file -> appendFile(part.name, file) }
                                }
                            }
                        )
                    )
                }

                /**
                 * normal body
                 */
                else if (body != null) {

                    when (bodyType) {
                        BodyType.JSON -> {
                            contentType(ContentType.Application.Json)
                            setBody(body)
                        }

                        BodyType.FORM_URLENCODED -> {
                            setBody(
                                FormDataContent(
                                    Parameters.build {
                                        appendFields(body)
                                    }
                                )
                            )
                        }
                        BodyType.FORM_DATA -> {
                            setBody(
                                MultiPartFormDataContent(
                                    formData {
                                        appendFields(body)
                                    }
                                )
                            )
                        }
                    }
                }
            }

            val data: Res = response.body()
            emit(Resource.Success(data))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "unknown error"))
        }
    }

    @PublishedApi
    internal fun <T> priorityWrapper(
        upstream: Flow<Resource<T>>,
        priority: ApiPriority
    ): Flow<Resource<T>> = channelFlow {
        ApiDispatcher.dispatch(priority) {
            upstream.collect { send(it) }
        }
    }
}

fun FormBuilder.appendFile(
    key: String,
    file: PickedFile
) {
    append(
        key,
        file.bytes,
        Headers.build {
            append(
                HttpHeaders.ContentType,
                file.mimeType ?: "application/octet-stream"
            )
            append(
                HttpHeaders.ContentDisposition,
                "filename=\"${file.fileName ?: "file"}\""
            )
        }
    )
}

@PublishedApi
internal fun FormBuilder.appendFields(obj: Any) {
    val json = Json.encodeToString(obj)
    Json.parseToJsonElement(json)
        .jsonObject
        .forEach {
            append(
                it.key,
                it.value.toString()
            )
        }
}

@PublishedApi
internal fun ParametersBuilder.appendFields(obj: Any) {
    val json = Json.encodeToString(obj)
    Json.parseToJsonElement(json)
        .jsonObject
        .forEach {
            append(
                it.key,
                it.value.toString()
            )
        }
}