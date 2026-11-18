package com.aj.shared.parser

/** Platform crypto primitives (JDK / Android JCA / Apple CommonCrypto) — not third-party libs. */
internal expect object PlatformCrypto {
    fun sha1(data: ByteArray): ByteArray
    fun sha256(data: ByteArray): ByteArray
    fun sha512(data: ByteArray): ByteArray
    fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, cipherText: ByteArray): ByteArray
}
