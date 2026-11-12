package com.aj.shared.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object ApiDispatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Bounded parallelism per priority — avoids a single hung request stalling the whole lane.
    private val high = Semaphore(4)
    private val normal = Semaphore(6)
    private val low = Semaphore(2)

    suspend fun dispatch(
        priority: ApiPriority,
        block: suspend () -> Unit
    ) {
        val semaphore = when (priority) {
            ApiPriority.HIGH -> high
            ApiPriority.NORMAL -> normal
            ApiPriority.LOW -> low
        }
        val done = CompletableDeferred<Unit>()
        scope.launch {
            try {
                semaphore.withPermit {
                    block()
                }
                done.complete(Unit)
            } catch (e: Exception) {
                done.completeExceptionally(e)
            }
        }
        done.await()
    }
}
