package com.teamantigravity.gravityinstaller.presentation.setting.diagnostics

import android.os.Bundle
import com.teamantigravity.gravityinstaller.base.BaseActivity

class DiagnosticsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            DiagnosticsScreen()
        }
    }
}
