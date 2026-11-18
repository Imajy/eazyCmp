package com.aj.shared.parser

/**
 * Filesystem helpers (platform APIs only — no 3rd-party I/O libs).
 * Prefer path-based parse so huge [ByteArray]s can be GC'd after use.
 */
internal expect object PlatformFileBytes {
    fun readOrNull(path: String): ByteArray?

    /** File length in bytes, or null if missing/unreadable (avoids loading contents). */
    fun lengthOrNull(path: String): Long?

    /** Write bytes to a temp file; returns absolute path or null. */
    fun writeTemp(bytes: ByteArray, suffix: String = ".bin"): String?

    fun deleteQuietly(path: String)
}
