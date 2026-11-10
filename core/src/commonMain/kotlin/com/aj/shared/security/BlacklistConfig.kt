package com.aj.shared.security

import kotlinx.serialization.Serializable

@Serializable
data class BlacklistConfig(
    val version: Int = 1,
    val blockedPackages: List<String> = emptyList()
)
