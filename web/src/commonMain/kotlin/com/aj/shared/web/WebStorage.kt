package com.aj.shared.web

/**
 * Access browser localStorage with fallback in-memory cache for JVM/testing.
 */
expect class WebLocalStorage() {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
    fun clear()
}

/**
 * Access browser sessionStorage with fallback in-memory cache for JVM/testing.
 */
expect class WebSessionStorage() {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
    fun clear()
}
