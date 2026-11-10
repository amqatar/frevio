package com.amqatar.marquee.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Lets a test server be set from a computer:
        // adb shell am start -n com.amqatar.marquee.tv/.MainActivity --es base http://192.168.1.20:8000/
        val baseFromIntent = intent.getStringExtra("base")
        setContent { PlayerTestApp(this, baseFromIntent) }
    }

    /** 0 restores the system's choice. */
    fun setDisplayMode(modeId: Int) {
        window.attributes = window.attributes.also { it.preferredDisplayModeId = modeId }
    }
}
