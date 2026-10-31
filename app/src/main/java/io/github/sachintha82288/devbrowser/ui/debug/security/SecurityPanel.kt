package io.github.sachintha82288.devbrowser.ui.debug.security

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.DevBrowserApplication
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.browser.BrowserTab
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import io.github.sachintha82288.devbrowser.debug.SecurityGrade
import io.github.sachintha82288.devbrowser.debug.SecurityParser
import io.github.sachintha82288.devbrowser.debug.SecurityScan
import io.github.sachintha82288.devbrowser.debug.SecurityScripts
import io.github.sachintha82288.devbrowser.debug.SriEntry
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugBadge
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugEmpty
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugKeyValue
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMono
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugMonoSmall
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugPanelScaffold
import io.github.sachintha82288.devbrowser.ui.debug.components.DebugSection
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import io.github.sachintha82288.devbrowser.ui.theme.NeonPurple
import io.github.sachintha82288.devbrowser.ui.theme.NeonRed
import io.github.sachintha82288.devbrowser.ui.theme.SuccessGreen
import io.github.sachintha82288.devbrowser.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Category 8 — security (features 65-74).
 *
 * The certificate comes from GeckoView's SecurityInformation; everything else is
 * derived from page APIs (CSP meta/violations, SRI hashes, form scanning) and a
 * same-origin re-fetch of the document for its response headers.
 */
