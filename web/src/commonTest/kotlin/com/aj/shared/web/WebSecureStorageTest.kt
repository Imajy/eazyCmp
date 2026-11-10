package com.aj.shared.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSecureStorageTest {
    @Test
    fun putGetRemoveClear_roundTrip() {
        val storage = WebSecureStorage(prefix = "test.secure.")
        storage.clear()
        storage.putString("token", "abc123")
        storage.putBoolean("flag", true)
        storage.putInt("count", 42)
        assertEquals("abc123", storage.getString("token"))
        assertEquals(true, storage.getBoolean("flag"))
        assertEquals(42, storage.getInt("count"))
        storage.remove("token")
        assertEquals("", storage.getString("token"))
        storage.clear()
        assertEquals(false, storage.getBoolean("flag", false))
    }

    @Test
    fun webEazyCmp_registerExtra() {
        WebEazyCmp.install {
            register("demo", 99)
        }
        assertEquals(99, WebEazyCmp.get<Int>("demo"))
        assertTrue(WebEazyCmp.storage.getString("missing", "x") == "x")
    }
}
