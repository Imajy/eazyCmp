package com.aj.shared.api

import com.aj.shared.picker.PickedFile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

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

        val startTime = Clock.System.now()
        try {

           /* if (!NetworkMonitor.connected.value) {
                if (options.retryOnConnection) {
                    NetworkMonitor.connected
                        .filter { it }
                        .first()
                } else {
                    emit(Resource.Error("No internet"))
                    return@flow
                }
            }
*/

            println("============== API REQUEST ==============")
            println("BASE        → $base")
            println("URL         → $url")
            println("METHOD      → $method")
            println("QUERY       → $query")
            println("BODY TYPE   → $bodyType")
            println("BODY        → $body")
            println("FILES       → ${files.size}")
            println("TOKEN       → ${config.token != null}")
            println("HEADERS     → ${config.defaultHeaders}")
            println("=========================================")

            val response = client.request(buildUrl(base, endpoint)) {
                println("STEP 2 → inside ktor")
                this.method = method
                applyDefaults(base)

                /**
                 * query params
                 */
                query.forEach { parameter(it.key, it.value) }
                println("STEP 3 → defaults applied")
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

            val duration = Clock.System.now() - startTime

            println("============== API RESPONSE =============")
            val data: Res = response.body()
            val rawResponse = response.bodyAsText()

            println("RAW RESPONSE ↓↓↓")
            println("$url => $duration=> ${rawResponse}")
            println("RAW RESPONSE ↑↑↑")
            emit(Resource.Success(data))
        } catch (e: Exception) {
            val duration = Clock.System.now() - startTime

            println("============== API ERROR ================")
            println("URL         → $url")
            println("TIME        → ${duration}ms")
            println("ERROR       → ${e.message}")
            println("=========================================")
            emit(Resource.Error(e.message ?: "unknown error"))
        }
    }
    @PublishedApi
    internal fun <T> priorityWrapper(
        upstream: Flow<Resource<T>>,
        priority: ApiPriority
    ): Flow<Resource<T>> = flow {

        ApiDispatcher.dispatch(priority) {

            upstream.collect {

                emit(it)

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