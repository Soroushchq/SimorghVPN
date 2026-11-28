package com.simorgh.vpn.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.simorgh.vpn.BuildConfig
import java.io.File

/**
 * Offline APK sharing (openrung OpenRungApkShare pattern): hand the installed
 * app binary to a friend with zero connectivity — no store, no download, no
 * server. Stages a versioned copy in the share cache and hands a content://
 * uri to the system share sheet.
 */
object ApkSharer {

    private fun stage(context: Context): File? = runCatching {
        val app = context.applicationContext
        val src = File(app.applicationInfo.sourceDir)
        if (!src.exists() || src.length() < 1_000_000) return null

        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        // versioned name so receivers can tell releases apart
        val out = File(dir, "SimorghVPN-${BuildConfig.VERSION_NAME}.apk")
        if (!out.exists() || out.length() != src.length()) {
            val tmp = File(dir, out.name + ".part")
            src.copyTo(tmp, overwrite = true)
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) return null
        }
        out
    }.getOrNull()

    /**
     * Opens the system share sheet with the APK as a stream.
     * @return false when the binary could not be staged (split-only install,
     *   removed source, full disk — the button then shows a snackbar).
     */
    fun share(context: Context, chooserTitle: String): Boolean {
        val app = context.applicationContext
        val staged = stage(app) ?: return false
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", staged)
        }.getOrNull() ?: return false

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { app.startActivity(chooser) }.isSuccess
    }
}
