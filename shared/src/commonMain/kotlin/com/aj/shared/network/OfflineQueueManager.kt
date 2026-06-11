package com.aj.shared.network

import com.aj.shared.EazyCmp
import com.aj.shared.api.json
import com.aj.shared.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private const val KEY_OFFLINE_QUEUE = "eazy_cmp_offline_queue"

@Serializable
data class QueuedRequest(
    val id: String,
    val tag: String,
    val payloadJson: String,
    val createdAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
)

class OfflineQueueManager internal constructor(
    private val storage: SecureStorage = SecureStorage(),
) {
    private val _flushSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flushSignal: SharedFlow<Unit> = _flushSignal.asSharedFlow()

    fun enqueue(request: QueuedRequest) {
        val queue = loadQueue().toMutableList()
        queue.removeAll { it.id == request.id }
        queue.add(request)
        saveQueue(queue)
    }

    fun remove(id: String) {
        saveQueue(loadQueue().filterNot { it.id == id })
    }

    fun pending(): List<QueuedRequest> = loadQueue()

    fun clear() = storage.remove(KEY_OFFLINE_QUEUE)

    fun startAutoFlush(scope: CoroutineScope, onFlush: suspend (List<QueuedRequest>) -> Unit) {
        scope.launch {
            EazyCmp.network.connectivityFlow.collect { online ->
                if (!online) return@collect
                val queue = loadQueue()
                if (queue.isEmpty()) return@collect
                onFlush(queue)
                _flushSignal.emit(Unit)
            }
        }
    }

    private fun loadQueue(): List<QueuedRequest> {
        val raw = storage.getString(KEY_OFFLINE_QUEUE)
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<QueuedRequest>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveQueue(queue: List<QueuedRequest>) {
        storage.putString(KEY_OFFLINE_QUEUE, json.encodeToString(queue))
    }
}
