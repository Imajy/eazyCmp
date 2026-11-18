package com.aj.shared.parser

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

internal actual object DeflateInflater {
    actual fun inflate(compressed: ByteArray, uncompressedSizeHint: Int): ByteArray {
        val inflater = Inflater(true) // raw DEFLATE (ZIP)
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream(
                if (uncompressedSizeHint > 0) uncompressedSizeHint else compressed.size * 2,
            )
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput()) break
                    if (inflater.needsDictionary()) {
                        throw IllegalArgumentException("DEFLATE needs dictionary (unsupported)")
                    }
                } else {
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
