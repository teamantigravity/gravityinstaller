package com.example.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileInputStream

class ShizukuUserService(private val context: Context) {
    
    companion object {
        private const val TAG = "ShizukuUserService"
    }

    fun installApks(
        apkFiles: List<File>,
        allowDowngrade: Boolean,
        allowTestOnly: Boolean,
        sessionIdCallback: (Int) -> Unit
    ) {
        var session: PackageInstaller.Session? = null
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            
            if (allowDowngrade && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val installFlagsField = params.javaClass.getDeclaredField("installFlags")
                    installFlagsField.isAccessible = true
                    var flags = installFlagsField.getInt(params)
                    flags = flags or 0x00000080
                    installFlagsField.setInt(params, flags)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set allowDowngrade", e)
                }
            }
            if (allowTestOnly) {
                try {
                    val installFlagsField = params.javaClass.getDeclaredField("installFlags")
                    installFlagsField.isAccessible = true
                    var flags = installFlagsField.getInt(params)
                    flags = flags or 0x00000004
                    installFlagsField.setInt(params, flags)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set allowTestOnly", e)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            sessionIdCallback(sessionId)
            session = packageInstaller.openSession(sessionId)

            apkFiles.forEachIndexed { index, file ->
                session.openWrite(file.name, 0, file.length()).use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    session.fsync(outputStream)
                }
            }

            val intent = Intent(context, InstallationReceiver::class.java).apply {
                action = "com.example.gravityinstaller.ACTION_INSTALL_COMMIT"
                putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
            }
            
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                pendingFlags
            )

            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session?.abandon()
            Log.e(TAG, "Shizuku session execution failed", e)
        } finally {
            session?.close()
        }
    }
}
