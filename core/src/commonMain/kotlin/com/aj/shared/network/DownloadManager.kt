package com.aj.shared.network

import com.aj.shared.api.HttpClientProvider
import com.aj.shared.storage.CacheFileIO
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val percent: Float?,
)

class DownloadManager(
    private val client: io.ktor.client.HttpClient = HttpClientProvider.client,
) {
    fun download(
        url: String,
        destinationPath: String,
        resumeFromByte: Long = 0L,
    ): Flow<DownloadProgress> = flow {
        var downloaded = resumeFromByte
        val chunks = ArrayList<ByteArray>()
        var totalSize = 0
        client.prepareGet(url) {
            if (resumeFromByte > 0) {
                headers.append("Range", "bytes=$resumeFromByte-")
            }
        }.execute { response ->
            val total = response.headers["Content-Length"]?.toLongOrNull()?.let { it + resumeFromByte }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(8192)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read <= 0) break
                val piece = buffer.copyOf(read)
                chunks += piece
                totalSize += read
                downloaded += read
                val percent = total?.let { downloaded.toFloat() / it }
                emit(DownloadProgress(downloaded, total, percent))
            }
            val allBytes = ByteArray(totalSize)
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(allBytes, offset)
                offset += chunk.size
            }
            CacheFileIO.writeBytes(destinationPath, allBytes)
        }
    }
}