@Composable
fun SecurityPanel(app: DevBrowserApplication, tab: BrowserTab, onClose: () -> Unit) {
    var scan by remember { mutableStateOf<SecurityScan?>(null) }
    var headers by remember { mutableStateOf<JSONObject?>(null) }
    var hsts by remember { mutableStateOf("unknown") }
    var sri by remember { mutableStateOf<List<SriEntry>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(tab.id, refreshTick) {
        DebugBridge.evalRaw(tab.session, SecurityScripts.installViolations) {}
        scan = SecurityParser.parseScan(DebugBridge.awaitJson(tab.session, SecurityScripts.scan))

        DebugBridge.evalRaw(tab.session, SecurityScripts.headersStart) {}
        DebugBridge.evalRaw(tab.session, SecurityScripts.hstsStart) {}
        DebugBridge.evalRaw(tab.session, SecurityScripts.sriStart) {}

        repeat(14) {
            if (headers == null) {
                headers = DebugBridge.awaitJson(tab.session, SecurityScripts.asyncHeaders)
            }
            val hstsPayload = DebugBridge.awaitJson(tab.session, SecurityScripts.asyncHsts)
            if (hstsPayload != null) {
                val value = hstsPayload.optString("value", "unknown")
                if (value != "unknown") hsts = value
            }
            if (sri.isEmpty()) {
                val sriPayload = DebugBridge.awaitJson(tab.session, SecurityScripts.asyncSri)
                val entries = sriPayload?.optJSONArray("entries")
                if (entries != null && entries.length() > 0) {
                    sri = SecurityParser.parseScan(
                        JSONObject().put("sri", entries),
                    )?.sriEntries.orEmpty()
                } else if (sriPayload != null && entries != null) {
                    sri = emptyList()
                }
            }
            if (headers != null && hsts != "unknown") return@LaunchedEffect
            delay(400)
        }
    }

    val current = scan
    val certificate = tab.certificate
    val grade: SecurityGrade? = current?.let {
        SecurityParser.grade(
            it.copy(hstsProbe = hsts, sriEntries = sri.ifEmpty { it.sriEntries }),
            certificate,
            headers,
        )
    }

    DebugPanelScaffold(
        title = stringResource(R.string.dbg_cat_security),
        subtitle = current?.origin ?: tab.url,
        onClose = onClose,
        actions = {
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.dbg_sec_refresh), tint = NeonCyan)
            }
        },
    ) {
        if (current == null) {
            DebugEmpty(stringResource(R.string.dbg_sec_unavailable), modifier = Modifier.fillMaxSize())
            return@DebugPanelScaffold
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (grade != null) GradeCard(grade)

            DebugSection(stringResource(R.string.dbg_sec_certificate))
            if (certificate == null) {
                Text(
                    text = stringResource(R.string.dbg_sec_no_certificate),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            } else {
                DebugKeyValue(stringResource(R.string.dbg_sec_subject), certificate.subject)
                DebugKeyValue(stringResource(R.string.dbg_sec_issuer), certificate.issuer)
                DebugKeyValue(stringResource(R.string.dbg_sec_valid_from), certificate.notBefore)
                DebugKeyValue(
                    stringResource(R.string.dbg_sec_valid_to),
                    certificate.notAfter,
                    valueColor = if (certificate.expired) NeonRed else SuccessGreen,
                )
                DebugKeyValue(stringResource(R.string.dbg_sec_serial), certificate.serial)
                DebugKeyValue(stringResource(R.string.dbg_sec_signature), certificate.signatureAlgorithm)
                DebugKeyValue(
                    stringResource(R.string.dbg_sec_key),
                    "${certificate.keyAlgorithm} ${certificate.keySize} bit",
                )
                if (certificate.selfSigned) {
                    DebugKeyValue(
                        stringResource(R.string.dbg_sec_self_signed),
                        stringResource(R.string.dbg_sec_yes),
                        valueColor = NeonRed,
                    )
                }
                DebugSection(stringResource(R.string.dbg_sec_san))
                if (certificate.subjectAltNames.isEmpty()) {
                    DebugEmpty(stringResource(R.string.dbg_sec_no_san))
                } else {
                    certificate.subjectAltNames.forEach { name ->
                        Text(
                            text = name,
                            style = DebugMonoSmall,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.dbg_sec_chain_note),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }

            DebugSection(stringResource(R.string.dbg_sec_tls))
            DebugKeyValue(stringResource(R.string.dbg_sec_protocol), if (current.isHttps) "TLS (HTTPS page)" else "none")
            DebugKeyValue(
                stringResource(R.string.dbg_sec_cipher),
                stringResource(R.string.dbg_sec_not_exposed),
            )
            DebugKeyValue(
                stringResource(R.string.dbg_sec_forward_secrecy),
                stringResource(R.string.dbg_sec_not_exposed),
            )
            if (certificate != null) {
                DebugKeyValue(stringResource(R.string.dbg_sec_key), "${certificate.keyAlgorithm} ${certificate.keySize} bit")
            }

            DebugSection(stringResource(R.string.dbg_sec_mixed))
            if (current.mixedContent.isEmpty()) {
                Text(
                    text = stringResource(R.string.dbg_sec_no_mixed),
                    style = DebugMonoSmall,
                    color = SuccessGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                current.mixedContent.forEach { url ->
                    Text(
                        text = url,
                        style = DebugMonoSmall,
                        color = NeonRed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                    )
                }
            }

            DebugSection(stringResource(R.string.dbg_sec_csp))
            val cspHeader = headers?.optString("csp").orEmpty()
            val csp = cspHeader.ifBlank { current.cspMeta }
            if (csp.isBlank()) {
                Text(
                    text = stringResource(R.string.dbg_sec_no_csp),
                    style = DebugMonoSmall,
                    color = WarningAmber,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                csp.split(';').filter { it.isNotBlank() }.forEach { directive ->
                    Text(
                        text = directive.trim(),
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                text = "${stringResource(R.string.dbg_sec_violations)}: ${current.cspViolations.size}",
                style = DebugMonoSmall,
                color = NeonPurple,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp),
            )
            current.cspViolations.takeLast(20).forEach { violation ->
                Text(
                    text = "• $violation",
                    style = DebugMonoSmall,
                    color = WarningAmber,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                )
            }

            DebugSection(stringResource(R.string.dbg_sec_hsts))
            val hstsHeader = headers?.optString("hsts").orEmpty()
            val hstsText = when {
                hstsHeader.isNotBlank() -> hstsHeader
                hsts == "upgraded" -> stringResource(R.string.dbg_sec_hsts_upgraded)
                hsts == "not-upgraded" -> stringResource(R.string.dbg_sec_hsts_absent)
                hsts == "http-page" -> stringResource(R.string.dbg_sec_hsts_http)
                else -> stringResource(R.string.dbg_sec_hsts_unknown)
            }
            DebugKeyValue(stringResource(R.string.dbg_sec_hsts), hstsText)
            Text(
                text = stringResource(R.string.dbg_sec_hsts_note),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )

            DebugSection(stringResource(R.string.dbg_sec_xfo))
            val xfo = headers?.optString("xfo").orEmpty()
            if (xfo.isBlank() && !csp.contains("frame-ancestors")) {
                Text(
                    text = stringResource(R.string.dbg_sec_xfo_missing),
                    style = DebugMonoSmall,
                    color = NeonRed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                DebugKeyValue("X-Frame-Options", xfo.ifBlank { "—" })
                DebugKeyValue(
                    "frame-ancestors",
                    if (csp.contains("frame-ancestors")) stringResource(R.string.dbg_sec_yes)
                    else stringResource(R.string.dbg_sec_no),
                )
            }

            DebugSection(stringResource(R.string.dbg_sec_sri))
            if (sri.isEmpty()) {
                Text(
                    text = stringResource(R.string.dbg_sec_no_sri),
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                sri.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DebugBadge(text = entry.status, color = sriColor(entry.status))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.url.takeLast(56),
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            DebugSection("${stringResource(R.string.dbg_sec_forms)} (${current.formFields.size})")
            if (current.formFields.isEmpty()) {
                DebugEmpty(stringResource(R.string.dbg_sec_no_forms))
            }
            current.formFields.take(60).forEach { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DebugBadge(text = field.classification, color = NeonPurple)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${field.tag}[${field.type}]${if (field.name.isNotBlank()) " name=${field.name}" else ""}",
                        style = DebugMonoSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (field.autofillPreview.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = field.autofillPreview,
                            style = DebugMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun GradeCard(grade: SecurityGrade) {
    val color = when (grade.letter) {
        "A" -> SuccessGreen
        "B" -> SuccessGreen
        "C" -> WarningAmber
        "D" -> WarningAmber
        else -> NeonRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = grade.letter, style = DebugMono, color = color)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.dbg_sec_grade),
                style = DebugMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "${grade.score} / 100", style = DebugMono, color = MaterialTheme.colorScheme.onSurface)
            grade.factors.take(4).forEach { factor ->
                Text(
                    text = "• ${factor.first}: ${factor.second}",
                    style = DebugMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun sriColor(status: String): Color = when (status) {
    "pass" -> SuccessGreen
    "fail" -> NeonRed
    "pending" -> WarningAmber
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
