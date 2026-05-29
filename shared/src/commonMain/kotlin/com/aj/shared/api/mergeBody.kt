package com.aj.shared.api

fun mergeBody(
    baseName: String,
    body: Map<String, Any?>?
): Map<String, Any?> {

    val config: BaseConfig = ApiConfig.getConfig(baseName)

    val finalMap: MutableMap<String, Any?> = mutableMapOf()

    finalMap.putAll(
        config.defaultBodyParams
    )

    if (body != null) {
        finalMap.putAll(body)
    }

    return finalMap
}