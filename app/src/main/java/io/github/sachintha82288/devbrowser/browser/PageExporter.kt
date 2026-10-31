package io.github.sachintha82288.devbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import io.github.sachintha82288.devbrowser.util.FileExport
import io.github.sachintha82288.devbrowser.util.PdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoSession
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage A: saves the current page as a PNG screenshot or a single-page PDF.
 * Both paths reuse [PageCapture], so they share the same stitching limits.
 */
object PageExporter {

    suspend fun saveScreenshot(
        context: Context,
        session: GeckoSession,
        fullPage: Boolean,
    ): String? {
        val bitmap = PageCapture.capture(session, fullPage) ?: return null
        val bytes = withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        }
        return FileExport.saveBytes(context, "devbrowser-${stamp()}.png", "image/png", bytes)
    }

    suspend fun savePdf(context: Context, session: GeckoSession): String? {
        val bitmap = PageCapture.capture(session, fullPage = true) ?: return null
        val jpeg = withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }
        }
        val pdf = withContext(Dispatchers.Default) {
            PdfWriter.fromJpeg(jpeg, bitmap.width, bitmap.height)
        }
        return FileExport.saveBytes(context, "devbrowser-${stamp()}.pdf", "application/pdf", pdf)
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
