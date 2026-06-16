package com.aj.shared.version

import com.aj.shared.EazyCmp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EazyCmpRelease(
    val version: String,
    val message: String = "",
    val publishedAt: String = "",
    val commit: String = "",
)

@Serializable
data class EazyCmpVersionManifest(
    val latest: String,
    val versions: List<String> = emptyList(),
    val releases: List<EazyCmpRelease> = emptyList(),
    val updatedAt: String = "",
)

object EazyCmpVersionCatalog {
    /** Hosted Maven repo (GitHub Pages) — JitPack-style version picker. */
    const val MAVEN_REPOSITORY_URL = "https://imajy.github.io/eazyCmp/"

    const val GROUP_ID = "com.github.Imajy.eazyCmp"
    const val ARTIFACT_ID = "shared"

    const val DEFAULT_MANIFEST_URL = "${MAVEN_REPOSITORY_URL}versions.json"

    /** Gradle dependency line for any published version. */
    fun dependencyNotation(version: String): String = "$GROUP_ID:$ARTIFACT_ID:$version"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchManifest(
        url: String = DEFAULT_MANIFEST_URL,
        client: HttpClient = HttpClient(),
    ): EazyCmpVersionManifest? {
        return try {
            client.use {
                json.decodeFromString(it.get(url).bodyAsText())
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasUpdate(
        current: String = EazyCmp.VERSION,
        manifest: EazyCmpVersionManifest,
    ): Boolean = manifest.latest.isNotBlank() && manifest.latest != current
}
