package com.simorgh.vpn.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app, for the per-app proxy picker (Derafsh AppManagerUtil pattern). */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object AppList {

    /** All launchable apps except Simorgh itself, sorted by label. */
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.applicationContext.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val own = context.applicationContext.packageName
        pm.queryIntentActivities(launcher, 0)
            .mapNotNull { ri ->
                val info = ri.activityInfo ?: return@mapNotNull null
                if (info.packageName == own) return@mapNotNull null
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull()
                )
            }
            .sortedWith(compareBy { it.label.lowercase() })
    }
}
