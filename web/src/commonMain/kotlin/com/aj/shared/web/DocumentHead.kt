package com.aj.shared.web

/**
 * Manages browser document title, meta tags, and favicon.
 */
expect object DocumentHead {
    fun setTitle(title: String)
    fun getTitle(): String
    fun setMetaTag(name: String, content: String)
    fun setFavicon(iconUrl: String)
}
