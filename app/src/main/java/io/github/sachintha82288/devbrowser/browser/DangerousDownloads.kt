package io.github.sachintha82288.devbrowser.browser

import java.util.Locale

/**
 * Stage B: dangerous-download classification.
 *
 * Installers and executables get a warning before they reach DownloadManager;
 * the check is intentionally extension-based because the content type is
 * supplied by the remote server.
 */
object DangerousDownloads {

    private val EXTENSIONS = setOf(
        "apk", "aab", "exe", "msi", "msix", "bat", "cmd", "com", "scr", "pif",
        "jar", "dmg", "pkg", "deb", "rpm", "sh", "bash", "ps1", "vbs", "js",
        "jse", "wsf", "hta", "cpl", "lnk", "reg",
    )

    private val ARCHIVES = setOf("zip", "7z", "rar", "tar", "gz", "xz", "iso")

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.US)

    fun isDangerous(fileName: String): Boolean = EXTENSIONS.contains(extensionOf(fileName))

    fun isArchive(fileName: String): Boolean = ARCHIVES.contains(extensionOf(fileName))

    /** True when the name is an APK specifically (installed via the system installer). */
    fun isAndroidPackage(fileName: String): Boolean =
        extensionOf(fileName) == "apk" || extensionOf(fileName) == "aab"
}
