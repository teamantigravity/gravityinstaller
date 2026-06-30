package com.teamantigravity.gravityinstaller.presentation.setting.profile.edit

import android.os.Bundle
import com.teamantigravity.gravityinstaller.base.BaseActivity

class ProfileEditActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        setContentWithTheme {
            ProfileEditScreen(profileId = profileId)
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }
}
