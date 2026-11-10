package com.aj.shared.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UserDataExport(
    val exportedAtEpochMs: Long,
    val preferences: Map<String, String> = emptyMap(),
    val consentRecords: List<String> = emptyList(),
)

fun exportUserData(
    preferenceKeys: List<String> = emptyList(),
    json: Json = Json { prettyPrint = true },
): String {
    val prefs = preferenceKeys.associateWith { key ->
        com.aj.shared.internal.ServiceLocator.preferencesOrDefault().getString(key)
    }
    val export = UserDataExport(
        exportedAtEpochMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        preferences = prefs,
    )
    return json.encodeToString(export)
}
