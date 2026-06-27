package com.phtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.phtv.app.ui.PhtvNavHost
import com.phtv.app.ui.theme.PhtvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug hook: `adb shell am start -n com.phtv.app/.MainActivity --es viewkey <id>`
        // opens the player directly, bypassing UI focus/navigation.
        val startViewkey = intent?.getStringExtra("viewkey")?.takeIf { it.isNotBlank() }
        setContent {
            PhtvTheme {
                PhtvNavHost(startViewkey = startViewkey, onExitApp = { finish() })
            }
        }
    }
}
