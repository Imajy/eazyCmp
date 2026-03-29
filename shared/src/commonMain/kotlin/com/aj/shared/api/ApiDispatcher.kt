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

    suspend fun dispatch(

        priority: ApiPriority,

        block: suspend () -> Unit

    ) {

        when (priority) {

            ApiPriority.HIGH -> block()

            ApiPriority.NORMAL -> block()

            ApiPriority.LOW -> block()

        }

    }

}