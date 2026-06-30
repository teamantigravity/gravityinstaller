package com.teamantigravity.gravityinstaller.di

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.teamantigravity.gravityinstaller.BuildConfig
import com.teamantigravity.gravityinstaller.data.local.AppDatabase
import com.teamantigravity.gravityinstaller.data.remote.PackageDownloadService
import com.teamantigravity.gravityinstaller.data.remote.VirusTotalNotifier
import com.teamantigravity.gravityinstaller.data.remote.VirusTotalService
import com.teamantigravity.gravityinstaller.data.repository.SessionDataRepositoryImpl
import com.teamantigravity.gravityinstaller.domain.repository.SessionDataRepository
import com.teamantigravity.gravityinstaller.presentation.download.DownloadHistoryViewModel
import com.teamantigravity.gravityinstaller.presentation.manage.BackupsViewModel
import com.teamantigravity.gravityinstaller.presentation.manage.permissions.AppPermissionsViewModel
import com.teamantigravity.gravityinstaller.presentation.install.InstallProgressNotifier
import com.teamantigravity.gravityinstaller.presentation.install.InstallViewModel
import com.teamantigravity.gravityinstaller.presentation.setting.SettingViewModel
import com.teamantigravity.gravityinstaller.presentation.sync.SyncViewModel
import com.teamantigravity.gravityinstaller.presentation.manage.ManageViewModel
import com.teamantigravity.gravityinstaller.presentation.manage.logs.UninstallLogsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import timber.log.Timber

val appModule = module {
    // Process-scoped coroutine scope for work that must outlive any single activity/VM —
    // e.g. installs that the user backgrounded from DialogInstallActivity. SupervisorJob so
    // one failed install doesn't poison the scope.
    single<CoroutineScope>(qualifier = org.koin.core.qualifier.named("appScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single { PackageInstaller.getInstance(get()) }
    single { PackageUninstaller.getInstance(get()) }
    factory { (handle: SavedStateHandle) -> SessionDataRepositoryImpl(handle) }
    singleOf(::SessionDataRepositoryImpl) { bind<SessionDataRepository>() }

    // Room
    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "universal_installer.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }
    single { get<AppDatabase>().installHistoryDao() }
    single { get<AppDatabase>().uninstallLogDao() }
    single { get<AppDatabase>().downloadHistoryDao() }

    // Ktor HttpClient. Uploads to VirusTotal can take minutes on slow connections, so we bump
    // the request timeout well past Ktor's default and leave the socket/connect timeouts sane.
    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5 * 60 * 1000L   // 5 minutes — covers 32 MB upload over 3G
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = 5 * 60 * 1000L     // match request timeout for large uploads
            }
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Timber.tag("KtorHttp").d(message)
                        }
                    }
                    // HEADERS logs method/URL/status/headers without dumping request bodies —
                    // keeps multipart APK uploads out of logcat. Bump to LogLevel.BODY when
                    // you need to inspect JSON payloads (VT responses etc.).
                    level = LogLevel.HEADERS
                    sanitizeHeader { header -> header.equals("x-apikey", ignoreCase = true) }
                }
            }
        }
    }
    single { VirusTotalService(get()) }
    single { VirusTotalNotifier(get()) }
    single { PackageDownloadService(get()) }
    single { InstallProgressNotifier(get(), get(), get()) }

    viewModel {
        InstallViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(qualifier = org.koin.core.qualifier.named("appScope")),
        )
    }
    viewModelOf(::ManageViewModel)
    viewModelOf(::BackupsViewModel)
    viewModelOf(::AppPermissionsViewModel)
    viewModelOf(::SettingViewModel)
    viewModelOf(::UninstallLogsViewModel)
    viewModelOf(::DownloadHistoryViewModel)
    viewModelOf(::SyncViewModel)
}