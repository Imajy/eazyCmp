package com.aj.shared.auth

expect class GoogleAuth() {
    val isAvailable: Boolean
    suspend fun signIn(): SocialAuthResult?
    suspend fun signOut()
}

fun GoogleAuth.asProvider(): SocialAuthProvider = object : SocialAuthProvider {
    override val type: SocialAuthProviderType = SocialAuthProviderType.GOOGLE
    override val isAvailable: Boolean get() = this@asProvider.isAvailable
    override suspend fun signIn(): SocialAuthResult? = this@asProvider.signIn()
    override suspend fun signOut() = this@asProvider.signOut()
}
