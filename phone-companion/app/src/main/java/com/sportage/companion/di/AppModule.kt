package com.sportage.companion.di

import com.sportage.companion.data.config.MqttConfigRepository
import com.sportage.companion.data.mqtt.CompanionDataRepository
import com.sportage.companion.viewmodel.ConnectionSetupViewModel
import com.sportage.companion.viewmodel.DashboardViewModel
import com.sportage.companion.viewmodel.FindMyCarViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { MqttConfigRepository(androidContext()) }
    single { CompanionDataRepository(get()) }

    viewModel { ConnectionSetupViewModel(get()) }
    viewModel { DashboardViewModel(get()) }
    viewModel { FindMyCarViewModel(get()) }
}