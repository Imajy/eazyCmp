package com.aj.shared.parser

import java.io.File

internal actual object PlatformFileBytes {
    actual fun readOrNull(path: String): ByteArray? =
        try {
            val f = File(path)
            if (!f.isFile) null else f.readBytes()
        } catch (_: Exception) {
            null
        }

    actual fun lengthOrNull(path: String): Long? =
        try {
            val f = File(path)
            if (!f.isFile) null else f.length()
        } catch (_: Exception) {
            null
        }

    actual fun writeTemp(bytes: ByteArray, suffix: String): String? =
        try {
            val f = File.createTempFile("eazycmp-", suffix.ifBlank { ".bin" })
            f.deleteOnExit()
            f.writeBytes(bytes)
            f.absolutePath
        } catch (_: Exception) {
            null
        }

    actual fun deleteQuietly(path: String) {
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }
}
