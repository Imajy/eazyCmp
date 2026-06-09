package com.aj.shared.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers

object ApiDispatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val high = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val normal = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val low = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            while (true) {
                when {
                    !high.isEmpty -> high.receive().invoke()
                    !normal.isEmpty -> normal.receive().invoke()
                    else -> low.receive().invoke()
                }
            }
        }
    }

    suspend fun dispatch(
        priority: ApiPriority,
        block: suspend () -> Unit
    ) {
        val channel = when (priority) {
            ApiPriority.HIGH -> high
            ApiPriority.NORMAL -> normal
            ApiPriority.LOW -> low
        }
        val done = CompletableDeferred<Unit>()
        channel.send {
            try {
                block()
                done.complete(Unit)
            } catch (e: Exception) {
                done.completeExceptionally(e)
            }
        }
        done.await()
    }
}
