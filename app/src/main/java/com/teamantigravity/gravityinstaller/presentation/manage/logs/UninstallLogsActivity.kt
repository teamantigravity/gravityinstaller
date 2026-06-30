package com.teamantigravity.gravityinstaller.presentation.manage.logs

import android.os.Bundle
import com.teamantigravity.gravityinstaller.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class UninstallLogsActivity : BaseActivity() {

    private val viewModel: UninstallLogsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            UninstallLogsScreen(viewModel = viewModel)
        }
    }
}
