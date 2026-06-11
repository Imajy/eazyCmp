package com.aj.shared.auth

actual class GoogleAuth actual constructor() {
    actual val isAvailable: Boolean = false

    actual suspend fun signIn(): SocialAuthResult? = null

    actual suspend fun signOut() = Unit
}
