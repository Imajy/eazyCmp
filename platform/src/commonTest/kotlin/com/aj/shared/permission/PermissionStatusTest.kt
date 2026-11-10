package com.aj.shared.permission

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionStatusTest {
    @Test
    fun isUsable_forGrantedAndNotRequired() {
        assertTrue(PermissionStatus.GRANTED.isUsable)
        assertTrue(PermissionStatus.NOT_REQUIRED.isUsable)
        assertFalse(PermissionStatus.DENIED.isUsable)
        assertFalse(PermissionStatus.PERMANENTLY_DENIED.isUsable)
        assertFalse(PermissionStatus.UNAVAILABLE.isUsable)
    }
}
