package com.aj.shared.plugin

import com.aj.shared.analytics.NoOpEazyAnalytics
import com.aj.shared.analytics.NoOpEazyCrash

data class ServiceStatus(
    val name: String,
    val capability: ServiceCapability,
    val hostHint: String?,
)

object EazyServiceCatalog {
    fun snapshot(registry: EazyRegistry): List<ServiceStatus> = buildList {
        add(
            ServiceStatus(
                name = "analytics",
                capability = if (registry.analytics is NoOpEazyAnalytics) {
                    ServiceCapability.PARTIAL
                } else {
                    ServiceCapability.FULL
                },
                hostHint = if (registry.analytics is NoOpEazyAnalytics) {
                    "Replace NoOpEazyAnalytics with your analytics backend in EazyCmp.install."
                } else {
                    null
                },
            ),
        )
        add(
            ServiceStatus(
                name = "crashReporter",
                capability = if (registry.crashReporter is NoOpEazyCrash) {
                    ServiceCapability.PARTIAL
                } else {
                    ServiceCapability.FULL
                },
                hostHint = if (registry.crashReporter is NoOpEazyCrash) {
                    "Replace NoOpEazyCrash with Crashlytics/Sentry in EazyCmp.install."
                } else {
                    null
                },
            ),
        )
        add(
            ServiceStatus(
                name = "qrScanner",
                capability = registry.qrScanner.capability,
                hostHint = registry.qrScanner.hostHint,
            ),
        )
        add(
            ServiceStatus(
                name = "pushToken",
                capability = registry.pushToken.capability,
                hostHint = registry.pushToken.hostHint,
            ),
        )
        add(
            ServiceStatus(
                name = "otpAutoRead",
                capability = registry.otpAutoRead.capability,
                hostHint = registry.otpAutoRead.hostHint,
            ),
        )
        add(
            ServiceStatus(
                name = "secureStorage",
                capability = registry.secureStorage?.capability ?: ServiceCapability.FULL,
                hostHint = if (registry.secureStorage == null) {
                    "Using platform SecureStorage via EazyCmp.storage."
                } else {
                    null
                },
            ),
        )
        registry.keys().sorted().forEach { key ->
            add(
                ServiceStatus(
                    name = "extra:$key",
                    capability = ServiceCapability.NOT_APPLICABLE,
                    hostHint = null,
                ),
            )
        }
    }
}
