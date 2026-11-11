package com.sportage.obd.ui

import com.sportage.obd.media.DiscoveredMusicApp as MediaDiscoveredMusicApp
import com.sportage.obd.media.LocalMusicAppScanner as MediaLocalMusicAppScanner
import com.sportage.obd.media.queryInstalledMusicApps as mediaQueryInstalledMusicApps

typealias DiscoveredMusicApp = MediaDiscoveredMusicApp
typealias LocalMusicAppScanner = MediaLocalMusicAppScanner

fun queryInstalledMusicApps(context: android.content.Context) = mediaQueryInstalledMusicApps(context)