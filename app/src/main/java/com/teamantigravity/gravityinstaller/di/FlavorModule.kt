package com.teamantigravity.gravityinstaller.di

import com.teamantigravity.gravityinstaller.presentation.install.controller.FullInstallerBackendFactory
import com.teamantigravity.gravityinstaller.presentation.install.controller.InstallerBackendFactory
import org.koin.dsl.module

val flavorModule = module {
    single<InstallerBackendFactory> { FullInstallerBackendFactory() }
}
