package com.aj.shared.analytics

data class AnalyticsEvent(
    val name: String,
    val params: Map<String, String> = emptyMap(),
)
