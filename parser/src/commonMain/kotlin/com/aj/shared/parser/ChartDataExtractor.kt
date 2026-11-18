package com.aj.shared.parser

/** Lightweight chart series extraction from OOXML chart parts (values only). */
internal object ChartDataExtractor {

    data class ChartSeries(
        val title: String,
        val categories: List<String>,
        val values: List<Double>,
    )

    fun extract(zip: ZipArchive): List<ChartSeries> {
        val out = ArrayList<ChartSeries>()
        val chartPaths = zip.names.filter {
            val p = it.replace('\\', '/')
            p.contains("/charts/chart", true) && p.endsWith(".xml", true)
        }.sorted()
        for (path in chartPaths.take(8)) {
            val xml = try {
                zip.readStringAndEvict(path)
            } catch (_: Throwable) {
                continue
            }
            if (xml.isBlank()) continue
            val title = SimpleXml.firstText(xml, "c:v")
                ?.takeIf { it.isNotBlank() }
                ?: path.substringAfterLast('/').removeSuffix(".xml")
            val cats = extractStrCache(xml, "c:cat") + extractNumCacheAsLabels(xml, "c:cat")
            val vals = extractNumCache(xml, "c:val").ifEmpty { extractNumCache(xml, "c:yVal") }
            if (vals.isEmpty()) continue
            val categories = if (cats.size >= vals.size) {
                cats.take(vals.size)
            } else {
                List(vals.size) { i -> cats.getOrElse(i) { "${i + 1}" } }
            }
            out += ChartSeries(title = title.take(48), categories = categories, values = vals)
        }
        return out
    }

    private fun extractNumCache(xml: String, parentHint: String): List<Double> {
        // Prefer numCache under parent; fallback to all c:v that look numeric near numCache
        val block = extractInnerApprox(xml, parentHint) ?: xml
        val nums = ArrayList<Double>()
        SimpleXml.forEachElement(block, "c:v") { _, inner ->
            val t = inner.trim()
            t.toDoubleOrNull()?.let { nums += it }
        }
        if (nums.isNotEmpty()) return nums
        SimpleXml.forEachElement(xml, "c:pt") { _, inner ->
            SimpleXml.firstText(inner, "c:v")?.toDoubleOrNull()?.let { nums += it }
        }
        return nums
    }

    private fun extractNumCacheAsLabels(xml: String, parentHint: String): List<String> =
        extractNumCache(xml, parentHint).map { formatNum(it) }

    private fun extractStrCache(xml: String, parentHint: String): List<String> {
        val block = extractInnerApprox(xml, parentHint) ?: return emptyList()
        val out = ArrayList<String>()
        SimpleXml.forEachElement(block, "c:v") { _, inner ->
            val t = inner.trim()
            if (t.isNotEmpty() && t.toDoubleOrNull() == null) out += t
        }
        SimpleXml.forEachElement(block, "c:pt") { _, inner ->
            SimpleXml.firstText(inner, "c:v")?.let { if (it.isNotBlank()) out += it }
        }
        return out
    }

    private fun extractInnerApprox(xml: String, tag: String): String? {
        val bare = tag.substringAfter(':')
        var found: String? = null
        SimpleXml.forEachElement(xml, tag) { _, inner -> if (found == null) found = inner }
        if (found != null) return found
        SimpleXml.forEachElement(xml, bare) { _, inner -> if (found == null) found = inner }
        return found
    }

    private fun formatNum(n: Double): String =
        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString().trimEnd('0').trimEnd('.')
}
