package com.sportage.obd.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Leave resolution to Android: compatible installed map apps, Just once / Always,
 * and any existing user default. Do not wrap with createChooser, which prevents
 * the system from offering a persistent default.
 */
object NavigationLauncher {
    fun open(context: Context, latitude: Double, longitude: Double): Boolean {
        if (!TrackerMapPolicy.valid(latitude, longitude)) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?z=16"))
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
