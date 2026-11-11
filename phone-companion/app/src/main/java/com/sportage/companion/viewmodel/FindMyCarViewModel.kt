package com.sportage.companion.viewmodel

import androidx.lifecycle.ViewModel
import com.sportage.companion.data.model.ParkedLocation
import com.sportage.companion.data.mqtt.CompanionDataRepository
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

class FindMyCarViewModel(private val repo: CompanionDataRepository) : ViewModel() {
    val parkedLocation: StateFlow<ParkedLocation?> = repo.parkedLocation
    val isDriving: StateFlow<Boolean> = repo.isDriving

    fun parkedDurationText(loc: ParkedLocation?): String {
        if (loc == null) return "No parked location yet"
        val diffMs = System.currentTimeMillis() - loc.timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val hours = minutes / 60
        return when {
            hours > 0 -> "Parked ${hours}h ${minutes % 60}m ago"
            minutes > 0 -> "Parked ${minutes}m ago"
            else -> "Parked just now"
        }
    }
}