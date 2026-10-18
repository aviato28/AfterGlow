package com.nbansal.afterglow.core

import android.app.Application

class AfterglowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }
}
