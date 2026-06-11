package com.aj.shared.network

import com.aj.shared.api.HttpClientProvider
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class WebSocketClient(private val client: io.ktor.client.HttpClient = HttpClientProvider.client) {

    fun connect(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Flow<String> = flow {
        client.webSocket(urlString = url, request = {
            extraHeaders.forEach { (key, value) -> headers.append(key, value) }
        }) {
            listenIncoming(this)
        }
    }

    suspend fun send(url: String, message: String, extraHeaders: Map<String, String> = emptyMap()) {
        client.webSocket(urlString = url, request = {
            extraHeaders.forEach { (key, value) -> headers.append(key, value) }
        }) {
            send(Frame.Text(message))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.listenIncoming(
        session: DefaultClientWebSocketSession,
    ) {
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                emit(frame.readText())
            }
        }
    }
}
