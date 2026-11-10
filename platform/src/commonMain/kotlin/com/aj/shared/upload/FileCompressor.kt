package com.aj.shared.upload

import com.aj.shared.picker.PickedFile

expect class FileCompressor() {
    suspend fun compress(file: PickedFile, config: CompressionConfig = CompressionConfig()): CompressionResult
}
