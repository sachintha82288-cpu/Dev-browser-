package io.github.sachintha82288.devbrowser.debug

import org.json.JSONObject

/** Navigation Timing phases (feature 47). */
data class NavTiming(
    val dns: Long = 0,
    val tcp: Long = 0,
    val tls: Long = 0,
    val ttfb: Long = 0,
    val domContentLoaded: Long = 0,
    val domInteractive: Long = 0,
    val load: Long = 0,
)

/** One resource's size and duration (features 48 and 53). */
data class ResourceMetric(
    val url: String,
    val type: String,
    val transferSize: Long,
    val decodedSize: Long,
    val durationMs: Long,
)

/** Core Web Vitals snapshot (feature 49). */
data class Vitals(
    val lcp: Double = 0.0,
    val inp: Double = 0.0,
    val cls: Double = 0.0,
)

data class LongTaskInfo(val durationMs: Long, val at: Long)

/** Everything the performance panel displays, from one page call. */
data class PerfSnapshot(
    val nav: NavTiming = NavTiming(),
    val paints: Map<String, Long> = emptyMap(),
    val resources: List<ResourceMetric> = emptyList(),
    val vitals: Vitals = Vitals(),
    val fps: Int = 0,
    val heapBytes: Long? = null,
    val longTasks: List<LongTaskInfo> = emptyList(),
) {
    /** Feature 53: JS + CSS payloads sorted by transfer size. */
    fun bundleAssets(): List<ResourceMetric> = resources
        .filter { it.url.contains(".js") || it.url.contains(".css") || it.type == "script" || it.type == "link" }
        .sortedByDescending { it.transferSize }

    val jsTotal: Long get() = bundleAssets().filter { it.url.contains(".js") || it.type == "script" }.sumOf { it.transferSize }
    val cssTotal: Long get() = bundleAssets().filter { it.url.contains(".css") || it.type == "link" }.sumOf { it.transferSize }
}

object PerfParser {

    fun parse(payload: JSONObject?): PerfSnapshot? {
        if (payload == null) return null
        val nav = payload.optJSONObject("nav")
        val paints = LinkedHashMap<String, Long>()
        payload.optJSONObject("paints")?.let { obj ->
            obj.keys().forEach { key -> paints[key] = obj.optLong(key) }
        }
        val resources = ArrayList<ResourceMetric>()
        payload.optJSONArray("resources")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                resources.add(
                    ResourceMetric(
                        url = item.optString("u"),
                        type = item.optString("ty"),
                        transferSize = item.optLong("tr"),
                        decodedSize = item.optLong("de"),
                        durationMs = item.optLong("d"),
                    ),
                )
            }
        }
        val vitals = payload.optJSONObject("vitals")
        val longTasks = ArrayList<LongTaskInfo>()
        payload.optJSONArray("longTasks")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                longTasks.add(LongTaskInfo(item.optLong("d"), item.optLong("t")))
            }
        }
        return PerfSnapshot(
            nav = NavTiming(
                dns = nav?.optLong("dns") ?: 0,
                tcp = nav?.optLong("tcp") ?: 0,
                tls = nav?.optLong("tls") ?: 0,
                ttfb = nav?.optLong("ttfb") ?: 0,
                domContentLoaded = nav?.optLong("dcl") ?: 0,
                domInteractive = nav?.optLong("dom") ?: 0,
                load = nav?.optLong("load") ?: 0,
            ),
            paints = paints,
            resources = resources,
            vitals = Vitals(
                lcp = vitals?.optDouble("lcp") ?: 0.0,
                inp = vitals?.optDouble("inp") ?: 0.0,
                cls = vitals?.optDouble("cls") ?: 0.0,
            ),
            fps = payload.optInt("fps"),
            heapBytes = if (payload.isNull("heap")) null else payload.optLong("heap"),
            longTasks = longTasks,
        )
    }

    /** WCAG-style rating used for the colour coding of feature 49. */
    fun rateLcp(value: Double): String = when {
        value <= 0 -> "unknown"
        value <= 2500 -> "good"
        value <= 4000 -> "mid"
        else -> "poor"
    }

    fun rateInp(value: Double): String = when {
        value <= 0 -> "unknown"
        value <= 200 -> "good"
        value <= 500 -> "mid"
        else -> "poor"
    }

    fun rateCls(value: Double): String = when {
        value <= 0.1 -> "good"
        value <= 0.25 -> "mid"
        else -> "poor"
    }

    fun formatBytes(size: Long): String = when {
        size <= 0 -> "0 B"
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.2f MB", size / (1024.0 * 1024.0))
    }
}
