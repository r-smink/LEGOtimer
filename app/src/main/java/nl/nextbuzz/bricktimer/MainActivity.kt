package nl.nextbuzz.bricktimer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nextbuzz.bricktimer.ui.AppViewModel
import nl.nextbuzz.bricktimer.ui.BrickTimerApp
import nl.nextbuzz.bricktimer.ui.SplashScreen
import nl.nextbuzz.bricktimer.ui.awaitSplash
import nl.nextbuzz.bricktimer.ui.theme.BrickTimerTheme
import nl.nextbuzz.bricktimer.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val dark = isDarkTheme(data.themeMode)
            var splash by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                awaitSplash()
                splash = false
            }
            // The splash always has a dark or saturated background, so it gets light status bar icons.
            val lightIcons = dark || splash
            DisposableEffect(lightIcons) {
                val style = if (lightIcons) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            BrickTimerTheme(dark) {
                Crossfade(splash, animationSpec = tween(400), label = "splash") { showSplash ->
                    if (showSplash) SplashScreen(dark) else BrickTimerApp(vm, onExit = { finish() })
                }
            }
        }
    }
}
