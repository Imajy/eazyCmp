package com.aj.shared.api

import com.aj.shared.picker.PickedFile
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.let
import kotlin.time.Clock
import com.aj.shared.EazyCmp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first


class ApiClient(val client: HttpClient = HttpClientProvider.client) {
    inline fun <reified Req : Any, reified Res> request(
        base: String,
        endpoint: String,
        method: ApiMethod = ApiMethod.GET,
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
                    method.toKtor(),
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

        val config = ApiConfig.getConfig(base)

        val url = buildUrl(base, endpoint)

        val mockJson = ApiConfig.getMockResponse(endpoint)
        if (mockJson != null) {
            kotlinx.coroutines.delay(500)
            try {
                val data: Res = json.decodeFromString(mockJson)
                emit(Resource.Success(data))
                return@flow
            } catch (e: Exception) {
                emit(Resource.Error("Mock deserialization failed: ${e.message}"))
                return@flow
            }
        }

        val startTime = Clock.System.now()
        try {

            if (!EazyCmp.network.isOnline) {
                if (options.retryOnConnection) {
                    EazyCmp.network.connectivityFlow
                        .filter { it }
                        .first()
                } else {
                    emit(Resource.Error("No internet"))
                    return@flow
                }
            }


            val mergedBody = mergeRequestBody<Req>(base, body)

            EazyLogger.logApiRequest(
                base = base,
                url = url,
                method = method.value,
                query = query,
                bodyType = bodyType,
                hasBody = mergedBody != null,
                fileCount = files.size,
                hasToken = config.token != null
            )

            val response = client.request(buildUrl(base, endpoint)) {
                this.method = method
                applyDefaults(base)
                query.forEach { parameter(it.key, it.value) }

                if (files.isNotEmpty()) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                mergedBody?.let { appendFields(it) }
                                files.forEach { part ->
                                    part.file?.let { file -> appendFile(part.name, file) }
                                }
                            }
                        )
                    )
                } else if (mergedBody != null) {
                    when (bodyType) {
                        BodyType.JSON -> {
                            contentType(ContentType.Application.Json)
                            setBody(mergedBody)
                        }

                        BodyType.FORM_URLENCODED -> {
                            setBody(
                                FormDataContent(
                                    Parameters.build {
                                        appendFields(mergedBody)
                                    }
                                )
                            )
                        }
                        BodyType.FORM_DATA -> {
                            setBody(
                                MultiPartFormDataContent(
                                    formData {
                                        appendFields(mergedBody)
                                    }
                                )
                            )
                        }
                    }
                }
            }

            val duration = Clock.System.now() - startTime
            val rawResponse = response.bodyAsText()
            val data: Res = json.decodeFromString(rawResponse)

            EazyLogger.logApiResponse(
                url = url,
                durationMs = duration.inWholeMilliseconds,
                rawResponse = rawResponse
            )
            emit(Resource.Success(data))
        } catch (e: Exception) {
            val duration = Clock.System.now() - startTime

            EazyLogger.logApiError(
                url = url,
                durationMs = duration.inWholeMilliseconds,
                error = e.message
            )
            emit(Resource.Error(e.message ?: "unknown error"))
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
internal inline fun <reified T : Any> FormBuilder.appendFields(obj: T) {

    val json = Json.encodeToString(obj)

    Json.parseToJsonElement(json)
        .jsonObject
        .forEach { (key, value) ->

            append(
                key,
                (value as? JsonPrimitive)
                    ?.content
                    ?: value.toString()
            )
        }
}

@PublishedApi
internal inline fun <reified T : Any> ParametersBuilder.appendFields(obj: T) {

    val json = Json.encodeToString(obj)

    Json.parseToJsonElement(json)
        .jsonObject
        .forEach { (key, value) ->

            append(
                key,
                (value as? JsonPrimitive)
                    ?.content
                    ?: value.toString()
            )
        }
}


enum class ApiMethod {

    GET,
    POST,
    PUT,
    DELETE,
    PATCH

}



fun ApiMethod.toKtor(): HttpMethod {

    return when (this) {

        ApiMethod.GET -> HttpMethod.Get

        ApiMethod.POST -> HttpMethod.Post

        ApiMethod.PUT -> HttpMethod.Put

        ApiMethod.DELETE -> HttpMethod.Delete

        ApiMethod.PATCH -> HttpMethod.Patch

    }

}