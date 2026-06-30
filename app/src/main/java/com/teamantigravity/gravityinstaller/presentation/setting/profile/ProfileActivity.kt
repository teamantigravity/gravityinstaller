package com.teamantigravity.gravityinstaller.presentation.setting.profile

import android.os.Bundle
import com.teamantigravity.gravityinstaller.base.BaseActivity

class ProfileActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            ProfileScreen()
        }
    }
}
