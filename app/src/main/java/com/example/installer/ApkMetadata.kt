package com.example.installer

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class ApkMetadata(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val isSplitBundle: Boolean,
    val splitCount: Int,
    val totalSize: Long
) {
    companion object {
        private fun getFileNameFromUri(context: Context, uri: Uri): String? {
            return try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }

        fun parseApkMetadata(context: Context, uri: Uri): ApkMetadata? {
            var tempFile: File? = null
            try {
                tempFile = File(context.cacheDir, "temp_parse_" + System.currentTimeMillis() + ".apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    return null
                }

                val pm = context.packageManager
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.GET_PERMISSIONS)
                }

                if (packageInfo == null) {
                    return null
                }

                val appInfo = packageInfo.applicationInfo
                if (appInfo != null) {
                    appInfo.sourceDir = tempFile.absolutePath
                    appInfo.publicSourceDir = tempFile.absolutePath
                }

                val label = appInfo?.loadLabel(pm)?.toString() ?: packageInfo.packageName ?: "Unknown"
                val packageName = packageInfo.packageName ?: "Unknown"
                val versionName = packageInfo.versionName ?: "1.0"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo?.minSdkVersion ?: 21
                } else {
                    21
                }
                val targetSdk = appInfo?.targetSdkVersion ?: 21
                val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
                val totalSize = tempFile.length()

                return ApkMetadata(
                    label = label,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    permissions = permissions,
                    isSplitBundle = false,
                    splitCount = 1,
                    totalSize = totalSize
                )
            } catch (e: Exception) {
                Log.e("ApkMetadata", "Error parsing APK metadata", e)
                return null
            } finally {
                try {
                    tempFile?.delete()
                } catch (e: Exception) {}
            }
        }

        fun parseApkMetadataFromUris(context: Context, uris: List<Uri>): ApkMetadata? {
            if (uris.isEmpty()) return null
            val baseUri = uris.find { uri ->
                getFileNameFromUri(context, uri)?.contains("base", ignoreCase = true) == true
            } ?: uris.first()

            val singleMeta = parseApkMetadata(context, baseUri) ?: return null

            var totalSize = 0L
            uris.forEach { uri ->
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                        totalSize += fd.length
                    }
                } catch (e: Exception) {
                    totalSize += singleMeta.totalSize
                }
            }

            return singleMeta.copy(
                isSplitBundle = uris.size > 1,
                splitCount = uris.size,
                totalSize = if (totalSize > 0) totalSize else (singleMeta.totalSize * uris.size)
            )
        }

        fun parseApkMetadataFromZip(context: Context, zipUri: Uri): ApkMetadata? {
            var tempFile: File? = null
            try {
                var baseApkEntryName: String? = null
                var totalZipSize = 0L
                try {
                    context.contentResolver.openAssetFileDescriptor(zipUri, "r")?.use { fd ->
                        totalZipSize = fd.length
                    }
                } catch (e: Exception) {}

                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    ZipInputStream(input).use { zipInput ->
                        var entry = zipInput.nextEntry
                        while (entry != null) {
                            if (entry.name.endsWith(".apk", ignoreCase = true)) {
                                if (entry.name.contains("base", ignoreCase = true) || baseApkEntryName == null) {
                                    baseApkEntryName = entry.name
                                }
                            }
                            entry = zipInput.nextEntry
                        }
                    }
                }

                if (baseApkEntryName == null) return null

                tempFile = File(context.cacheDir, "temp_parse_zip_" + System.currentTimeMillis() + ".apk")
                var splitCount = 0
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    ZipInputStream(input).use { zipInput ->
                        var entry = zipInput.nextEntry
                        while (entry != null) {
                            if (entry.name.endsWith(".apk", ignoreCase = true)) {
                                splitCount++
                                if (entry.name == baseApkEntryName) {
                                    FileOutputStream(tempFile).use { output ->
                                        zipInput.copyTo(output)
                                    }
                                }
                            }
                            entry = zipInput.nextEntry
                        }
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    return null
                }

                val pm = context.packageManager
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.GET_PERMISSIONS)
                }

                if (packageInfo == null) {
                    return null
                }

                val appInfo = packageInfo.applicationInfo
                if (appInfo != null) {
                    appInfo.sourceDir = tempFile.absolutePath
                    appInfo.publicSourceDir = tempFile.absolutePath
                }

                val label = appInfo?.loadLabel(pm)?.toString() ?: packageInfo.packageName ?: "Unknown"
                val packageName = packageInfo.packageName ?: "Unknown"
                val versionName = packageInfo.versionName ?: "1.0"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo?.minSdkVersion ?: 21
                } else {
                    21
                }
                val targetSdk = appInfo?.targetSdkVersion ?: 21
                val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

                return ApkMetadata(
                    label = label,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    permissions = permissions,
                    isSplitBundle = splitCount > 1,
                    splitCount = splitCount,
                    totalSize = if (totalZipSize > 0) totalZipSize else tempFile.length()
                )
            } catch (e: Exception) {
                Log.e("ApkMetadata", "Error parsing ZIP metadata", e)
                return null
            } finally {
                try {
                    tempFile?.delete()
                } catch (e: Exception) {}
            }
        }
    }
}
