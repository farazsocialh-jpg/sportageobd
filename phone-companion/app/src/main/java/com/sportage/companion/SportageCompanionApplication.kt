package com.sportage.companion

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import com.sportage.companion.di.appModule

class SportageCompanionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SportageCompanionApplication)
            modules(appModule)
        }
    }
}