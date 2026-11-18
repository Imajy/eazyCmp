package com.aj.shared.parser

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DATA_ERROR
import platform.zlib.Z_MEM_ERROR
import platform.zlib.Z_NEED_DICT
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

/**
 * Apple system zlib — not a third-party Kotlin library.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual object DeflateInflater {
    actual fun inflate(compressed: ByteArray, uncompressedSizeHint: Int): ByteArray {
        if (compressed.isEmpty()) return ByteArray(0)
        memScoped {
            val stream = alloc<z_stream>()
            // windowBits = -15 → raw DEFLATE (ZIP), no zlib/gzip wrapper
            val init = inflateInit2(stream.ptr, -15)
            if (init != Z_OK) {
                throw IllegalArgumentException("zlib inflateInit2 failed: $init")
            }
            try {
                val chunks = ArrayList<ByteArray>()
                var total = 0
                val outCapacity =
                    if (uncompressedSizeHint > 0) uncompressedSizeHint.coerceAtLeast(4096) else 16_384
                val outBuf = allocArray<UByteVar>(outCapacity)

                compressed.usePinned { pinnedIn ->
                    stream.next_in = pinnedIn.addressOf(0).reinterpret()
                    stream.avail_in = compressed.size.convert()

                    while (true) {
                        stream.next_out = outBuf
                        stream.avail_out = outCapacity.convert()
                        val rc = inflate(stream.ptr, Z_NO_FLUSH)
                        val produced = outCapacity - stream.avail_out.toInt()
                        if (produced > 0) {
                            val chunk = ByteArray(produced)
                            chunk.usePinned { pinnedOut ->
                                memcpy(pinnedOut.addressOf(0), outBuf, produced.convert())
                            }
                            chunks += chunk
                            total += produced
                        }
                        when (rc) {
                            Z_STREAM_END -> break
                            Z_OK, Z_BUF_ERROR -> {
                                if (stream.avail_in.toInt() == 0 && produced == 0) break
                            }
                            Z_NEED_DICT -> throw IllegalArgumentException("DEFLATE needs dictionary")
                            Z_DATA_ERROR -> throw IllegalArgumentException("Corrupt DEFLATE data")
                            Z_MEM_ERROR -> throw OutOfMemoryError("zlib inflate OOM")
                            else -> throw IllegalArgumentException("zlib inflate failed: $rc")
                        }
                    }
                }

                val result = ByteArray(total)
                var pos = 0
                for (chunk in chunks) {
                    chunk.copyInto(result, pos)
                    pos += chunk.size
                }
                return result
            } finally {
                inflateEnd(stream.ptr)
            }
        }
    }
}
