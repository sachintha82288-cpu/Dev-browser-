package io.github.sachintha82288.devbrowser.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import org.mozilla.geckoview.WebResponse

/**
 * Hands download responses to Android's system DownloadManager so users get
 * progress notifications, retry behaviour and files in /Downloads.
 */
object Downloader {

    fun enqueue(context: Context, response: WebResponse): Boolean {
        return try {
            val uri = Uri.parse(response.uri)
            val request = DownloadManager.Request(uri)

            val contentType = response.headers["Content-Type"]?.substringBefore(';')?.trim()
            if (!contentType.isNullOrBlank()) {
                request.setMimeType(contentType)
            }
            // Forward cookies / auth headers so the re-request succeeds.
            response.headers.forEach { (key, value) ->
                val lower = key.lowercase()
                if (lower == "cookie" || lower == "authorization" || lower.startsWith("x-")) {
                    request.addRequestHeader(key, value)
                }
            }

            val filename = fileName(response)
            request.setTitle(filename)
            request.setDescription(response.uri)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            request.setAllowedOverMetered(true)
            request.setAllowedOverRoaming(true)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            true
        } catch (e: Throwable) {
            Log.w("DevBrowser/Download", "Failed to enqueue download", e)
            false
        }
    }

    private fun fileName(response: WebResponse): String {
        val disposition = response.headers["Content-Disposition"]
        val fromHeader = disposition?.let { parseFilename(it) }
        if (!fromHeader.isNullOrBlank()) return sanitize(fromHeader)
        val fromUri = try {
            Uri.parse(response.uri).lastPathSegment
        } catch (_: Exception) {
            null
        }
        if (!fromUri.isNullOrBlank()) return sanitize(fromUri)
        return "devbrowser-download-${System.currentTimeMillis()}"
    }

    /** Parse `attachment; filename="foo.zip"` / `filename=foo.zip`. */
    private fun parseFilename(header: String): String? {
        val regex = Regex("""filename\*?=(?:UTF-8''|")?([^";]+)"?""", RegexOption.IGNORE_CASE)
        return regex.find(header)?.groupValues?.get(1)
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return if (cleaned.length > 120) cleaned.takeLast(120) else cleaned.ifBlank { "download" }
    }
}
