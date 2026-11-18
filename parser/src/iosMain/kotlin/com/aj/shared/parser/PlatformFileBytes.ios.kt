package com.aj.shared.parser

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy
import platform.posix.remove

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual object PlatformFileBytes {
    actual fun readOrNull(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val len = data.length.toInt()
        if (len <= 0) return ByteArray(0)
        val out = ByteArray(len)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return out
    }

    actual fun lengthOrNull(path: String): Long? {
        return try {
            val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
                ?: return null
            (attrs[NSFileSize] as? NSNumber)?.longValue
        } catch (_: Throwable) {
            null
        }
    }

    actual fun writeTemp(bytes: ByteArray, suffix: String): String? {
        return try {
            val dir = NSTemporaryDirectory()
            val name = "eazycmp-${NSUUID().UUIDString}${suffix.ifBlank { ".bin" }}"
            val path = dir.trimEnd('/') + "/" + name
            val data = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            val ok = data.writeToFile(path, atomically = true)
            if (ok) path else null
        } catch (_: Throwable) {
            null
        }
    }

    actual fun deleteQuietly(path: String) {
        try {
            remove(path)
        } catch (_: Throwable) {
        }
    }
}
