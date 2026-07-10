package com.drivingcoach

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DrivingCoachApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
