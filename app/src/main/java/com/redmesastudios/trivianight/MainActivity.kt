package com.redmesastudios.trivianight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import com.redmesastudios.trivianight.ui.Night
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Mode { Solo, Group }

private sealed interface Screen {
    data object Home : Screen
    data object Who : Screen
    data object Scores : Screen
    data class Setup(val mode: Mode) : Screen
    data class Solo(val settings: Settings) : Screen
    data class Group(val settings: Settings) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Records.load(this)
        val prefs = getSharedPreferences("solo", MODE_PRIVATE)
        setContent {
            val bank by produceState<QuestionBank?>(null) {
                value = withContext(Dispatchers.Default) { QuestionBank.load(applicationContext) }
            }
            var screen by remember { mutableStateOf<Screen>(Screen.Home) }
            // The last choices, so "New settings" and the next game start from them.
            var settings by remember { mutableStateOf(Settings()) }
            // Who plays solo, remembered for next time.
            var player by remember { mutableStateOf(prefs.getString("player", null)) }
            val saved by Records.state.collectAsState()

            // The remote does nothing until something has focus. When the window has focus
            // but nothing in it does (back from the background, or a sync dropped the focused
            // row), give the screen a moment to pick its own, then move into it.
            val focusManager = LocalFocusManager.current
            val windowFocused = LocalWindowInfo.current.isWindowFocused
            var anyFocused by remember { mutableStateOf(false) }
            LaunchedEffect(windowFocused, anyFocused) {
                if (!windowFocused || anyFocused) return@LaunchedEffect
                delay(250)
                focusManager.moveFocus(FocusDirection.Next)
            }

            Box(Modifier.fillMaxSize().background(Night).onFocusChanged { anyFocused = it.hasFocus }) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "screen",
                ) { shown ->
                    val loaded = bank
                    when {
                        shown == Screen.Home || loaded == null -> HomeScreen(
                            bank = loaded,
                            onScores = { screen = Screen.Scores },
                        ) { mode -> screen = if (mode == Mode.Solo) Screen.Who else Screen.Setup(mode) }
                        shown == Screen.Who -> WhoScreen(player, onBack = { screen = Screen.Home }) { picked ->
                            player = picked.id
                            prefs.edit().putString("player", picked.id).apply()
                            screen = Screen.Setup(Mode.Solo)
                        }
                        shown == Screen.Scores -> HighScoresScreen(onBack = { screen = Screen.Home })
                        shown is Screen.Setup -> SetupScreen(
                            bank = loaded,
                            mode = shown.mode,
                            initial = settings,
                            onBack = { screen = if (shown.mode == Mode.Solo) Screen.Who else Screen.Home },
                            onStart = { chosen ->
                                settings = chosen
                                screen = if (shown.mode == Mode.Solo) Screen.Solo(chosen) else Screen.Group(chosen)
                            },
                        )
                        shown is Screen.Solo -> SoloGame(loaded, shown.settings, saved.player(player) ?: return@AnimatedContent, onNewSettings = { screen = Screen.Setup(Mode.Solo) }, onExit = { screen = Screen.Home })
                        shown is Screen.Group -> GroupGame(loaded, shown.settings, onNewSettings = { screen = Screen.Setup(Mode.Group) }, onExit = { screen = Screen.Home })
                    }
                }
            }
        }
    }

    // Catch up with the household's other TVs whenever the app comes back to the front.
    override fun onResume() {
        super.onResume()
        Records.sync()
    }
}
