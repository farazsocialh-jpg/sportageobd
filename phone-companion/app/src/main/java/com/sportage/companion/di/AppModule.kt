package com.sportage.companion.di

import com.sportage.companion.data.auth.AuthRepository
import com.sportage.companion.data.auth.FirebaseAuthRepository
import com.sportage.companion.data.mqtt.CompanionDataRepository
import com.sportage.companion.data.vehicle.VehicleRepository
import com.sportage.companion.viewmodel.AuthViewModel
import com.sportage.companion.viewmodel.DashboardViewModel
import com.sportage.companion.viewmodel.FindMyCarViewModel
import com.sportage.companion.viewmodel.VehicleSetupViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { VehicleRepository(androidContext()) }
    single<AuthRepository> { FirebaseAuthRepository() }
    single { CompanionDataRepository(get()) }

    viewModel { AuthViewModel(get()) }
    viewModel { VehicleSetupViewModel(get(), get()) }
    viewModel { DashboardViewModel(get()) }
    viewModel { FindMyCarViewModel(get()) }
}