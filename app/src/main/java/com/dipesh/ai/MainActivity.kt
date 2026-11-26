package com.dipesh.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dipesh.ai.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { AppRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** The floating glass orb opens the app with listen=true. */
    private fun handle(i: Intent?) {
        if (i?.getBooleanExtra("listen", false) == true) {
            vm.pendingListen = true
            i.removeExtra("listen")
        }
    }
}
