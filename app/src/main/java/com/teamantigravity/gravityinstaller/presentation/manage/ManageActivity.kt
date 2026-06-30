package com.teamantigravity.gravityinstaller.presentation.manage

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

class ManageActivity : BaseActivity() {

    private val viewModel: ManageViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            Scaffold(
                bottomBar = { BottomBar(BottomBarItem.Manage) }
            ) { innerPadding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())) {
                    ManageScreen(viewModel = viewModel)
                }
            }
        }
    }
}
