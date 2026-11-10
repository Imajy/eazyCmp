package com.aj.shared.internal

import com.aj.shared.analytics.EazyAnalytics
import com.aj.shared.analytics.NoOpEazyAnalytics
import com.aj.shared.network.ConnectivityObserver
import com.aj.shared.network.OfflineQueueManager
import com.aj.shared.storage.ApiCacheStorage
import com.aj.shared.storage.FormDraftManager
import com.aj.shared.storage.PreferencesStore

/**
 * Internal hooks so feature modules do not depend on the umbrella [com.aj.shared.EazyCmp] facade.
 * The shared module wires these from EazyCmp during init / first access.
 */
object ServiceLocator {
    var analytics: EazyAnalytics = NoOpEazyAnalytics
    var network: ConnectivityObserver? = null
    var apiCache: ApiCacheStorage? = null
    var offlineQueue: OfflineQueueManager? = null
    var formDrafts: FormDraftManager? = null
    var preferences: PreferencesStore? = null
    var isDebugEnabled: Boolean = false
    var libraryVersion: String = "unknown"

    fun networkOrDefault(): ConnectivityObserver =
        network ?: ConnectivityObserver().also { network = it }

    fun apiCacheOrDefault(): ApiCacheStorage =
        apiCache ?: ApiCacheStorage().also { apiCache = it }

    fun offlineQueueOrDefault(): OfflineQueueManager =
        offlineQueue ?: OfflineQueueManager().also { offlineQueue = it }

    fun formDraftsOrDefault(): FormDraftManager =
        formDrafts ?: FormDraftManager().also { formDrafts = it }

    fun preferencesOrDefault(): PreferencesStore =
        preferences ?: PreferencesStore().also { preferences = it }
}
