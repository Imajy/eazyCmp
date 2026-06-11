package com.aj.shared.upload

import com.aj.shared.picker.PickedFile

data class CompressionResult(
    val file: PickedFile,
    val originalSizeBytes: Int,
    val compressedSizeBytes: Int,
    val wasCompressed: Boolean,
    val qualityUsed: Int? = null,
) {
    val savedBytes: Int
        get() = (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0)

    val compressionRatio: Double
        get() = if (originalSizeBytes == 0) 1.0 else compressedSizeBytes.toDouble() / originalSizeBytes
}
