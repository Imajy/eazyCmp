package com.aj.shared.api

import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine

expect fun createSettings(): Settings

expect fun getPlatformEngine(): HttpClientEngine
