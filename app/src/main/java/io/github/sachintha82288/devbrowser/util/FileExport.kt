package io.github.sachintha82288.devbrowser.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Writes developer artifacts (console logs, performance recordings, storage
 * snapshots) to the public Downloads folder.
 *
 * On Android 10+ the MediaStore Downloads collection is used, which needs no
 * storage permission. On older releases the app-scoped external Downloads
 * directory is used (public Downloads would require WRITE_EXTERNAL_STORAGE).
 */
object FileExport {

    private const val TAG = "DevBrowser/Export"

    /** Returns a human-readable location on success, null on failure. */
    fun saveToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
    ): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, fileName, mimeType, content)
        } else {
            saveToAppDownloads(context, fileName, content)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not export $fileName", e)
        null
    }

    /** Binary variant used by the Stage A screenshot/PDF exporters. */
    fun saveBytes(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): String? {
        return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/DevBrowser",
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                null
            } else {
                resolver.openOutputStream(uri)?.use { stream -> stream.write(bytes) }
                val publish = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, publish, null, null)
                "Downloads/DevBrowser/$fileName"
            }
        } else {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (root == null) {
                null
            } else {
                val dir = File(root, "DevBrowser")
                if (!dir.exists() && !dir.mkdirs()) {
                    null
                } else {
                    val file = File(dir, fileName)
                    file.writeBytes(bytes)
                    file.absolutePath
                }
            }
        }
        } catch (e: Exception) {
            Log.w(TAG, "Could not export $fileName", e)
            null
        }
    }

    private fun saveWithMediaStore(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
    ): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/DevBrowser",
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: return null
        val publish = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        resolver.update(uri, publish, null, null)
        return "Downloads/DevBrowser/$fileName"
    }

    private fun saveToAppDownloads(context: Context, fileName: String, content: String): String? {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val dir = File(root, "DevBrowser")
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        file.writeText(content)
        return file.absolutePath
    }
}
