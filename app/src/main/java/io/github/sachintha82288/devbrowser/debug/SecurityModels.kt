package io.github.sachintha82288.devbrowser.debug

import org.json.JSONArray
import org.json.JSONObject

/** A form field detected on the page (features 70/71). */
data class FormField(
    val tag: String,
    val type: String,
    val name: String,
    val id: String,
    val autocomplete: String,
    val placeholder: String,
    val classification: String,
    val autofillPreview: String,
)

/** One `<script>` / `<link>` carrying an integrity attribute (feature 69). */
data class SriEntry(
    val url: String,
    val algorithm: String,
    val expected: String,
    val status: String,
)

/** Aggregated security facts about the current page. */
data class SecurityScan(
    val origin: String = "",
    val isHttps: Boolean = false,
    val mixedContent: List<String> = emptyList(),
    val cspMeta: String = "",
    val cspViolations: List<String> = emptyList(),
    val formFields: List<FormField> = emptyList(),
    val sriEntries: List<SriEntry> = emptyList(),
    val iframeCount: Int = 0,
    val xFrameOptions: String = "",
    val cspHeader: String = "",
    val hstsProbe: String = "unknown",
    val sriCoverage: String = "",
)

object SecurityParser {

    fun parseScan(payload: JSONObject?): SecurityScan? {
        if (payload == null) return null
        fun strings(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
        }
        val fields = ArrayList<FormField>()
        payload.optJSONArray("fields")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                fields.add(
                    FormField(
                        tag = obj.optString("tag"),
                        type = obj.optString("type"),
                        name = obj.optString("name"),
                        id = obj.optString("id"),
                        autocomplete = obj.optString("ac"),
                        placeholder = obj.optString("ph"),
                        classification = obj.optString("cls"),
                        autofillPreview = obj.optString("preview"),
                    ),
                )
            }
        }
        val sri = ArrayList<SriEntry>()
        payload.optJSONArray("sri")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                sri.add(
                    SriEntry(
                        url = obj.optString("url"),
                        algorithm = obj.optString("alg"),
                        expected = obj.optString("expected"),
                        status = obj.optString("status"),
                    ),
                )
            }
        }
        return SecurityScan(
            origin = payload.optString("origin"),
            isHttps = payload.optBoolean("https"),
            mixedContent = strings(payload.optJSONArray("mixed")),
            cspMeta = payload.optString("cspMeta"),
            cspViolations = strings(payload.optJSONArray("violations")),
            formFields = fields,
            sriEntries = sri,
            iframeCount = payload.optInt("frames"),
            sriCoverage = payload.optString("sriCoverage"),
        )
    }

    /** Feature 74: a single A-F grade with per-factor reasoning. */
    fun grade(
        scan: SecurityScan,
        certificate: io.github.sachintha82288.devbrowser.browser.CertificateInfo?,
        headers: JSONObject?,
    ): SecurityGrade {
        var score = 100
        val notes = ArrayList<Pair<String, String>>()

        if (!scan.isHttps) {
            score -= 40
            notes.add("transport" to "Page is not served over HTTPS")
        } else {
            notes.add("transport" to "Encrypted with HTTPS")
        }
        if (scan.mixedContent.isNotEmpty()) {
            score -= minOf(30, scan.mixedContent.size * 6)
            notes.add("mixed" to "${scan.mixedContent.size} insecure sub-resource(s)")
        } else {
            notes.add("mixed" to "No mixed content")
        }

        val csp = headers?.optString("csp").orEmpty().ifBlank { scan.cspMeta }
        if (csp.isBlank()) {
            score -= 15
            notes.add("csp" to "No Content-Security-Policy")
        } else {
            notes.add("csp" to "Content-Security-Policy present")
        }

        val hsts = headers?.optString("hsts").orEmpty()
        when {
            hsts.isNotBlank() -> notes.add("hsts" to "Strict-Transport-Security sent")
            scan.hstsProbe == "upgraded" -> {
                score -= 5
                notes.add("hsts" to "Upgrades to HTTPS (HSTS likely)")
            }
            else -> {
                score -= 10
                notes.add("hsts" to "No HSTS policy detected")
            }
        }

        val xfo = headers?.optString("xfo").orEmpty().ifBlank { scan.xFrameOptions }
        val frameAncestors = csp.contains("frame-ancestors")
        if (xfo.isBlank() && !frameAncestors) {
            score -= 10
            notes.add("xfo" to "No X-Frame-Options or frame-ancestors")
        } else {
            notes.add("xfo" to "Framing restricted")
        }

        val withIntegrity = scan.sriEntries.count { it.status != "missing" }
        if (scan.sriEntries.isNotEmpty()) {
            val passed = scan.sriEntries.count { it.status == "pass" }
            if (passed < scan.sriEntries.size) {
                score -= 10
                notes.add("sri" to "${scan.sriEntries.size - passed} integrity failure(s)")
            } else {
                notes.add("sri" to "$withIntegrity sub-resource(s) verified")
            }
        } else {
            notes.add("sri" to "No sub-resource integrity hashes")
        }

        if (certificate != null && !certificate.validNow) {
            score -= 30
            notes.add("certificate" to "Certificate is not currently valid")
        }

        val safe = score.coerceIn(0, 100)
        val letter = when {
            safe >= 90 -> "A"
            safe >= 80 -> "B"
            safe >= 70 -> "C"
            safe >= 60 -> "D"
            safe >= 50 -> "E"
            else -> "F"
        }
        return SecurityGrade(letter, safe, notes)
    }
}

data class SecurityGrade(
    val letter: String,
    val score: Int,
    val factors: List<Pair<String, String>>,
)
