package com.aj.shared.security

import kotlin.random.Random

/**
 * Multiplatform Cryptographic & Hashing toolkit for EazyCmp.
 * Provides pure Kotlin SHA-256, MD5, SHA-1 hashing, Base64/Hex encoding, and token generation.
 */
object EazyCrypto {

    /**
     * Computes the SHA-256 hash of a string and returns a lowercase hex string.
     */
    fun sha256(input: String): String {
        return toHexString(sha256Bytes(input.encodeToByteArray()))
    }

    /**
     * Computes the SHA-256 hash of a byte array.
     */
    fun sha256Bytes(input: ByteArray): ByteArray {
        val h = intArrayOf(
            0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372.toInt(), 0xa54ff53a.toInt(),
            0x510e527f.toInt(), 0x9b05688c.toInt(), 0x1f83d9ab.toInt(), 0x5be0cd19.toInt()
        )
        val k = intArrayOf(
            0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
            0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
            0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
            0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
            0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
            0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
            0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
            0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
        )

        val bitLength = input.size.toLong() * 8L
        val padLen = ((56 - (input.size + 1) % 64) + 64) % 64
        val padded = ByteArray(input.size + 1 + padLen + 8)
        input.copyInto(padded, 0)
        padded[input.size] = 0x80.toByte()

        for (i in 0 until 8) {
            padded[padded.size - 1 - i] = ((bitLength ushr (i * 8)) and 0xFF).toByte()
        }

        val w = IntArray(64)
        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                w[i] = ((padded[offset + i * 4].toInt() and 0xFF) shl 24) or
                        ((padded[offset + i * 4 + 1].toInt() and 0xFF) shl 16) or
                        ((padded[offset + i * 4 + 2].toInt() and 0xFF) shl 8) or
                        (padded[offset + i * 4 + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = (w[i - 15] ushr 7 or (w[i - 15] shl 25)) xor
                        (w[i - 15] ushr 18 or (w[i - 15] shl 14)) xor
                        (w[i - 15] ushr 3)
                val s1 = (w[i - 2] ushr 17 or (w[i - 2] shl 15)) xor
                        (w[i - 2] ushr 19 or (w[i - 2] shl 13)) xor
                        (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var h0 = h[7]

            for (i in 0 until 64) {
                val s1 = (e ushr 6 or (e shl 26)) xor (e ushr 11 or (e shl 21)) xor (e ushr 25 or (e shl 7))
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h0 + s1 + ch + k[i] + w[i]
                val s0 = (a ushr 2 or (a shl 30)) xor (a ushr 13 or (a shl 19)) xor (a ushr 22 or (a shl 10))
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                h0 = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += h0
            offset += 64
        }

        val result = ByteArray(32)
        for (i in 0 until 8) {
            result[i * 4] = ((h[i] ushr 24) and 0xFF).toByte()
            result[i * 4 + 1] = ((h[i] ushr 16) and 0xFF).toByte()
            result[i * 4 + 2] = ((h[i] ushr 8) and 0xFF).toByte()
            result[i * 4 + 3] = (h[i] and 0xFF).toByte()
        }
        return result
    }

    /**
     * Converts byte array to lowercase hexadecimal string.
     */
    fun toHexString(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = hexChars[v ushr 4]
            chars[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return chars.concatToString()
    }

    /**
     * Parses a hexadecimal string into a byte array.
     */
    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").lowercase()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val h = clean[i].digitToInt(16)
            val l = clean[i + 1].digitToInt(16)
            data[i / 2] = ((h shl 4) + l).toByte()
            i += 2
        }
        return data
    }

    /**
     * Generates a random alphanumeric token or session ID of specified length.
     */
    fun randomToken(length: Int = 32): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Generates a pseudo-random UUID v4 string.
     */
    fun randomUuid(): String {
        val bytes = Random.nextBytes(16)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte() // version 4
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // variant IETF
        val hex = toHexString(bytes)
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
