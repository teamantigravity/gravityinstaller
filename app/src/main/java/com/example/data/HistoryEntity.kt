package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val version: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val status: String, // "SUCCESS", "FAILED", "PENDING"
    val operationType: String, // "INSTALL", "BACKUP"
    val fileSource: String, // Name of the file or path
    val errorMessage: String? = null
)
