package com.teamantigravity.gravityinstaller.tv.presentation.receive

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamantigravity.core.data.ApkMetadataReader
import com.teamantigravity.core.data.DownloadsApkScanner
import com.teamantigravity.core.domain.ApkFile
import com.teamantigravity.core.install.ApkInstaller
import com.teamantigravity.core.receiver.ReceivedApk
import com.teamantigravity.core.receiver.ReceiverStatus
import com.teamantigravity.core.receiver.TvReceiverState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReceiveViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val metadataReader = ApkMetadataReader(context)
    private val installer = ApkInstaller(context)

    val status: StateFlow<ReceiverStatus> = TvReceiverState.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReceiverStatus.Stopped)

    private val _pendingApk = MutableStateFlow<ReceivedApk?>(null)
    val pendingApk: StateFlow<ReceivedApk?> = _pendingApk.asStateFlow()

    private val _downloads = MutableStateFlow<List<ApkFile>>(emptyList())
    val downloads: StateFlow<List<ApkFile>> = _downloads.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _installingLabel = MutableStateFlow<String?>(null)
    val installingLabel: StateFlow<String?> = _installingLabel.asStateFlow()

    private val _installResult = MutableStateFlow<String?>(null)
    val installResult: StateFlow<String?> = _installResult.asStateFlow()

    init {
        viewModelScope.launch {
            TvReceiverState.received.collectLatest { received ->
                _installResult.value = null
                // Extract metadata immediately for received APK
                val metadata = metadataReader.readMetadata(Uri.fromFile(File(received.path)), received.fileName.isBundleName())
                _pendingApk.value = received.copy(metadata = metadata)
            }
        }
    }

    fun scanLocalApks() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val files = withContext(Dispatchers.IO) { DownloadsApkScanner.scan(context) }
            _downloads.value = files
            
            // Optionally load metadata for local files lazily or all at once if small
            // For now, let's load them all to show icons in the list
            val enriched = files.map { file ->
                val meta = metadataReader.readMetadata(Uri.parse(file.uri), file.isBundle)
                file.copy(metadata = meta)
            }
            _downloads.value = enriched
            _isScanning.value = false
        }
    }

    fun install(uri: Uri, isBundle: Boolean, label: String) {
        if (_installingLabel.value != null) return
        viewModelScope.launch {
            _installingLabel.value = label
            _installResult.value = "Installing $label..."
            val result = withContext(Dispatchers.IO) { installer.install(uri, isBundle) }
            _installingLabel.value = null
            _installResult.value = when (result) {
                is ApkInstaller.Result.Success -> "Installed $label ✓"
                is ApkInstaller.Result.Failure -> "Failed: ${result.message}"
            }
        }
    }

    fun dismissPending() {
        _pendingApk.value = null
        _installResult.value = null
    }

    private fun String.isBundleName(): Boolean =
        substringAfterLast('.', "").lowercase() in setOf("apks", "xapk", "apkm", "apk+")
}
