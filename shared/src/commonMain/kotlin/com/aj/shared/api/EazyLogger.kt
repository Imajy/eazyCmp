package com.aj.shared.api

import com.aj.shared.security.redactPiiFromLog

object EazyLogger {
    var isDebugEnabled: Boolean = true

    fun d(message: String) {
        if (isDebugEnabled) println(message)
    }

    fun logApiRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ) {
        if (!isDebugEnabled) return
        println("🚀 ============== API REQUEST ==============")
        println("URL         → $method $url")
        println("HEADERS     →")
        if (headers.isEmpty()) {
            println("  (none)")
        } else {
            headers.forEach { (key, value) ->
                println("  $key: $value")
            }
        }
        println("BODY        →")
        if (body.isNullOrBlank()) {
            println("  (none)")
        } else {
            println("  $body")
        }
        println("🚀 =========================================")
    }

    fun logApiResponse(
        url: String,
        statusCode: Int,
        headers: Map<String, String>,
        durationMs: Long,
        rawResponse: String
    ) {
        if (!isDebugEnabled) return
        println("✅ ============== API RESPONSE =============")
        println("URL         → $url")
        println("STATUS CODE → $statusCode")
        println("TIME        → ${durationMs}ms")
        println("HEADERS     →")
        if (headers.isEmpty()) {
            println("  (none)")
        } else {
            headers.forEach { (key, value) ->
                println("  $key: $value")
            }
        }
        println("RESPONSE    →")
        if (rawResponse.isBlank()) {
            println("  (empty)")
        } else {
            println("  ${redactPiiFromLog(rawResponse)}")
        }
        println("✅ =========================================")
    }

    fun logApiError(url: String, durationMs: Long, error: String?) {
        if (!isDebugEnabled) return
        println("❌ ============== API ERROR ================")
        println("URL         → $url")
        println("TIME        → ${durationMs}ms")
        println("ERROR       → $error")
        println("❌ ========================================")
    }
}
