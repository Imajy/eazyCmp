package com.aj.shared.api

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

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

    fun dispatch(
        priority: ApiPriority,
        block: suspend () -> Unit
    ) {
        scope.launch {
            when (priority) {
                ApiPriority.HIGH -> high.send(block)
                ApiPriority.NORMAL -> normal.send(block)
                ApiPriority.LOW -> low.send(block)
            }
        }
    }
}