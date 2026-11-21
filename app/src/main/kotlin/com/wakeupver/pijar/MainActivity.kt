package com.wakeupver.pijar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeupver.pijar.gamepad.VirtualGamepad
import com.wakeupver.pijar.ui.PijarRoot
import com.wakeupver.pijar.ui.theme.PijarTheme

/**
 * Activity tunggal yang berperan sebagai aplikasi Home (CATEGORY_HOME) sekaligus peluncur biasa.
 * launchMode=singleTask: menekan Home saat sudah terbuka masuk lewat [onNewIntent].
 */
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge penuh: bilah status/navigasi transparan, kompatibel dengan navigasi gestur sistem.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            PijarTheme(prefs) {
                PijarRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        VirtualGamepad.hide()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }
}
