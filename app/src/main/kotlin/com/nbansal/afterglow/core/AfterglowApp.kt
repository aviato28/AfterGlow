package com.nbansal.afterglow.core

import android.app.Application

class AfterglowApp : Application() {
    lateinit var model: AppModel
        private set

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        model = AppModel(this)
    }
}
