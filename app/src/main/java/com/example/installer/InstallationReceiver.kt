package com.example.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InstallationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.gravityinstaller.ACTION_INSTALL_COMMIT") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No details"
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: "Unknown"
            val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

            Log.d("GravityInstaller", "Install status: $status, message: $message, package: $packageName, session: $sessionId")

            val statusStr = when (status) {
                PackageInstaller.STATUS_SUCCESS -> "SUCCESS"
                else -> "FAILED"
            }

            // Save installation result to database
            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                db.historyDao().insertHistory(
                    HistoryEntity(
                        appName = packageName.substringAfterLast("."),
                        packageName = packageName,
                        version = "N/A",
                        timestamp = System.currentTimeMillis(),
                        sizeBytes = 0L,
                        status = statusStr,
                        operationType = "INSTALL",
                        fileSource = "Session #$sessionId",
                        errorMessage = if (status == PackageInstaller.STATUS_SUCCESS) null else "Code $status: $message"
                    )
                )

                // Broadcast local event to notify active UI
                val updateIntent = Intent("com.example.gravityinstaller.INSTALL_STATUS_UPDATE").apply {
                    putExtra("packageName", packageName)
                    putExtra("status", statusStr)
                    putExtra("message", message)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(updateIntent)
            }
        }
    }
}
