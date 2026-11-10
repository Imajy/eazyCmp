package com.aj.shared.permission

import kotlin.test.Test
import kotlin.test.assertTrue

class PermissionManifestTest {
    @Test
    fun cameraMapsToAndroidPermission() {
        val perms = PermissionManifest.androidPermissionsFor(setOf(AppPermission.CAMERA))
        assertTrue(perms.isNotEmpty())
        assertTrue(perms.any { it.permission.contains("CAMERA") })
    }

    @Test
    fun galleryIsPickerOnly() {
        assertTrue(AppPermission.GALLERY.isPickerOnly)
        assertTrue(AppPermission.STORAGE.isPickerOnly)
    }
}
