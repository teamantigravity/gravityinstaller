package com.teamantigravity.gravityinstaller.presentation.setting

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.teamantigravity.gravityinstaller.base.BaseActivity
import com.teamantigravity.gravityinstaller.presentation.composable.BottomBar
import com.teamantigravity.gravityinstaller.presentation.composable.BottomBarItem
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingActivity : BaseActivity() {

    private val viewModel: SettingViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            Scaffold(
                bottomBar = { BottomBar(BottomBarItem.Settings) }
            ) { innerPadding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())) {
                    SettingScreen(viewModel = viewModel)
                }
            }
        }
    }
}
