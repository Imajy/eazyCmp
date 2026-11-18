package com.aj.shared.parser

/**
 * Tiny XML helpers for OOXML — depth-aware matching for nested same tags.
 */
internal object SimpleXml {

    fun decodeEntities(value: String): String =
        value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    fun attr(tag: String, name: String): String? {
        val local = name.substringAfterLast(':')
        val patterns = listOf(
            Regex("""\b${Regex.escape(name)}\s*=\s*"([^"]*)""""),
            Regex("""\b${Regex.escape(name)}\s*=\s*'([^']*)'"""),
            Regex("""\b(?:[\w.]+:)?${Regex.escape(local)}\s*=\s*"([^"]*)""""),
            Regex("""\b(?:[\w.]+:)?${Regex.escape(local)}\s*=\s*'([^']*)'"""),
        )
        for (pattern in patterns) {
            pattern.find(tag)?.groupValues?.getOrNull(1)?.let { return decodeEntities(it) }
        }
        return null
    }

    fun forEachElement(xml: String, tagName: String, block: (openTag: String, inner: String) -> Unit) {
        val openPattern = Regex("""<(?:[\w.]+:)?${Regex.escape(tagName)}(?=[\s/>])""")
        val closePattern = Regex("""</(?:[\w.]+:)?${Regex.escape(tagName)}>""")
        var searchFrom = 0
        while (searchFrom < xml.length) {
            val match = openPattern.find(xml, searchFrom) ?: break
            val start = match.range.first
            val tagEnd = xml.indexOf('>', start)
            if (tagEnd < 0) break
            val openTag = xml.substring(start, tagEnd + 1)
            if (openTag.endsWith("/>")) {
                block(openTag, "")
                searchFrom = tagEnd + 1
                continue
            }
            // Depth-aware: skip nested same-named tags
            var depth = 1
            var cursor = tagEnd + 1
            var closeAt = -1
            while (cursor < xml.length && depth > 0) {
                val nextOpen = openPattern.find(xml, cursor)
                val nextClose = closePattern.find(xml, cursor) ?: break
                val openIdx = nextOpen?.range?.first ?: Int.MAX_VALUE
                val closeIdx = nextClose.range.first
                if (openIdx < closeIdx) {
                    // nested open — only count if not self-closing
                    val nestedEnd = xml.indexOf('>', openIdx)
                    if (nestedEnd < 0) break
                    val nestedTag = xml.substring(openIdx, nestedEnd + 1)
                    if (!nestedTag.endsWith("/>")) depth++
                    cursor = nestedEnd + 1
                } else {
                    depth--
                    if (depth == 0) {
                        closeAt = closeIdx
                        cursor = nextClose.range.last + 1
                    } else {
                        cursor = nextClose.range.last + 1
                    }
                }
            }
            if (closeAt < 0) {
                searchFrom = tagEnd + 1
                continue
            }
            val inner = xml.substring(tagEnd + 1, closeAt)
            block(openTag, inner)
            searchFrom = cursor
        }
    }

    fun firstText(xml: String, tagName: String): String? {
        var found: String? = null
        forEachElement(xml, tagName) { _, inner ->
            if (found == null) {
                found = decodeEntities(inner.replace(Regex("<[^>]+>"), ""))
            }
        }
        return found
    }

    fun allTexts(xml: String, tagName: String): List<String> {
        val out = mutableListOf<String>()
        forEachElement(xml, tagName) { _, inner ->
            out += decodeEntities(inner.replace(Regex("<[^>]+>"), ""))
        }
        return out
    }
}
