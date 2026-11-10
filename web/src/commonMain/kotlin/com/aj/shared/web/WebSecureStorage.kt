package com.aj.shared.web

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Browser-oriented secure-ish key/value store.
 * Values are obfuscated (Base64 + rolling XOR) before localStorage persistence.
 */
class WebSecureStorage(
    private val prefix: String = "eazycmp.secure.",
    private val passphrase: String = "eazycmp-web-default-key",
) {
    private val storage = WebLocalStorage()
    private val indexKey = prefix + "__keys__"

    fun putString(key: String, value: String) {
        storage.setItem(prefix + key, obfuscate(value))
        rememberKey(key)
    }

    fun getString(key: String, defaultValue: String = ""): String {
        val raw = storage.getItem(prefix + key) ?: return defaultValue
        return try {
            deobfuscate(raw)
        } catch (_: Throwable) {
            defaultValue
        }
    }

    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        getString(key, defaultValue.toString()).toBooleanStrictOrNull() ?: defaultValue

    fun putInt(key: String, value: Int) = putString(key, value.toString())
    fun getInt(key: String, defaultValue: Int = 0): Int =
        getString(key, defaultValue.toString()).toIntOrNull() ?: defaultValue

    fun putLong(key: String, value: Long) = putString(key, value.toString())
    fun getLong(key: String, defaultValue: Long = 0L): Long =
        getString(key, defaultValue.toString()).toLongOrNull() ?: defaultValue

    fun remove(key: String) {
        storage.removeItem(prefix + key)
        forgetKey(key)
    }

    fun clear() {
        knownKeys().forEach { storage.removeItem(prefix + it) }
        storage.removeItem(indexKey)
    }

    private fun rememberKey(key: String) {
        val keys = knownKeys().toMutableSet()
        if (keys.add(key)) {
            storage.setItem(indexKey, keys.joinToString("\u0001"))
        }
    }

    private fun forgetKey(key: String) {
        val keys = knownKeys().toMutableSet()
        if (keys.remove(key)) {
            storage.setItem(indexKey, keys.joinToString("\u0001"))
        }
    }

    private fun knownKeys(): List<String> {
        val raw = storage.getItem(indexKey).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split('\u0001').filter { it.isNotBlank() }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun obfuscate(plain: String): String {
        val key = passphrase.encodeToByteArray()
        val bytes = plain.encodeToByteArray()
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) {
            out[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return Base64.encode(out)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun deobfuscate(encoded: String): String {
        val key = passphrase.encodeToByteArray()
        val bytes = Base64.decode(encoded)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) {
            out[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return out.decodeToString()
    }
}
