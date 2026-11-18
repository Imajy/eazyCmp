package com.aj.shared.parser

/**
 * Random-access bytes for ZIP without requiring the whole file as a [ByteArray].
 * Path-backed sources (JVM/Android RAF / iOS mapped read) cut peak RAM on large `.xlsx`.
 */
internal interface BinarySource {
    val size: Int
    fun get(index: Int): Byte
    fun copyOfRange(start: Int, endExclusive: Int): ByteArray
    fun close() {}
}

internal class ByteArraySource(private val bytes: ByteArray) : BinarySource {
    override val size: Int get() = bytes.size
    override fun get(index: Int): Byte = bytes[index]
    override fun copyOfRange(start: Int, endExclusive: Int): ByteArray =
        bytes.copyOfRange(start, endExclusive)
}

internal fun BinarySource.readLeShort(index: Int): Int {
    val b0 = get(index).toInt() and 0xff
    val b1 = get(index + 1).toInt() and 0xff
    return b0 or (b1 shl 8)
}

internal fun BinarySource.readLeInt(index: Int): Int {
    val b0 = get(index).toInt() and 0xff
    val b1 = get(index + 1).toInt() and 0xff
    val b2 = get(index + 2).toInt() and 0xff
    val b3 = get(index + 3).toInt() and 0xff
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

internal fun BinarySource.readLeLong(index: Int): Long {
    var result = 0L
    for (k in 0 until 8) {
        result = result or ((get(index + k).toLong() and 0xff) shl (8 * k))
    }
    return result
}

/** Platform file → [BinarySource] (RAF / mapped); null = fall back to full read. */
internal expect object PlatformBinarySource {
    fun openFile(path: String): BinarySource?
}
