package com.aj.shared.parser

/**
 * Raw DEFLATE inflate (ZIP method 8) — platform zlib / JDK only, no third-party libs.
 */
internal expect object DeflateInflater {
    fun inflate(compressed: ByteArray, uncompressedSizeHint: Int = 0): ByteArray
}
