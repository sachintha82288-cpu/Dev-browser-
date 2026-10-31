package io.github.sachintha82288.devbrowser.browser

import android.util.Log
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Human-readable summary of the page's TLS certificate. */
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val notBefore: String,
    val notAfter: String,
    val serial: String,
    val signatureAlgorithm: String,
    val keyAlgorithm: String,
    val keySize: Int,
    val subjectAltNames: List<String>,
    val expired: Boolean,
    val selfSigned: Boolean,
    val validNow: Boolean,
)

/**
 * Reads the leaf certificate out of GeckoView's `SecurityInformation`.
 *
 * The field is accessed reflectively because its presence differs between
 * GeckoView builds; when it is missing the certificate panel simply reports that
 * the engine did not expose a certificate instead of breaking the build.
 */
object CertificateInspector {

    private const val TAG = "DevBrowser/Cert"

    fun fromSecurityInformation(info: Any?): CertificateInfo? {
        if (info == null) return null
        val certificate = runCatching {
            val field = info.javaClass.fields.firstOrNull { it.name == "certificate" } ?: return null
            field.isAccessible = true
            field.get(info) as? X509Certificate
        }.onFailure { Log.d(TAG, "certificate field unavailable: ${it.message}") }
            .getOrNull() ?: return null
        return describe(certificate)
    }

    fun describe(certificate: X509Certificate): CertificateInfo {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val altNames = runCatching {
            certificate.subjectAlternativeNames
                ?.mapNotNull { entry ->
                    entry?.takeIf { it.size >= 2 }?.let { "${it[0]}: ${it[1]}" }
                }
                ?.take(24)
                .orEmpty()
        }.getOrDefault(emptyList())

        val key = certificate.publicKey
        val keyAlgorithm = key?.algorithm ?: "unknown"
        val keySize = when (key) {
            is RSAPublicKey -> key.modulus.bitLength()
            is java.security.interfaces.DSAPublicKey -> key.params.p.bitLength()
            is ECPublicKey -> key.params.order.bitLength()
            else -> 0
        }
        val expired = runCatching { certificate.notAfter.before(Date()) }.getOrDefault(false)
        return CertificateInfo(
            subject = certificate.subjectX500Principal?.name ?: "unknown",
            issuer = certificate.issuerX500Principal?.name ?: "unknown",
            notBefore = runCatching { formatter.format(certificate.notBefore) }.getOrDefault("?"),
            notAfter = runCatching { formatter.format(certificate.notAfter) }.getOrDefault("?"),
            serial = runCatching { certificate.serialNumber.toString(16) }.getOrDefault("?"),
            signatureAlgorithm = certificate.sigAlgName ?: "unknown",
            keyAlgorithm = keyAlgorithm,
            keySize = keySize,
            subjectAltNames = altNames,
            expired = expired,
            selfSigned = runCatching {
                certificate.subjectX500Principal == certificate.issuerX500Principal
            }.getOrDefault(false),
            validNow = runCatching { certificate.checkValidity() }.isSuccess,
        )
    }
}
