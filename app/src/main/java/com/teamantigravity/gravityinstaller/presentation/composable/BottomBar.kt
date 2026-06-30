package com.teamantigravity.gravityinstaller.presentation.composable

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.teamantigravity.gravityinstaller.R
import com.teamantigravity.gravityinstaller.presentation.install.InstallActivity
import com.teamantigravity.gravityinstaller.presentation.manage.ManageActivity
import com.teamantigravity.gravityinstaller.presentation.setting.SettingActivity
import com.teamantigravity.gravityinstaller.util.extension.disableSceneTransition

enum class BottomBarItem(
    val activityClass: Class<*>,
    val label: Int,
    val icon: ImageVector,
) {
    Install(InstallActivity::class.java, R.string.txt_install, Icons.Rounded.InstallMobile),
    Manage(ManageActivity::class.java, R.string.txt_manage, Icons.Rounded.Apps),
    Settings(SettingActivity::class.java, R.string.txt_setting, Icons.Rounded.Settings)
}

@Composable
fun BottomBar(
    currentTab: BottomBarItem
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.onPrimaryContainer,
        selectedTextColor = colors.primary,
        indicatorColor = colors.primaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
    )
    Column {
        com.teamantigravity.gravityinstaller.ads.BannerAdView()
        NavigationBar {
            BottomBarItem.entries.forEach { destination ->
                val isSelected = currentTab == destination
                NavigationBarItem(
                    selected = isSelected,
                    colors = itemColors,
                    onClick = {
                        if (!isSelected) {
                            com.teamantigravity.gravityinstaller.ads.AdManager.showInterstitialAd(context as android.app.Activity) {
                                val intent = Intent(context, destination.activityClass).apply {
                                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
                                }
                                context.startActivity(intent)
                                (context as? android.app.Activity)?.disableSceneTransition()
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.label)
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        }
    }
}