package com.nbansal.afterglow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.nbansal.afterglow.home.HomeScreen
import com.nbansal.afterglow.ui.AfterglowBackground
import com.nbansal.afterglow.ui.TvCanvas
import com.nbansal.afterglow.ui.rememberGlowPhase

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A launcher must never exit on Back.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent {
            TvCanvas {
                Box(Modifier.fillMaxSize()) {
                    AfterglowBackground(rememberGlowPhase())
                    HomeScreen()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        super.onTrimMemory(level)
        // M1: clear the image memory cache here.
    }
}
