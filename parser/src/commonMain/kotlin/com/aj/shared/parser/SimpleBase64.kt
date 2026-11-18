package com.aj.shared.parser

/** Minimal Base64 decoder (RFC 4648) — no third-party libs. */
internal object SimpleBase64 {
    private val DECODE = IntArray(128) { -1 }.also { table ->
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        alphabet.forEachIndexed { i, c -> table[c.code] = i }
    }

    fun decode(input: String): ByteArray {
        val clean = input.filter { it.code < 128 && (DECODE[it.code] >= 0 || it == '=') }
        if (clean.isEmpty()) return ByteArray(0)
        val out = ArrayList<Byte>(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (ch in clean) {
            if (ch == '=') break
            val v = DECODE[ch.code]
            if (v < 0) continue
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out += ((buffer shr bits) and 0xff).toByte()
            }
        }
        return out.toByteArray()
    }
}
