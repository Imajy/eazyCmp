package com.aj.api

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient


/**
 * Platform Providers
 *
 * This file defines platform-specific dependencies required by the API library.
 *
 * Since this is a Kotlin Multiplatform (KMP) library, certain implementations
 * must differ per platform:
 *
 * Android → OkHttp engine + SharedPreferences
 * iOS → Darwin engine + NSUserDefaults
 * Desktop/JVM → CIO engine + Preferences API
 *
 *
 * expect / actual mechanism
 * -------------------------
 *
 * Kotlin Multiplatform allows us to declare expected functionality in common code
 * and provide platform-specific implementations in each platform source set.
 *
 *
 * commonMain
 * expect fun provideHttpClient(): HttpClient
 * expect fun provideSettings(): Settings
 *
 *
 * androidMain
 * actual fun provideHttpClient() = HttpClient(OkHttp)
 * actual fun provideSettings() = SharedPreferencesSettings(...)
 *
 *
 * iosMain
 * actual fun provideHttpClient() = HttpClient(Darwin)
 * actual fun provideSettings() = NSUserDefaultsSettings(...)
 *
 *
 * jvmMain
 * actual fun provideHttpClient() = HttpClient(CIO)
 * actual fun provideSettings() = PreferencesSettings(...)
 *
 *
 * Library users DO NOT need to implement anything.
 * Everything is internally managed by this library.
 */


/**
 * Provides platform-specific HttpClient instance.
 *
 * The actual implementation is defined in:
 *
 * androidMain → OkHttp engine
 * iosMain → Darwin engine
 * jvmMain → CIO engine
 *
 * Why expect?
 * Because each platform requires a different HTTP engine.
 */
expect fun provideHttpClient(): HttpClient



/**
 * Provides platform-specific Settings storage.
 *
 * Used for storing:
 *
 * - tokens
 * - user preferences
 * - app configuration
 * - session data
 *
 *
 * Platform mapping:
 *
 * Android → SharedPreferences
 * iOS → NSUserDefaults
 * Desktop → Preferences API
 *
 *
 * Why not use SharedPreferences directly?
 *
 * SharedPreferences works only on Android.
 * This library supports multiple platforms, so we use a
 * multiplatform abstraction.
 */
expect fun provideSettings(): Settings




/**
 * HttpClientProvider
 *
 * Singleton provider for HttpClient.
 *
 * Why singleton?
 *
 * HttpClient is expensive to create and should be reused.
 *
 * Benefits:
 *
 * - connection pooling
 * - better performance
 * - shared headers configuration
 * - shared interceptors
 * - reduced memory usage
 *
 *
 * DO NOT create HttpClient manually in user code.
 *
 * Always use:
 *
 * HttpClientProvider.client
 *
 *
 * Example:
 *
 * val apiClient = ApiClient(
 *      client = HttpClientProvider.client
 * )
 *
 *
 * Lifecycle
 * ----------
 *
 * The client is lazily initialized only once when first used.
 *
 * val client by lazy { provideHttpClient() }
 *
 *
 * Thread safe:
 * Kotlin lazy initialization is thread-safe by default.
 *
 *
 * Used internally by:
 *
 * ApiClient
 * Repository layer
 * ViewModels
 *
 */
object HttpClientProvider {


    /**
     * Shared HttpClient instance.
     *
     * This instance will be reused across all API calls.
     */
    val client: HttpClient by lazy {

        provideHttpClient()
    }
}