package com.aj.shared.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual class SecureStorage actual constructor() {
    private val prefs = Preferences.userNodeForPackage(SecureStorage::class.java)
    private val masterKey: SecretKeySpec by lazy { SecretKeySpec(loadOrCreateMasterKey(), "AES") }

    actual fun putString(key: String, value: String) {
        prefs.put(key, encrypt(value.toByteArray(Charsets.UTF_8)))
    }

    actual fun getString(key: String, defaultValue: String): String {
        val stored = prefs.get(key, null) ?: return defaultValue
        return decodeString(stored, defaultValue)
    }

    actual fun putBoolean(key: String, value: Boolean) {
        putString(key, value.toString())
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val stored = prefs.get(key, null)
        if (stored != null) {
            return decodeString(stored, defaultValue.toString()).toBooleanStrictOrNull() ?: defaultValue
        }
        return prefs.getBoolean(key, defaultValue)
    }

    actual fun putInt(key: String, value: Int) {
        putString(key, value.toString())
    }

    actual fun getInt(key: String, defaultValue: Int): Int {
        val stored = prefs.get(key, null)
        if (stored != null) {
            return decodeString(stored, defaultValue.toString()).toIntOrNull() ?: defaultValue
        }
        return prefs.getInt(key, defaultValue)
    }

    actual fun putLong(key: String, value: Long) {
        putString(key, value.toString())
    }

    actual fun getLong(key: String, defaultValue: Long): Long {
        val stored = prefs.get(key, null)
        if (stored != null) {
            return decodeString(stored, defaultValue.toString()).toLongOrNull() ?: defaultValue
        }
        return prefs.getLong(key, defaultValue)
    }

    actual fun remove(key: String) {
        prefs.remove(key)
    }

    actual fun clear() {
        prefs.clear()
    }

    private fun decodeString(stored: String, defaultValue: String): String {
        if (!looksEncrypted(stored)) {
            return stored
        }
        val decrypted = decrypt(stored) ?: return defaultValue
        return decrypted.toString(Charsets.UTF_8)
    }

    private fun looksEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

    private fun encrypt(plaintext: ByteArray): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        val payload = iv + ciphertext
        return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    private fun decrypt(encoded: String): ByteArray? {
        if (!looksEncrypted(encoded)) return null
        return try {
            val payload = Base64.getDecoder().decode(encoded.removePrefix(ENCRYPTED_PREFIX))
            if (payload.size <= GCM_IV_LENGTH) return null
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadOrCreateMasterKey(): ByteArray {
        val keyFile = File(System.getProperty("user.home"), ".eazycmp/secure.key")
        if (keyFile.exists() && keyFile.length() == MASTER_KEY_LENGTH.toLong()) {
            return keyFile.readBytes()
        }
        keyFile.parentFile?.mkdirs()
        val key = ByteArray(MASTER_KEY_LENGTH).also { SecureRandom().nextBytes(it) }
        keyFile.writeBytes(key)
        restrictKeyFilePermissions(keyFile)
        return key
    }

    private fun restrictKeyFilePermissions(keyFile: File) {
        try {
            val path = keyFile.toPath()
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: Exception) {
            // Best effort only (e.g. non-POSIX filesystems).
        }
    }

    private companion object {
        private const val ENCRYPTED_PREFIX = "gcm:"
        private const val MASTER_KEY_LENGTH = 32
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
