package com.aj.shared.plugin.adapters

import com.aj.shared.plugin.PushTokenService
import com.aj.shared.plugin.ServiceCapability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Firebase Cloud Messaging push token adapter.
 *
 * Uses reflection so the library does **not** hard-require `firebase-messaging`.
 * Host apps that add Firebase Messaging get a working implementation automatically.
 *
 * ```kotlin
 * EazyCmp.install { pushToken = FcmPushTokenService() }
 * ```
 */
class FcmPushTokenService : PushTokenService {

    override val capability: ServiceCapability =
        if (isFirebaseMessagingPresent()) ServiceCapability.FULL else ServiceCapability.HOST_REQUIRED

    override val hostHint: String?
        get() = if (isFirebaseMessagingPresent()) null
        else "Add com.google.firebase:firebase-messaging to the host app, then use FcmPushTokenService()."

    override suspend fun getToken(): String? {
        if (!isFirebaseMessagingPresent()) return null
        return suspendCancellableCoroutine { cont ->
            try {
                val messagingClass = Class.forName("com.google.firebase.messaging.FirebaseMessaging")
                val instance = messagingClass.getMethod("getInstance").invoke(null)
                val task = messagingClass.getMethod("getToken").invoke(instance)
                val taskClass = Class.forName("com.google.android.gms.tasks.Task")
                val addSuccess = taskClass.getMethod(
                    "addOnSuccessListener",
                    Class.forName("com.google.android.gms.tasks.OnSuccessListener"),
                )
                val addFailure = taskClass.getMethod(
                    "addOnFailureListener",
                    Class.forName("com.google.android.gms.tasks.OnFailureListener"),
                )
                val successListener = java.lang.reflect.Proxy.newProxyInstance(
                    taskClass.classLoader,
                    arrayOf(Class.forName("com.google.android.gms.tasks.OnSuccessListener")),
                ) { _, _, args ->
                    if (cont.isActive) cont.resume(args?.firstOrNull() as? String)
                    null
                }
                val failureListener = java.lang.reflect.Proxy.newProxyInstance(
                    taskClass.classLoader,
                    arrayOf(Class.forName("com.google.android.gms.tasks.OnFailureListener")),
                ) { _, _, _ ->
                    if (cont.isActive) cont.resume(null)
                    null
                }
                addSuccess.invoke(task, successListener)
                addFailure.invoke(task, failureListener)
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    override fun onTokenRefresh(callback: (String) -> Unit) {
        // Host should forward FirebaseMessagingService.onNewToken into this callback if needed.
        // Reflection cannot safely subclass FirebaseMessagingService here.
    }

    companion object {
        fun isFirebaseMessagingPresent(): Boolean =
            try {
                Class.forName("com.google.firebase.messaging.FirebaseMessaging")
                true
            } catch (_: Throwable) {
                false
            }
    }
}

/**
 * Simple host-provided push token bridge when FCM is wrapped elsewhere.
 */
class HostPushTokenService(
    private val tokenProvider: suspend () -> String?,
    private val refreshBinder: ((String) -> Unit) -> Unit = {},
) : PushTokenService {
    override val capability: ServiceCapability = ServiceCapability.FULL
    override val hostHint: String? = null
    override suspend fun getToken(): String? = tokenProvider()
    override fun onTokenRefresh(callback: (String) -> Unit) = refreshBinder(callback)
}
