package com.aj.shared.api

data class BaseConfig(

    val baseUrl: String,

    val token: String?,

    val defaultHeaders: Map<String, String>,

    val defaultQueryParams: Map<String, String>,

    val defaultBodyParams: Map<String, Any?>
)


object ApiConfig {

    private val configs: MutableMap<String, BaseConfig> = mutableMapOf()

    fun registerBaseUrl(

        name: String,

        baseUrl: String,

        token: String? = null,

        defaultHeaders: Map<String, String> = emptyMap(),

        defaultQueryParams: Map<String, String> = emptyMap(),

        defaultBodyParams: Map<String, Any?> = emptyMap()
    ) {

        configs[name] = BaseConfig(

            baseUrl = baseUrl,

            token = token,

            defaultHeaders = defaultHeaders,

            defaultQueryParams = defaultQueryParams,

            defaultBodyParams = defaultBodyParams
        )
    }

    fun updateToken(

        name: String,

        token: String
    ) {

        val config = configs[name] ?: return

        configs[name] = config.copy(

            token = token
        )
    }

    fun getConfig(name: String): BaseConfig {

        return configs[name]

            ?: error(

                """

Base url not registered: $name

Registered bases:

${configs.keys.joinToString()}

""".trimIndent()

            )

    }

    fun isRegistered(name: String): Boolean {

        return configs.containsKey(name)

    }

    fun clear(name: String) {

        configs.remove(name)

    }

    fun clearAll() {

        configs.clear()

    }
}