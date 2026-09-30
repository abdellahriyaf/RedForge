package com.redforge.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.updateAll
import com.redforge.app.widget.RedForgeWidget
import com.redforge.intro.AnvilDropIntro
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import com.redforge.app.navigation.RedForgeApp
import com.redforge.app.ui.theme.RedForgeTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            runCatching {
                RedForgeWidget().updateAll(applicationContext)
            }.onFailure {
                Log.e("RedForge", "Widget refresh failed; continuing app startup", it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as RedForgeApplication
        setContent {
            val settings by app.settingsDataStore.settingsFlow.collectAsState(initial = com.redforge.app.data.datastore.ForgeSettings())
            RedForgeTheme(forceDark = settings.darkThemeForced) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    var introFinished by remember { mutableStateOf(false) }
                    if (!introFinished) {
                        AnvilDropIntro { introFinished = true }
                    } else {
                        RedForgeApp(openWorkoutOnLaunch = intent.getBooleanExtra("start_workout", false))
                    }
                }
            }
        }
    }
}
