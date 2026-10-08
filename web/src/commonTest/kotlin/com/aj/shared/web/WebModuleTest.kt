package com.aj.shared.web

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebModuleTest {

    @Test
    fun testWebBreakpoints() {
        assertEquals(WebBreakpoint.MOBILE, getWebBreakpoint(375.dp))
        assertEquals(WebBreakpoint.TABLET, getWebBreakpoint(768.dp))
        assertEquals(WebBreakpoint.DESKTOP, getWebBreakpoint(1200.dp))
        assertEquals(WebBreakpoint.ULTRAWIDE, getWebBreakpoint(1920.dp))
    }

    @Test
    fun testWebRouter() {
        val router = WebHashRouter(initialRoute = "portfolio")
        assertEquals("portfolio", router.currentRoute)

        router.navigate("#contact")
        assertEquals("contact", router.currentRoute)

        router.navigate("/projects")
        assertEquals("projects", router.currentRoute)
    }

    @Test
    fun testWebStorageJvmFallback() {
        val storage = WebLocalStorage()
        storage.setItem("theme", "dark")
        assertEquals("dark", storage.getItem("theme"))

        storage.removeItem("theme")
        assertEquals(null, storage.getItem("theme"))
    }
}
