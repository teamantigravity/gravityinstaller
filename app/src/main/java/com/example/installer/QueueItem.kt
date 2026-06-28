package com.example.installer

import android.net.Uri

data class QueueItem(
    val id: String,
    val name: String,
    val uris: List<Uri>,
    val isZip: Boolean,
    val status: String, // "QUEUED", "INSTALLING", "SUCCESS", "FAILED"
    val progress: Float?,
    val progressMessage: String?,
    val errorMessage: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val sizeBytes: Long = 0L
)
