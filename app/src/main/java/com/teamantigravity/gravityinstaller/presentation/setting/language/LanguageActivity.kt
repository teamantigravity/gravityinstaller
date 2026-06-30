package com.teamantigravity.gravityinstaller.presentation.setting.language

import android.os.Bundle
import com.teamantigravity.gravityinstaller.base.BaseActivity

class LanguageActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            LanguageScreen()
        }
    }
}
