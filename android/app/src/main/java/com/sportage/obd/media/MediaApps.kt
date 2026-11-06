package com.sportage.obd.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Installed app that can play music: either exposes a MediaBrowserService (preferred,
 * allows now-playing sync and in-app controls) or at least a launchable music activity.
 */
data class MediaAppInfo(
    val label: String,
    val packageName: String,
    val browseComponent: ComponentName?,
    val launchIntent: Intent?
)

/** Live snapshot of the active media session, when a MediaBrowserService is reachable. */
data class NowPlaying(
    val appName: String,
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean
)

object MediaApps {

    /** Apps that expose android.media.browse.MediaBrowserService (real sync possible). */
    fun browseComponents(context: Context): List<ComponentName> =
        context.packageManager.queryIntentServices(
            Intent("android.media.browse.MediaBrowserService"), PackageManager.GET_META_DATA
        ).mapNotNull { it.serviceInfo?.let { si -> ComponentName(si.packageName, si.name) } }

    /** All launchable music apps (media browsers first, then generic music players). */
    fun musicApps(context: Context): List<MediaAppInfo> {
        val pm = context.packageManager
        val browsers = browseComponents(context)
        val result = mutableListOf<MediaAppInfo>()
        val seen = mutableSetOf<String>()
        for (component in browsers) {
            if (seen.add(component.packageName)) {
                val label = runCatching {
                    pm.getApplicationInfo(component.packageName, 0).loadLabel(pm).toString()
                }.getOrDefault(component.packageName)
                result.add(MediaAppInfo(label, component.packageName, component, null))
            }
        }
        val musicIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        for (resolve in pm.queryIntentActivities(musicIntent, 0)) {
            val pkg = resolve.activityInfo.packageName
            if (seen.add(pkg)) {
                val label = runCatching {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                }.getOrDefault(pkg)
                result.add(MediaAppInfo(label, pkg, null, resolve.let {
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                        .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }))
            }
        }
        return result
    }

    /** Launchable apps whose name suggests FM/radio capability. */
    fun radioApps(context: Context): List<MediaAppInfo> {
        val pm = context.packageManager
        return pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).mapNotNull { resolve ->
            val pkg = resolve.activityInfo.packageName
            val label = runCatching {
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            }.getOrDefault(pkg)
            if (label.contains("radio", ignoreCase = true) || label.contains(" fm", ignoreCase = true)) {
                val intent = pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                MediaAppInfo(label, pkg, null, intent)
            } else null
        }
    }
}

/**
 * Connects to one app's MediaBrowserService and mirrors its active media session:
 * track metadata and playback state update in real time, and the transport controls
 * act on that session directly, so the app's own UI and this dashboard stay in sync.
 * Falls back gracefully: when no service is reachable, callers should use media keys.
 */
class NowPlayingController(private val context: Context) {

    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    private var browser: MediaBrowserCompat? = null
    private var controller: MediaControllerCompat? = null
    private var controllerCallback: MediaControllerCompat.Callback? = null

    /** Preferred app package, or null to connect to the first available browser service. */
    fun connect(preferredPackage: String?) {
        disconnect()
        val components = MediaApps.browseComponents(context)
        val component = components.firstOrNull { it.packageName == preferredPackage } ?: components.firstOrNull()
            ?: return
        val pm = context.packageManager
        val appName = runCatching {
            pm.getApplicationInfo(component.packageName, 0).loadLabel(pm).toString()
        }.getOrDefault(component.packageName)
        browser = MediaBrowserCompat(context, component, object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                val token = browser?.sessionToken ?: return
                controller = MediaControllerCompat(context, token)
                controllerCallback = object : MediaControllerCompat.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadataCompat?) = publish(appName)
                    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = publish(appName)
                }.also { controller?.registerCallback(it) }
                publish(appName)
            }
        }, null).also { it.connect() }
    }

    private fun publish(appName: String) {
        val c = controller ?: return
        val metadata = c.metadata
        val playing = c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING ||
            c.playbackState?.state == PlaybackStateCompat.STATE_BUFFERING
        _state.value = NowPlaying(
            appName = appName,
            title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
            artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
            isPlaying = playing
        )
    }

    val isConnected: Boolean get() = controller != null

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    fun disconnect() {
        controllerCallback?.let { cb -> runCatching { controller?.unregisterCallback(cb) } }
        controllerCallback = null
        controller = null
        runCatching { browser?.disconnect() }
        browser = null
        _state.value = null
    }
}

/**
 * Small helper so the picker can remember the chosen music app package across restarts.
 */
fun mediaAppPrefs(context: Context) =
    context.getSharedPreferences("sportage_media_apps", Context.MODE_PRIVATE)

fun saveChosenMusicApp(context: Context, packageName: String?) {
    mediaAppPrefs(context).edit().putString("musicApp", packageName).apply()
}

fun chosenMusicApp(context: Context): String? = mediaAppPrefs(context).getString("musicApp", null)