package com.teamantigravity.gravityinstaller.presentation.setting.about

import android.os.Bundle
import com.teamantigravity.gravityinstaller.base.BaseActivity

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            AboutScreen()
        }
    }
}
