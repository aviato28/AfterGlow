package com.nbansal.afterglow.saver

import android.service.dreams.DreamService
import com.nbansal.afterglow.showcase.ShowcaseImages

/** "Afterglow artwork": the system screensaver showing cached Showcase backdrops. Disk only; no network. Drawn by [ArtView]. */
class ArtDream : DreamService() {
    private var view: ArtView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        view = ArtView(this, ShowcaseImages.cachedBackdrops(this)).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        // Same rule as the clock: if something is playing, give the screen back.
        if (getSystemService(android.media.AudioManager::class.java)?.isMusicActive == true) {
            wakeUp()
            return
        }
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
