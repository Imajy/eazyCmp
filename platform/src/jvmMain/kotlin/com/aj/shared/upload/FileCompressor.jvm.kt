package com.aj.shared.upload

import com.aj.shared.picker.PickedFile

actual class FileCompressor actual constructor() {

    actual suspend fun compress(
        file: PickedFile,
        config: CompressionConfig,
    ): CompressionResult {
        val size = file.bytes.size
        return CompressionResult(
            file = file,
            originalSizeBytes = size,
            compressedSizeBytes = size,
            wasCompressed = false,
        )
    }
}
