package com.aj.shared.plugin

import com.aj.shared.EazyCmp
import com.aj.shared.analytics.EazyAnalytics
import com.aj.shared.analytics.AnalyticsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EazyPluginSpiTest {

    @Test
    fun install_overridesAnalyticsAndExtras() {
        var logged = 0
        val analytics = object : EazyAnalytics {
            override fun logEvent(event: AnalyticsEvent) {
                logged++
            }

            override fun setUserId(userId: String?) = Unit
            override fun setUserProperty(key: String, value: String?) = Unit
        }

        EazyCmp.install {
            this.analytics = analytics
            register("demo", "ok")
        }

        EazyCmp.analytics.logEvent(AnalyticsEvent(name = "test"))
        assertEquals(1, logged)
        assertEquals("ok", EazyCmp.getService<String>("demo"))

        val catalog = EazyCmp.serviceCatalog()
        assertTrue(catalog.any { it.name == "analytics" && it.capability == ServiceCapability.FULL })
        assertTrue(catalog.any { it.name == "extra:demo" })
    }
}
