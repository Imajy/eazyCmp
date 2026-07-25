package com.aj.shared.network

import com.aj.shared.api.EazyLogger
import com.aj.shared.api.HttpClientProvider
import com.aj.shared.storage.SocketLogItem
import com.aj.shared.storage.SocketLogStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SocketMessage(
    val url: String,
    val event: String,
    val data: String,
    val direction: String, // "SENT" or "RECEIVED"
    val timestampMs: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
)

/**
 * Socket Manager for Kotlin Multiplatform applications.
 * Manages WebSocket connections, handles event sending & receiving, displays real-time console logs,
 * and maintains a 10MB persistent log file storing timestamps, URLs, events, requests, and responses.
 */
class EazySocketManager(
    private val client: HttpClient = HttpClientProvider.client,
    val logStorage: SocketLogStorage = SocketLogStorage()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @PublishedApi
    internal val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val activeSessions = mutableMapOf<String, DefaultClientWebSocketSession>()
    private val messageFlows = mutableMapOf<String, MutableSharedFlow<SocketMessage>>()

    /**
     * Connect to a WebSocket URL and observe incoming messages.
     * Logs the connection and all incoming frames to console & 10MB text log storage.
     */
    fun connect(
        url: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Flow<SocketMessage> = flow {
        val sharedFlow = messageFlows.getOrPut(url) { MutableSharedFlow(extraBufferCapacity = 64) }

        // Log connection start
        EazyLogger.logSocketEvent(
            url = url,
            event = "CONNECT",
            direction = "CONNECT",
            requestData = if (extraHeaders.isNotEmpty()) json.encodeToString(extraHeaders) else null
        )
        logStorage.logEvent(
            url = url,
            event = "CONNECT",
            direction = "CONNECT",
            requestData = if (extraHeaders.isNotEmpty()) json.encodeToString(extraHeaders) else null
        )

        try {
            client.webSocket(urlString = url, request = {
                extraHeaders.forEach { (key, value) -> headers.append(key, value) }
            }) {
                activeSessions[url] = this
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val rawText = frame.readText()
                            val eventName = extractEventName(rawText)

                            val message = SocketMessage(
                                url = url,
                                event = eventName,
                                data = rawText,
                                direction = "RECEIVED"
                            )

                            // Display console log & persist to 10MB log storage
                            EazyLogger.logSocketEvent(
                                url = url,
                                event = eventName,
                                direction = "RECEIVED",
                                responseData = rawText
                            )
                            logStorage.logEvent(
                                url = url,
                                event = eventName,
                                direction = "RECEIVED",
                                responseData = rawText
                            )

                            sharedFlow.emit(message)
                            emit(message)
                        }
                    }
                } finally {
                    activeSessions.remove(url)
                    EazyLogger.logSocketEvent(
                        url = url,
                        event = "DISCONNECT",
                        direction = "DISCONNECT"
                    )
                    logStorage.logEvent(
                        url = url,
                        event = "DISCONNECT",
                        direction = "DISCONNECT"
                    )
                }
            }
        } catch (e: Exception) {
            EazyLogger.logSocketEvent(
                url = url,
                event = "ERROR",
                direction = "ERROR",
                error = e.message
            )
            logStorage.logEvent(
                url = url,
                event = "ERROR",
                direction = "ERROR",
                responseData = e.message
            )
            throw e
        }
    }

    /**
     * Send a raw string or frame to a WebSocket URL.
     * Logs the sent event to console & 10MB log storage.
     */
    suspend fun send(
        url: String,
        message: String,
        eventName: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val event = eventName ?: extractEventName(message)

        // Log sent event
        EazyLogger.logSocketEvent(
            url = url,
            event = event,
            direction = "SENT",
            requestData = message
        )
        logStorage.logEvent(
            url = url,
            event = event,
            direction = "SENT",
            requestData = message
        )

        val session = activeSessions[url]
        if (session != null) {
            session.send(Frame.Text(message))
        } else {
            // One-off send connection if not actively persistent
            client.webSocket(urlString = url, request = {
                extraHeaders.forEach { (key, value) -> headers.append(key, value) }
            }) {
                send(Frame.Text(message))
            }
        }
    }

    /**
     * Send a structured event payload (serializable object or Map) to a WebSocket URL.
     */
    suspend inline fun <reified T : Any> sendEvent(
        url: String,
        event: String,
        payload: T,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val jsonPayload = try {
            json.encodeToString(payload)
        } catch (_: Exception) {
            payload.toString()
        }

        val eventObject = buildString {
            if (jsonPayload.startsWith("{")) {
                append("{\"event\":\"$event\",")
                append(jsonPayload.drop(1))
            } else {
                append("{\"event\":\"$event\",\"data\":$jsonPayload}")
            }
        }

        send(url = url, message = eventObject, eventName = event, extraHeaders = extraHeaders)
    }

    /**
     * Disconnect an active WebSocket URL session.
     */
    suspend fun disconnect(url: String) {
        val session = activeSessions.remove(url)
        if (session != null) {
            try {
                session.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnected by client"))
            } catch (_: Exception) {}
        }
        EazyLogger.logSocketEvent(
            url = url,
            event = "DISCONNECT",
            direction = "DISCONNECT"
        )
        logStorage.logEvent(
            url = url,
            event = "DISCONNECT",
            direction = "DISCONNECT"
        )
    }

    /**
     * Disconnect all active socket sessions.
     */
    suspend fun disconnectAll() {
        val urls = activeSessions.keys.toList()
        urls.forEach { disconnect(it) }
    }

    /**
     * Get all recorded socket log items from the 10MB persistent store.
     */
    fun getLogs(): List<SocketLogItem> = logStorage.getLogs()

    /**
     * Get recorded socket logs for a specific socket URL.
     */
    fun getLogsForUrl(url: String): List<SocketLogItem> = logStorage.getLogsForUrl(url)

    /**
     * Clear all recorded socket logs.
     */
    fun clearLogs() = logStorage.clearAll()

    /**
     * Export all socket logs formatted as a plain text string.
     */
    fun exportLogsAsText(): String = logStorage.exportLogsAsText()

    private fun extractEventName(raw: String): String {
        if (!raw.startsWith("{") || !raw.endsWith("}")) return "MESSAGE"
        return runCatching {
            val element = json.parseToJsonElement(raw).jsonObject
            val eventVal = element["event"]?.jsonPrimitive?.content
                ?: element["type"]?.jsonPrimitive?.content
                ?: element["action"]?.jsonPrimitive?.content
            eventVal ?: "MESSAGE"
        }.getOrDefault("MESSAGE")
    }
}
