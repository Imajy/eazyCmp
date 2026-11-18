package com.aj.shared.parser

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileHandle
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import platform.posix.memcpy

/**
 * iOS random-access file source via [NSFileHandle] — avoids loading the whole `.xlsx` into RAM.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual object PlatformBinarySource {
    actual fun openFile(path: String): BinarySource? {
        val sizeLong = PlatformFileBytes.lengthOrNull(path) ?: return null
        if (sizeLong <= 0L || sizeLong > Int.MAX_VALUE) return null
        val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return null
        return NsFileHandleSource(handle, sizeLong.toInt())
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class NsFileHandleSource(
    private val handle: NSFileHandle,
    override val size: Int,
) : BinarySource {
    private var bufStart = -1
    private var bufLen = 0
    private val buf = ByteArray(64 * 1024)

    override fun get(index: Int): Byte {
        if (index < bufStart || index >= bufStart + bufLen) fill(index)
        return buf[index - bufStart]
    }

    override fun copyOfRange(start: Int, endExclusive: Int): ByteArray {
        val len = (endExclusive - start).coerceAtLeast(0)
        val out = ByteArray(len)
        if (len == 0) return out
        handle.seekToFileOffset(start.toULong())
        val data = handle.readDataOfLength(len.toULong())
        val n = data.length.toInt().coerceAtMost(len)
        if (n > 0) {
            out.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, n.toULong())
            }
        }
        bufStart = -1
        bufLen = 0
        return out
    }

    private fun fill(index: Int) {
        val start = index.coerceAtLeast(0)
        val want = minOf(buf.size, size - start).coerceAtLeast(0)
        handle.seekToFileOffset(start.toULong())
        val data = handle.readDataOfLength(want.toULong())
        val n = data.length.toInt().coerceAtMost(buf.size)
        if (n > 0) {
            buf.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, n.toULong())
            }
        }
        bufStart = start
        bufLen = n.coerceAtLeast(0)
        if (bufLen == 0) {
            buf[0] = 0
            bufLen = 1
        }
    }

    override fun close() {
        try {
            handle.closeFile()
        } catch (_: Throwable) {
        }
    }
}
