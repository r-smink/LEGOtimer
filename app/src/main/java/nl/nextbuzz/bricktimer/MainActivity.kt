package nl.nextbuzz.bricktimer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nextbuzz.bricktimer.ui.AppViewModel
import nl.nextbuzz.bricktimer.ui.BrickTimerApp
import nl.nextbuzz.bricktimer.ui.theme.BrickTimerTheme
import nl.nextbuzz.bricktimer.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val dark = isDarkTheme(data.themeMode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            BrickTimerTheme(dark) {
                BrickTimerApp(vm, onExit = { finish() })
            }
        }
    }
}
