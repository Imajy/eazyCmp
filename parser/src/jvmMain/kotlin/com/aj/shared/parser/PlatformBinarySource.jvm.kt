package com.aj.shared.parser

import java.io.RandomAccessFile
import java.nio.channels.FileChannel

internal actual object PlatformBinarySource {
    actual fun openFile(path: String): BinarySource? =
        try {
            val raf = RandomAccessFile(path, "r")
            val len = raf.length()
            if (len <= 0L || len > Int.MAX_VALUE) {
                raf.close()
                null
            } else {
                // Prefer OS mmap when available; fall back to buffered RAF.
                try {
                    val channel = raf.channel
                    val map = channel.map(FileChannel.MapMode.READ_ONLY, 0, len)
                    MappedFileSource(raf, map)
                } catch (_: Exception) {
                    RandomAccessFileSource(raf)
                }
            }
        } catch (_: Exception) {
            null
        }
}

internal class MappedFileSource(
    private val raf: RandomAccessFile,
    private val map: java.nio.MappedByteBuffer,
) : BinarySource {
    override val size: Int = map.capacity()

    override fun get(index: Int): Byte = map.get(index)

    override fun copyOfRange(start: Int, endExclusive: Int): ByteArray {
        val len = (endExclusive - start).coerceAtLeast(0)
        val out = ByteArray(len)
        if (len == 0) return out
        val dup = map.duplicate()
        dup.position(start)
        dup.get(out)
        return out
    }

    override fun close() {
        try {
            raf.close()
        } catch (_: Exception) {
        }
    }
}

internal class RandomAccessFileSource(
    private val raf: RandomAccessFile,
) : BinarySource {
    override val size: Int = raf.length().toInt()
    private var bufStart = -1
    private var bufLen = 0
    private val buf = ByteArray(64 * 1024)

    override fun get(index: Int): Byte {
        if (index < bufStart || index >= bufStart + bufLen) {
            fill(index)
        }
        return buf[index - bufStart]
    }

    override fun copyOfRange(start: Int, endExclusive: Int): ByteArray {
        val len = (endExclusive - start).coerceAtLeast(0)
        val out = ByteArray(len)
        if (len == 0) return out
        raf.seek(start.toLong())
        var off = 0
        while (off < len) {
            val n = raf.read(out, off, len - off)
            if (n < 0) break
            off += n
        }
        // Invalidate buffer after large range reads
        bufStart = -1
        bufLen = 0
        return out
    }

    private fun fill(index: Int) {
        val start = index.coerceAtLeast(0)
        raf.seek(start.toLong())
        val n = raf.read(buf, 0, minOf(buf.size, size - start))
        bufStart = start
        bufLen = n.coerceAtLeast(0)
        if (bufLen == 0) {
            buf[0] = 0
            bufLen = 1
        }
    }

    override fun close() {
        try {
            raf.close()
        } catch (_: Exception) {
        }
    }
}
