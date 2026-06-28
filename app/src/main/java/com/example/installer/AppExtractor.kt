package com.example.installer

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class InstalledApp(
    val name: String,
    val packageName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val apkPaths: List<String>,
    val splitCount: Int,
    val totalSize: Long,
    val icon: Drawable? = null
)

object AppExtractor {
    private const val TAG = "AppExtractor"

    fun getInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val apps = mutableListOf<InstalledApp>()
        
        val flags = PackageManager.GET_META_DATA
        val installedApplications = pm.getInstalledApplications(flags)
        
        for (appInfo in installedApplications) {
            try {
                val name = appInfo.loadLabel(pm).toString()
                val packageName = appInfo.packageName
                
                var versionName = "Unknown"
                try {
                    val packageInfo = pm.getPackageInfo(packageName, 0)
                    versionName = packageInfo.versionName ?: "N/A"
                } catch (e: Exception) {
                    // Ignore
                }

                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                val paths = mutableListOf<String>()
                paths.add(appInfo.sourceDir)
                appInfo.splitSourceDirs?.forEach { paths.add(it) }

                var totalSize = 0L
                paths.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        totalSize += file.length()
                    }
                }

                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }

                apps.add(
                    InstalledApp(
                        name = name,
                        packageName = packageName,
                        versionName = versionName,
                        isSystemApp = isSystemApp,
                        apkPaths = paths,
                        splitCount = paths.size - 1,
                        totalSize = totalSize,
                        icon = icon
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing app info for package: ${appInfo.packageName}", e)
            }
        }
        
        return apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun extractApp(
        context: Context,
        app: InstalledApp,
        saveToDownloads: Boolean = false,
        onProgress: (String, Float?) -> Unit
    ): Result<File> {
        return try {
            onProgress("Gathering package files...", 0.05f)
            val backupDir = File(context.cacheDir, "backups").apply {
                mkdirs()
            }
            
            // Clean up old backups
            backupDir.listFiles()?.forEach { it.delete() }

            // Safe file name
            val safeAppName = app.name.replace(Regex("[^a-zA-Z0-9]"), "_")
            val fileName = "${safeAppName}_${app.versionName}.apks"
            val backupFile = File(backupDir, fileName)
            
            onProgress("Packaging APK slices into standard APKS format...", 0.1f)
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                app.apkPaths.forEachIndexed { index, path ->
                    val apkFile = File(path)
                    if (apkFile.exists() && apkFile.length() > 0) {
                        // Rename standard split files cleanly, base is base.apk, splits are split_config.xxx.apk
                        val entryName = if (index == 0) "base.apk" else "split_${apkFile.name.substringAfterLast("_")}"
                        
                        val zipEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(zipEntry)
                        
                        val sizeBytes = apkFile.length()
                        val startPercent = 0.1f + (index.toFloat() / app.apkPaths.size.toFloat()) * 0.75f
                        val stepSize = 0.75f / app.apkPaths.size.toFloat()
                        
                        apkFile.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalBytesCopied = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                zipOut.write(buffer, 0, bytesRead)
                                totalBytesCopied += bytesRead
                                val sliceProgress = if (sizeBytes > 0) totalBytesCopied.toFloat() / sizeBytes.toFloat() else 0f
                                val overallProgress = startPercent + sliceProgress * stepSize
                                val pct = (overallProgress * 100).toInt()
                                onProgress("Compressing slice ${index + 1} of ${app.apkPaths.size} (${apkFile.name.substringAfterLast("/")}) — $pct%...", overallProgress)
                            }
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            
            if (backupFile.exists() && backupFile.length() > 0) {
                if (saveToDownloads) {
                    onProgress("Exporting archive to Downloads/GravityInstaller...", 0.90f)
                    val publicFile = saveFileToPublicDownloads(context, backupFile, fileName)
                    if (publicFile != null) {
                        onProgress("Saved successfully to Downloads/GravityInstaller/${fileName}!", 1.0f)
                        Result.success(publicFile)
                    } else {
                        onProgress("Saved locally (Downloads folder access failed)...", 1.0f)
                        Result.success(backupFile)
                    }
                } else {
                    onProgress("Extraction completed successfully!", 1.0f)
                    Result.success(backupFile)
                }
            } else {
                Result.failure(Exception("Failed to generate backup package archive"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract package for ${app.packageName}", e)
            Result.failure(e)
        }
    }

    private fun saveFileToPublicDownloads(context: Context, srcFile: File, displayName: String): File? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GravityInstaller")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        srcFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GravityInstaller/$displayName")
                } else {
                    null
                }
            } else {
                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GravityInstaller")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                val destFile = File(downloadDir, displayName)
                srcFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file to Downloads folder", e)
            null
        }
    }
}
