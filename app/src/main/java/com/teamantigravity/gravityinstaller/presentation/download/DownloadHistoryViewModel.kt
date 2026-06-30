package com.teamantigravity.gravityinstaller.presentation.download

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamantigravity.gravityinstaller.data.local.DownloadHistoryDao
import com.teamantigravity.gravityinstaller.data.local.DownloadHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DownloadHistoryViewModel(
    @Suppress("unused") private val application: Application,
    private val dao: DownloadHistoryDao,
) : ViewModel() {

    val items = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun delete(entry: DownloadHistoryEntity, alsoDeleteFile: Boolean) {
        viewModelScope.launch {
            if (alsoDeleteFile) runCatching { File(entry.filePath).takeIf { it.exists() }?.delete() }
            dao.deleteById(entry.id)
        }
    }

    fun clearAll(alsoDeleteFiles: Boolean) {
        viewModelScope.launch {
            if (alsoDeleteFiles) {
                items.value.forEach { runCatching { File(it.filePath).takeIf { f -> f.exists() }?.delete() } }
            }
            dao.clearAll()
        }
    }

    fun fileExists(entry: DownloadHistoryEntity): Boolean = File(entry.filePath).exists()
}
