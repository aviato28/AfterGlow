package com.nbansal.afterglow.takeover

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.nbansal.afterglow.HomeActivity

/** Only watches the Home key. Reads no screen content and receives no accessibility events. */
class HomeKeyService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean = handleKey(event.keyCode, event.action) {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    companion object {
        /** Home down opens Afterglow and is consumed (Home up too, so the system sees neither); all other keys pass. */
        fun handleKey(keyCode: Int, action: Int, openHome: () -> Unit): Boolean {
            if (keyCode != KeyEvent.KEYCODE_HOME) return false
            if (action == KeyEvent.ACTION_DOWN) openHome()
            return true
        }
    }
}
