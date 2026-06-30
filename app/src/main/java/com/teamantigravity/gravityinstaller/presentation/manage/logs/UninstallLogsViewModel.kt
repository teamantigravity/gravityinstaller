package com.teamantigravity.gravityinstaller.presentation.manage.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamantigravity.gravityinstaller.data.local.UninstallLogDao
import com.teamantigravity.gravityinstaller.data.local.UninstallLogEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UninstallLogsViewModel(
    private val uninstallLogDao: UninstallLogDao,
) : ViewModel() {

    val logs: StateFlow<List<UninstallLogEntity>> = uninstallLogDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() {
        viewModelScope.launch { uninstallLogDao.clearAll() }
    }

    fun deleteById(id: Long) {
        viewModelScope.launch { uninstallLogDao.deleteById(id) }
    }
}
