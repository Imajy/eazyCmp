package com.aj.shared.plugin

import com.aj.shared.analytics.AnalyticsEvent
import com.aj.shared.analytics.EazyAnalytics
import com.aj.shared.storage.SecureStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginSpiTest {

    @Test
    fun hostRequiredDefaults_exposeHints() {
        assertEquals(ServiceCapability.HOST_REQUIRED, HostRequiredQrScannerService.capability)
        assertTrue(HostRequiredQrScannerService.hostHint.contains("CameraX") ||
            HostRequiredQrScannerService.hostHint.contains("ZXing"))
        assertEquals(ServiceCapability.HOST_REQUIRED, HostRequiredPushTokenService.capability)
        assertEquals(ServiceCapability.HOST_REQUIRED, HostRequiredOtpAutoReadService.capability)
    }

    @Test
    fun registry_acceptsCustomServices() {
        val registry = EazyRegistry()
        var events = 0
        registry.analytics = object : EazyAnalytics {
            override fun logEvent(event: AnalyticsEvent) {
                events++
            }

            override fun setUserId(userId: String?) = Unit
            override fun setUserProperty(key: String, value: String?) = Unit
        }
        registry.register("payments", "stripe")
        registry.analytics.logEvent(AnalyticsEvent("pay"))
        assertEquals(1, events)
        assertEquals("stripe", registry.get<String>("payments"))
        assertTrue(EazyServiceCatalog.snapshot(registry).any { it.name == "extra:payments" })
    }

    @Test
    fun defaultSecureStorageService_roundTrips() {
        val storage = SecureStorage()
        val service = DefaultSecureStorageService(storage)
        service.putString("k", "v")
        assertEquals("v", service.getString("k"))
        service.putInt("n", 7)
        assertEquals(7, service.getInt("n"))
        service.remove("k")
        assertEquals("", service.getString("k"))
        assertEquals(ServiceCapability.FULL, service.capability)
    }
}
