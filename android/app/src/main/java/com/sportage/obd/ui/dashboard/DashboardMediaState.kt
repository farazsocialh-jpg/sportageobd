package com.sportage.obd.ui.dashboard

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sportage.obd.media.NowPlayingController
import com.sportage.obd.media.chosenMusicApp

/**
 * Binds the dashboard to the active media session for as long as a layout theme is on
 * screen. Both themes receive the same [DashboardMedia] instance from the launcher host,
 * so switching layouts does not create a second session or reset playback metadata.
 *
 * When no MediaBrowserService is reachable, transport buttons fall back to standard
 * media-key broadcasts, matching the existing Media page behaviour.
 */
@Composable
fun rememberDashboardMedia(
    enabled: Boolean = true,
    fallbackMediaKey: (Int) -> Unit
): DashboardMedia {
    val context = LocalContext.current
    val controller = remember(context) { NowPlayingController(context) }
    val nowPlaying by controller.state.collectAsState()

    DisposableEffect(controller, enabled) {
        if (enabled) controller.connect(chosenMusicApp(context))
        onDispose { if (enabled) controller.disconnect() }
    }

    return DashboardMedia(
        nowPlaying = if (enabled) nowPlaying else null,
        onPrevious = {
            if (controller.isConnected) controller.previous()
            else fallbackMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        },
        onPlayPause = {
            if (controller.isConnected) controller.playPause()
            else fallbackMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        },
        onNext = {
            if (controller.isConnected) controller.next()
            else fallbackMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    )
}
