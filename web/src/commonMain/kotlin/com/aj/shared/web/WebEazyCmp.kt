package com.aj.shared.web

/**
 * Web-facing facade for JS / Wasm / JVM-browser helpers.
 *
 * ```kotlin
 * WebEazyCmp.storage.putString("token", "…")
 * WebEazyCmp.media.pick(WebPickerType.IMAGE) { file -> … }
 * WebEazyCmp.install { register("analytics", myAnalytics) }
 * ```
 */
object WebEazyCmp {
    val storage: WebSecureStorage by lazy { WebSecureStorage() }
    val media: WebMediaPicker by lazy { WebMediaPicker() }
    val local: WebLocalStorage by lazy { WebLocalStorage() }
    val session: WebSessionStorage by lazy { WebSessionStorage() }

    private val extras = mutableMapOf<String, Any>()

    fun install(block: WebEazyRegistry.() -> Unit) {
        WebEazyRegistry().apply(block)
    }

    fun register(key: String, service: Any) {
        extras[key] = service
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = extras[key] as? T
}

class WebEazyRegistry internal constructor() {
    fun register(key: String, service: Any) = WebEazyCmp.register(key, service)
}
