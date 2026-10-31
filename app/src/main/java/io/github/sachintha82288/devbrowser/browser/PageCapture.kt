package io.github.sachintha82288.devbrowser.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import io.github.sachintha82288.devbrowser.debug.DebugBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import kotlin.math.ceil
import kotlin.math.min

/**
 * Stage A: full-page capture.
 *
 * GeckoView has no full-page screenshot API, so the page is scrolled viewport by
 * viewport, each frame is drawn into a bitmap and the slices are stitched. The
 * original scroll offset is restored afterwards. Captures are capped at
 * [MAX_SLICES] viewports so a very long page cannot exhaust memory.
 */
object PageCapture {

    /** The single GeckoView instance, set by BrowserScreen's AndroidView factory. */
    var view: GeckoView? = null

    private const val MAX_SLICES = 15
    private const val SETTLE_MS = 150L

    private const val METRICS_SCRIPT =
        "(function(){try{" +
            "var d=document.documentElement;" +
            "var b=document.body;" +
            "var h=Math.max(d?d.scrollHeight:0,b?b.scrollHeight:0);" +
            "return JSON.stringify({h:h,v:window.innerHeight||0,s:window.scrollY||0});" +
            "}catch(e){return 'null';}})()"

    private fun scrollScript(y: Int): String =
        "(function(){try{window.scrollTo(0,$y);return 'ok';}catch(e){return 'err';}})()"

    /**
     * Captures the page (or the visible viewport when [fullPage] is false).
     * Returns null when the view is not attached or the page reports no size.
     */
    suspend fun capture(session: GeckoSession, fullPage: Boolean = true): Bitmap? =
        withContext(Dispatchers.Main) {
            val host = view ?: return@withContext null
            if (host.width <= 0 || host.height <= 0) return@withContext null

            val metrics = DebugBridge.awaitJson(session, METRICS_SCRIPT) ?: return@withContext null
            val pageHeight = metrics.optInt("h", 0)
            val viewport = metrics.optInt("v", 0)
            val originalScroll = metrics.optInt("s", 0)
            if (pageHeight <= 0 || viewport <= 0) return@withContext null

            if (!fullPage) return@withContext drawView(host)

            val sliceCount = min(ceil(pageHeight.toDouble() / viewport).toInt(), MAX_SLICES)
            if (sliceCount <= 1) return@withContext drawView(host)

            val scale = host.height.toFloat() / viewport
            val totalHeight = min((pageHeight * scale).toInt(), host.height * sliceCount)
            val stitched = Bitmap.createBitmap(host.width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitched)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            var y = 0
            for (index in 0 until sliceCount) {
                val offset = index * viewport
                DebugBridge.awaitRaw(session, scrollScript(offset))
                delay(SETTLE_MS)
                val slice = drawView(host)
                val destTop = (offset * scale).toInt().coerceAtLeast(0)
                val remaining = totalHeight - destTop
                if (remaining <= 0) break
                val srcHeight = min(slice.height, (remaining / scale).toInt().coerceAtLeast(1))
                val src = Rect(0, 0, slice.width, srcHeight)
                val destHeight = min((srcHeight * scale).toInt().coerceAtLeast(1), remaining)
                val dest = Rect(0, destTop, host.width, destTop + destHeight)
                canvas.drawBitmap(slice, src, dest, paint)
                y = destTop + dest.height()
                if (y >= totalHeight) break
            }
            DebugBridge.awaitRaw(session, scrollScript(originalScroll))
            stitched
        }

    private fun drawView(host: GeckoView): Bitmap {
        val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        host.draw(canvas)
        return bitmap
    }
}
