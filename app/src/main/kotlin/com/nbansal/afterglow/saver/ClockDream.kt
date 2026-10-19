package com.nbansal.afterglow.saver

import android.service.dreams.DreamService

/** "Afterglow clock": the system screensaver. Everything is drawn by [ClockView]; nothing runs once dreaming stops. */
class ClockDream : DreamService() {
    private var view: ClockView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        view = ClockView(this).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.start()
    }

    override fun onDreamingStopped() {
        view?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        view?.stop()
        view = null
        super.onDetachedFromWindow()
    }
}
