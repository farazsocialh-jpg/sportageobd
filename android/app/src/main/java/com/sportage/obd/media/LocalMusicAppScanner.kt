package com.sportage.obd.media

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.musicAppDataStore by preferencesDataStore(name = "sportage_music_app")

val CHOSEN_MUSIC_APP_PACKAGE_KEY = stringPreferencesKey("chosen_music_app_package")

data class DiscoveredMusicApp(
    val packageName: String,
    val activityName: String,
    val appName: String,
    val icon: Drawable,
    val isHeadUnitStockApp: Boolean
)

object LocalMusicAppScanner {

    val KNOWN_HEAD_UNIT_STOCK_PACKAGES: List<String> = listOf(
        "com.microntek.music",
        "com.syu.music",
        "com.ts.music",
        "com.sds.music",
        "com.android.music",
        "com.mx.music",
        "com.yecon.music",
        "com.ts.main.music",
        "com.syu.ms.music"
    )

    fun queryInstalledMusicApps(context: Context): List<DiscoveredMusicApp> {
        val pm = context.packageManager
        val discovered = linkedMapOf<String, DiscoveredMusicApp>()

        fun addPackage(packageName: String, activityName: String? = null, isStock: Boolean = false) {
            if (packageName.isBlank() || discovered.containsKey(packageName)) return
            val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return
            val resolvedActivity = activityName
                ?: runCatching {
                    pm.getLaunchIntentForPackage(packageName)?.component?.className
                }.getOrNull()
                ?: appInfo.packageName
            val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(packageName)
            val icon = runCatching { appInfo.loadIcon(pm) }.getOrNull() ?: return
            val stock = isStock || packageName in KNOWN_HEAD_UNIT_STOCK_PACKAGES
            discovered[packageName] = DiscoveredMusicApp(
                packageName = packageName,
                activityName = resolvedActivity,
                appName = label,
                icon = icon,
                isHeadUnitStockApp = stock
            )
        }

        // (a) Official Android music launcher category.
        runCatching {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC).let { intent ->
                pm.queryIntentActivities(intent, 0).forEach { resolve ->
                    addPackage(resolve.activityInfo.packageName, resolve.activityInfo.name)
                }
            }
        }

        // (b) Apps capable of playing local MP3/FLAC files.
        runCatching {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("file:///dummy.mp3"), "audio/*")
            }.let { intent ->
                pm.queryIntentActivities(intent, 0).forEach { resolve ->
                    addPackage(resolve.activityInfo.packageName, resolve.activityInfo.name)
                }
            }
        }

        // (c) Legacy Android automotive music player action.
        runCatching {
            Intent("android.intent.action.MUSIC_PLAYER").let { intent ->
                pm.queryIntentActivities(intent, 0).forEach { resolve ->
                    addPackage(resolve.activityInfo.packageName, resolve.activityInfo.name)
                }
            }
        }

        // (d) Packages whose ApplicationInfo.category == CATEGORY_AUDIO.
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { appInfo ->
                if (appInfo.category == ApplicationInfo.CATEGORY_AUDIO) {
                    addPackage(appInfo.packageName)
                }
            }
        }

        // (e) Known head-unit stock packages.
        KNOWN_HEAD_UNIT_STOCK_PACKAGES.forEach { pkg ->
            runCatching {
                pm.getApplicationInfo(pkg, 0)
                addPackage(pkg, isStock = true)
            }
        }

        return discovered.values.sortedBy { it.appName.lowercase() }
    }
}

fun queryInstalledMusicApps(context: Context): List<DiscoveredMusicApp> =
    LocalMusicAppScanner.queryInstalledMusicApps(context)

suspend fun saveChosenMusicAppPackage(context: Context, packageName: String?) {
    context.musicAppDataStore.edit { prefs ->
        if (packageName == null) prefs.remove(CHOSEN_MUSIC_APP_PACKAGE_KEY)
        else prefs[CHOSEN_MUSIC_APP_PACKAGE_KEY] = packageName
    }
    // Keep legacy SharedPreferences in sync for older callers.
    saveChosenMusicApp(context, packageName)
}

fun chosenMusicAppPackageFlow(context: Context): Flow<String?> =
    context.musicAppDataStore.data.map { it[CHOSEN_MUSIC_APP_PACKAGE_KEY] }

fun chosenMusicAppPackage(context: Context): String? =
    runCatching {
        kotlinx.coroutines.runBlocking {
            context.musicAppDataStore.data.map { it[CHOSEN_MUSIC_APP_PACKAGE_KEY] }.let { flow ->
                var value: String? = null
                flow.collect { value = it }
                value
            }
        }
    }.getOrNull() ?: chosenMusicApp(context)