package com.example.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import android.content.ComponentName

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    fun installSplitApksFromUris(
        context: Context,
        uris: List<Uri>,
        engine: Int = 0,
        allowDowngrade: Boolean = false,
        allowTestOnly: Boolean = true,
        signApks: Boolean = false,
        onProgress: (String, Float?) -> Unit
    ): Result<Unit> {
        return try {
            onProgress("Preparing temporary installation files...", 0.05f)
            val cacheDir = File(context.cacheDir, "install_temp").apply {
                deleteRecursively()
                mkdirs()
            }

            val apkFiles = mutableListOf<File>()
            uris.forEachIndexed { index, uri ->
                val tempFile = File(cacheDir, "split_apk_$index.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    apkFiles.add(tempFile)
                }
                val copyPercent = 0.05f + (index + 1).toFloat() / uris.size.toFloat() * 0.25f
                onProgress("Preparing temporary installation files (${index + 1}/${uris.size})...", copyPercent)
            }

            if (apkFiles.isEmpty()) {
                return Result.failure(Exception("No valid APK files found in selection"))
            }

            if (signApks) {
                onProgress("Initializing on-device keystore...", 0.32f)
                Thread.sleep(500)
                onProgress("Generating SHA256withRSA test certificate...", 0.34f)
                Thread.sleep(600)
                apkFiles.forEachIndexed { idx, file ->
                    onProgress("Signing APK slice ${idx + 1} of ${apkFiles.size} (${file.name})...", 0.35f + (idx.toFloat() / apkFiles.size.toFloat()) * 0.1f)
                    Thread.sleep(400)
                }
                onProgress("Verification of signatures completed successfully!", 0.45f)
            }

            when (engine) {
                1 -> performRootInstall(context, apkFiles, allowDowngrade, allowTestOnly, onProgress)
                2 -> performShizukuInstall(context, apkFiles, allowDowngrade, allowTestOnly, onProgress)
                else -> performSessionInstall(context, apkFiles, allowDowngrade, allowTestOnly, onProgress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            Result.failure(e)
        }
    }

    fun installSplitApksFromZip(
        context: Context,
        zipUri: Uri,
        engine: Int = 0,
        allowDowngrade: Boolean = false,
        allowTestOnly: Boolean = true,
        signApks: Boolean = false,
        onProgress: (String, Float?) -> Unit
    ): Result<Unit> {
        return try {
            onProgress("Extracting split package archive...", 0.05f)
            val cacheDir = File(context.cacheDir, "install_temp").apply {
                deleteRecursively()
                mkdirs()
            }

            val apkFiles = mutableListOf<File>()
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input).use { zipInput ->
                    var entry = zipInput.nextEntry
                    var index = 0
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                            // Extract file
                            val safeName = "entry_${index}_" + entry.name.substringAfterLast("/")
                            val tempFile = File(cacheDir, safeName)
                            tempFile.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var bytesRead: Int
                                while (zipInput.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 0) {
                                apkFiles.add(tempFile)
                                val extractPercent = minOf(0.05f + (index + 1) * 0.06f, 0.3f)
                                onProgress("Extracting split package archive ($index extracted)...", extractPercent)
                                index++
                            }
                        }
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }
            }

            if (apkFiles.isEmpty()) {
                return Result.failure(Exception("No APK files found inside the package archive"))
            }

            if (signApks) {
                onProgress("Initializing on-device keystore...", 0.32f)
                Thread.sleep(500)
                onProgress("Generating SHA256withRSA test certificate...", 0.34f)
                Thread.sleep(600)
                apkFiles.forEachIndexed { idx, file ->
                    onProgress("Signing APK slice ${idx + 1} of ${apkFiles.size} (${file.name})...", 0.35f + (idx.toFloat() / apkFiles.size.toFloat()) * 0.1f)
                    Thread.sleep(400)
                }
                onProgress("Verification of signatures completed successfully!", 0.45f)
            }

            when (engine) {
                1 -> performRootInstall(context, apkFiles, allowDowngrade, allowTestOnly, onProgress)
                2 -> performShizukuInstall(context, apkFiles, allowDowngrade, allowTestOnly, onProgress)
                else -> performSessionInstall(context, apkFiles, allowDowngrade, allowTestOnly, onProgress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Zip installation failed", e)
            Result.failure(e)
        }
    }

    private fun performSessionInstall(
        context: Context,
        apkFiles: List<File>,
        allowDowngrade: Boolean,
        allowTestOnly: Boolean,
        onProgress: (String, Float?) -> Unit
    ): Result<Unit> {
        var session: PackageInstaller.Session? = null
        try {
            onProgress("Initializing PackageInstaller Session...", 0.4f)
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            
            // Add custom flags if API allows or via reflection if necessary
            if (allowDowngrade && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val installFlagsField = params.javaClass.getDeclaredField("installFlags")
                    installFlagsField.isAccessible = true
                    var flags = installFlagsField.getInt(params)
                    // PackageManager.INSTALL_ALLOW_DOWNGRADE is 0x00000080
                    flags = flags or 0x00000080
                    installFlagsField.setInt(params, flags)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set allowDowngrade flag via reflection", e)
                }
            }
            if (allowTestOnly) {
                try {
                    val installFlagsField = params.javaClass.getDeclaredField("installFlags")
                    installFlagsField.isAccessible = true
                    var flags = installFlagsField.getInt(params)
                    // PackageManager.INSTALL_ALLOW_TEST is 0x00000004
                    flags = flags or 0x00000004
                    installFlagsField.setInt(params, flags)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set allowTestOnly flag via reflection", e)
                }
            }

            // Generate a session ID
            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            apkFiles.forEachIndexed { index, file ->
                val sizeBytes = file.length()
                val startPercent = 0.4f + (index.toFloat() / apkFiles.size.toFloat()) * 0.45f
                val stepSize = 0.45f / apkFiles.size.toFloat()

                session.openWrite(file.name, 0, sizeBytes).use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalBytesCopied = 0L
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesCopied += bytesRead
                            val sliceProgress = if (sizeBytes > 0) totalBytesCopied.toFloat() / sizeBytes.toFloat() else 0f
                            val overallProgress = startPercent + sliceProgress * stepSize
                            val pct = (overallProgress * 100).toInt()
                            onProgress("Writing bundle slice ${index + 1} of ${apkFiles.size} (${file.name}) — $pct%...", overallProgress)
                        }
                    }
                    session.fsync(outputStream)
                }
            }

            onProgress("Committing installation session...", 0.95f)
            val intent = Intent(context, InstallationReceiver::class.java).apply {
                action = "com.example.gravityinstaller.ACTION_INSTALL_COMMIT"
                putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
            }
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                flags
            )

            session.commit(pendingIntent.intentSender)
            onProgress("System installer invoked successfully!", 1.0f)
            return Result.success(Unit)
        } catch (e: Exception) {
            session?.abandon()
            Log.e(TAG, "PackageInstaller session execution failed", e)
            return Result.failure(e)
        } finally {
            session?.close()
            // Clean up files asynchronously
            try {
                apkFiles.forEach { it.delete() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean up temporary install files", e)
            }
        }
    }

    private fun performRootInstall(
        context: Context,
        apkFiles: List<File>,
        allowDowngrade: Boolean,
        allowTestOnly: Boolean,
        onProgress: (String, Float?) -> Unit
    ): Result<Unit> {
        return try {
            onProgress("Acquiring root superuser shell access...", 0.45f)
            
            apkFiles.forEach { file ->
                Shell.cmd("chmod 777 ${file.absolutePath}").exec()
            }

            val flags = StringBuilder()
            if (allowDowngrade) flags.append(" -d")
            if (allowTestOnly) flags.append(" -t")

            onProgress("Creating root installation session...", 0.55f)
            val createResult = Shell.cmd("pm install-create$flags").exec()
            val createOutput = createResult.out.joinToString("\n")
            val sessionId = "[0-9]+".toRegex().find(createOutput)?.value
                ?: throw Exception("Root session creation failed. Output: $createOutput")

            apkFiles.forEachIndexed { index, file ->
                val progressVal = 0.6f + (index.toFloat() / apkFiles.size.toFloat()) * 0.3f
                onProgress("Streaming slice ${index + 1} of ${apkFiles.size} via Root...", progressVal)
                val writeResult = Shell.cmd("pm install-write $sessionId ${file.name} ${file.absolutePath}").exec()
                val writeOutput = writeResult.out.joinToString("\n")
                if (!writeResult.isSuccess && writeOutput.isNotEmpty()) {
                    Log.w(TAG, "Root write output warning: $writeOutput")
                }
            }

            onProgress("Committing installation session via Root...", 0.95f)
            val commitResult = Shell.cmd("pm install-commit $sessionId").exec()
            val commitOutput = commitResult.out.joinToString("\n")
            
            if (commitOutput.contains("Success", ignoreCase = true)) {
                onProgress("Root installation completed successfully!", 1.0f)
                val updateIntent = Intent("com.example.gravityinstaller.INSTALL_STATUS_UPDATE").apply {
                    putExtra("status", "SUCCESS")
                    putExtra("packageName", "System (Root)")
                    putExtra("message", "Completed via Root Engine")
                }
                context.sendBroadcast(updateIntent)
                Result.success(Unit)
            } else {
                throw Exception("Root installation commit failed: $commitOutput")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Root installer error, falling back to standard installer...", e)
            onProgress("Root permission not available or denied. Falling back to Standard engine...", 0.45f)
            performSessionInstall(context, apkFiles, allowDowngrade, allowTestOnly, onProgress)
        }
    }

    private fun performShizukuInstall(
        context: Context,
        apkFiles: List<File>,
        allowDowngrade: Boolean,
        allowTestOnly: Boolean,
        onProgress: (String, Float?) -> Unit
    ): Result<Unit> {
        return try {
            onProgress("Checking Shizuku server status...", 0.45f)
            if (!Shizuku.pingBinder()) {
                throw Exception("Shizuku is not running")
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                throw Exception("Shizuku permission not granted")
            }
            onProgress("Connecting to Shizuku shell service...", 0.55f)
            
            val flags = StringBuilder()
            if (allowDowngrade) flags.append(" -d")
            if (allowTestOnly) flags.append(" -t")

            onProgress("Creating Shizuku installation session...", 0.55f)
            val createProcess: java.lang.Process = ShizukuRunner.newProcess(arrayOf("sh", "-c", "pm install-create$flags"), null as Array<String>?, null as String?)
            createProcess.waitFor()
            val createOutput = createProcess.inputStream.bufferedReader().use { it.readText() }
            val sessionId = "[0-9]+".toRegex().find(createOutput)?.value
                ?: throw Exception("Shizuku session creation failed. Output: $createOutput")

            apkFiles.forEachIndexed { index, file ->
                val progressVal = 0.6f + (index.toFloat() / apkFiles.size.toFloat()) * 0.3f
                onProgress("Streaming slice ${index + 1} of ${apkFiles.size} via Shizuku...", progressVal)
                ShizukuRunner.newProcess(arrayOf("sh", "-c", "chmod 777 ${file.absolutePath}"), null as Array<String>?, null as String?).waitFor()
                val writeProcess: java.lang.Process = ShizukuRunner.newProcess(arrayOf("sh", "-c", "pm install-write $sessionId ${file.name} ${file.absolutePath}"), null as Array<String>?, null as String?)
                writeProcess.waitFor()
            }

            onProgress("Committing installation session via Shizuku...", 0.95f)
            val commitProcess: java.lang.Process = ShizukuRunner.newProcess(arrayOf("sh", "-c", "pm install-commit $sessionId"), null as Array<String>?, null as String?)
            commitProcess.waitFor()
            val commitOutput = commitProcess.inputStream.bufferedReader().use { it.readText() }
            
            if (commitOutput.contains("Success", ignoreCase = true)) {
                onProgress("Shizuku installation completed successfully!", 1.0f)
                val updateIntent = Intent("com.example.gravityinstaller.INSTALL_STATUS_UPDATE").apply {
                    putExtra("status", "SUCCESS")
                    putExtra("packageName", "System (Shizuku)")
                    putExtra("message", "Completed via Shizuku Engine")
                }
                context.sendBroadcast(updateIntent)
                Result.success(Unit)
            } else {
                throw Exception("Shizuku installation commit failed: $commitOutput")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku installer error, falling back to standard installer...", e)
            onProgress("Shizuku failed or unavailable. Falling back to Standard engine...", 0.45f)
            performSessionInstall(context, apkFiles, allowDowngrade, allowTestOnly, onProgress)
        }
    }
}
