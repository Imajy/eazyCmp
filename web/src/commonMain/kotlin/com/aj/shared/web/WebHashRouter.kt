package com.aj.shared.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Controller for hash-based browser routing in Compose Web / Wasm apps.
 */
class WebHashRouter(
    initialRoute: String = ""
) {
    var currentRoute by mutableStateOf(
        if (initialRoute.isNotBlank()) initialRoute else normalizeRoute(WebBrowser.getHash())
    )
        private set

    fun navigate(route: String) {
        val normalized = normalizeRoute(route)
        currentRoute = normalized
        WebBrowser.setHash(normalized)
    }

    fun syncFromBrowser() {
        val hash = normalizeRoute(WebBrowser.getHash())
        if (hash != currentRoute) {
            currentRoute = hash
        }
    }

    private fun normalizeRoute(route: String): String {
        return route.removePrefix("#").removePrefix("/").trim()
    }
}

/**
 * Remembers a WebHashRouter and keeps it in sync with the browser window hash.
 */
@Composable
fun rememberWebHashRouter(initialRoute: String = "home"): WebHashRouter {
    val router = remember { WebHashRouter(initialRoute) }

    LaunchedEffect(Unit) {
        router.syncFromBrowser()
    }

    return router
}
