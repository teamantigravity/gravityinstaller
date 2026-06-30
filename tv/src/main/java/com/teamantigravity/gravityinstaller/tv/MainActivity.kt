package com.teamantigravity.gravityinstaller.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.teamantigravity.core.data.local.SharedPrefsKeys
import com.teamantigravity.core.data.local.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import com.teamantigravity.core.presentation.onboarding.OnboardingScreen
import com.teamantigravity.gravityinstaller.tv.presentation.splash.SplashScreen
import com.teamantigravity.gravityinstaller.tv.ui.theme.UniversalInstallerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

import androidx.datastore.preferences.core.stringPreferencesKey
import com.teamantigravity.core.domain.ThemeMode

import com.teamantigravity.gravityinstaller.tv.util.LocaleHelper

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val themeModeName by dataStore.data
                .map { it[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name }
                .collectAsState(initial = ThemeMode.System.name)
            
            val themeMode = remember(themeModeName) {
                ThemeMode.entries.find { it.name == themeModeName } ?: ThemeMode.System
            }

            UniversalInstallerTheme(themeMode = themeMode) {
                val onboardingCompleted by dataStore.data
                    .map { it[SharedPrefsKeys.ONBOARDING_COMPLETED] ?: false }
                    .collectAsState(initial = null)

                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1800)
                    showSplash = false
                }

                if (onboardingCompleted == null) {
                    // Still loading preferences
                    SplashScreen()
                } else if (onboardingCompleted == false) {
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    OnboardingScreen(onFinish = {
                        scope.launch {
                            dataStore.edit { prefs ->
                                prefs[SharedPrefsKeys.ONBOARDING_COMPLETED] = true
                            }
                        }
                    })
                } else {
                    LaunchedEffect(Unit) {
                        com.teamantigravity.core.receiver.TvReceiver.start(applicationContext)
                    }
                    TvApp()
                    AnimatedVisibility(visible = showSplash, enter = fadeIn(), exit = fadeOut()) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.teamantigravity.core.receiver.TvReceiver.stop()
    }
}
