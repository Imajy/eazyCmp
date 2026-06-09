package com.aj.shared.api

object EazyLogger {
    var isDebugEnabled: Boolean = false

    fun d(message: String) {
        if (isDebugEnabled) println(message)
    }

    fun logApiRequest(
        base: String,
        url: String,
        method: String,
        query: Map<String, String>,
        bodyType: BodyType,
        hasBody: Boolean,
        fileCount: Int,
        hasToken: Boolean
    ) {
        if (!isDebugEnabled) return
        println("============== API REQUEST ==============")
        println("BASE        → $base")
        println("URL         → $url")
        println("METHOD      → $method")
        println("QUERY       → $query")
        println("BODY TYPE   → $bodyType")
        println("BODY        → ${if (hasBody) "(present)" else "(none)"}")
        println("FILES       → $fileCount")
        println("TOKEN       → $hasToken")
        println("=========================================")
    }

    fun logApiResponse(url: String, durationMs: Long, rawResponse: String) {
        if (!isDebugEnabled) return
        println("============== API RESPONSE =============")
        println("$url => ${durationMs}ms")
        val preview = if (rawResponse.length > 500) rawResponse.take(500) + "..." else rawResponse
        println(preview)
        println("=========================================")
    }

    fun logApiError(url: String, durationMs: Long, error: String?) {
        if (!isDebugEnabled) return
        println("============== API ERROR ================")
        println("URL         → $url")
        println("TIME        → ${durationMs}ms")
        println("ERROR       → $error")
        println("=========================================")
    }
}
