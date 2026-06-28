package com.example

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.installer.AppExtractor
import com.example.installer.ApkInstaller
import com.example.installer.InstalledApp
import com.example.installer.ApkMetadata
import com.example.installer.QueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface AppListUiState {
    object Loading : AppListUiState
    data class Success(val apps: List<InstalledApp>) : AppListUiState
    data class Error(val message: String) : AppListUiState
}

data class InAppNotification(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val durationMs: Long = 3500L
)

enum class NotificationType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

class MainViewModel(private val context: Context) : ViewModel() {
    private val database = AppDatabase.getDatabase(context)
    private val historyDao = database.historyDao()

    // Flow trigger to show AdMob Interstitial Ads in UI
    private val _showInterstitialTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showInterstitialTrigger: SharedFlow<Unit> = _showInterstitialTrigger.asSharedFlow()

    // Premium Toast/Notification State
    private val _toastNotification = MutableStateFlow<InAppNotification?>(null)
    val toastNotification: StateFlow<InAppNotification?> = _toastNotification.asStateFlow()

    fun showToast(message: String, type: NotificationType = NotificationType.INFO) {
        viewModelScope.launch {
            val notification = InAppNotification(message = message, type = type)
            _toastNotification.value = notification
            kotlinx.coroutines.delay(notification.durationMs)
            if (_toastNotification.value?.id == notification.id) {
                _toastNotification.value = null
            }
        }
    }

    // Theme Switcher State
    private val sharedPrefs = context.getSharedPreferences("gravity_installer_prefs", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(sharedPrefs.getInt("theme_mode", 0)) // 0: System, 1: Light, 2: Dark
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        sharedPrefs.edit().putInt("theme_mode", mode).apply()
    }

    // SAI-inspired persistent settings state flows
    private val _installerEngine = MutableStateFlow(sharedPrefs.getInt("installer_engine", 0)) // 0: Standard, 1: Root, 2: Shizuku
    val installerEngine: StateFlow<Int> = _installerEngine.asStateFlow()

    private val _allowDowngrade = MutableStateFlow(sharedPrefs.getBoolean("allow_downgrade", false))
    val allowDowngrade: StateFlow<Boolean> = _allowDowngrade.asStateFlow()

    private val _allowTestOnly = MutableStateFlow(sharedPrefs.getBoolean("allow_test_only", true))
    val allowTestOnly: StateFlow<Boolean> = _allowTestOnly.asStateFlow()

    private val _signApks = MutableStateFlow(sharedPrefs.getBoolean("sign_apks", false))
    val signApks: StateFlow<Boolean> = _signApks.asStateFlow()

    private val _saveToDownloads = MutableStateFlow(sharedPrefs.getBoolean("save_to_downloads", false))
    val saveToDownloads: StateFlow<Boolean> = _saveToDownloads.asStateFlow()

    fun setInstallerEngine(engine: Int) {
        _installerEngine.value = engine
        sharedPrefs.edit().putInt("installer_engine", engine).apply()
    }

    fun setAllowDowngrade(allow: Boolean) {
        _allowDowngrade.value = allow
        sharedPrefs.edit().putBoolean("allow_downgrade", allow).apply()
    }

    fun setAllowTestOnly(allow: Boolean) {
        _allowTestOnly.value = allow
        sharedPrefs.edit().putBoolean("allow_test_only", allow).apply()
    }

    fun setSignApks(sign: Boolean) {
        _signApks.value = sign
        sharedPrefs.edit().putBoolean("sign_apks", sign).apply()
    }

    fun setSaveToDownloads(save: Boolean) {
        _saveToDownloads.value = save
        sharedPrefs.edit().putBoolean("save_to_downloads", save).apply()
    }

    // Multi-select / Batch Backup state
    private val _selectedBackupApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedBackupApps: StateFlow<Set<String>> = _selectedBackupApps.asStateFlow()

    private val _isBackupMultiSelectActive = MutableStateFlow(false)
    val isBackupMultiSelectActive: StateFlow<Boolean> = _isBackupMultiSelectActive.asStateFlow()

    fun toggleBackupAppSelection(packageName: String) {
        val current = _selectedBackupApps.value
        _selectedBackupApps.value = if (current.contains(packageName)) {
            current - packageName
        } else {
            current + packageName
        }
    }

    fun setBackupMultiSelectActive(active: Boolean) {
        _isBackupMultiSelectActive.value = active
        if (!active) {
            _selectedBackupApps.value = emptySet()
        }
    }

    fun clearBackupSelection() {
        _selectedBackupApps.value = emptySet()
    }

    // Installed Apps State
    private val _rawApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystemApps = MutableStateFlow(sharedPrefs.getBoolean("show_system_apps", false))
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
        sharedPrefs.edit().putBoolean("show_system_apps", show).apply()
    }

    val appListState: StateFlow<AppListUiState> = combine(
        _rawApps, _searchQuery, _showSystemApps
    ) { apps, query, showSystem ->
        if (apps.isEmpty()) {
            AppListUiState.Loading
        } else {
            val filtered = apps.filter { app ->
                val matchesQuery = app.name.contains(query, ignoreCase = true) || 
                                 app.packageName.contains(query, ignoreCase = true)
                val matchesSystem = showSystem || !app.isSystemApp
                matchesQuery && matchesSystem
            }
            AppListUiState.Success(filtered)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppListUiState.Loading)

    // Pre-install App Info Detail Panel States
    private val _selectedApkMetadata = MutableStateFlow<ApkMetadata?>(null)
    val selectedApkMetadata: StateFlow<ApkMetadata?> = _selectedApkMetadata.asStateFlow()

    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUris: StateFlow<List<Uri>> = _selectedUris.asStateFlow()

    private val _isZipSelection = MutableStateFlow(false)
    val isZipSelection: StateFlow<Boolean> = _isZipSelection.asStateFlow()

    private val _selectedLabel = MutableStateFlow("")
    val selectedLabel: StateFlow<String> = _selectedLabel.asStateFlow()

    private val _parsingMetadata = MutableStateFlow(false)
    val parsingMetadata: StateFlow<Boolean> = _parsingMetadata.asStateFlow()

    // Sequential Queue Management States
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _isProcessingQueue = MutableStateFlow(false)
    val isProcessingQueue: StateFlow<Boolean> = _isProcessingQueue.asStateFlow()

    // Operation Progress State
    private val _progressMessage = MutableStateFlow<String?>(null)
    val progressMessage: StateFlow<String?> = _progressMessage.asStateFlow()

    private val _progressPercent = MutableStateFlow<Float?>(null)
    val progressPercent: StateFlow<Float?> = _progressPercent.asStateFlow()

    private val _operationLoading = MutableStateFlow(false)
    val operationLoading: StateFlow<Boolean> = _operationLoading.asStateFlow()

    // History Log Flow
    val historyLogs: StateFlow<List<HistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App/System States
    private val _canRequestInstalls = MutableStateFlow(false)
    val canRequestInstalls: StateFlow<Boolean> = _canRequestInstalls.asStateFlow()

    // Broadcast Receiver to catch system package install outcome dynamically
    private val installStatusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.gravityinstaller.INSTALL_STATUS_UPDATE") {
                val packageName = intent.getStringExtra("packageName") ?: ""
                val status = intent.getStringExtra("status") ?: "FAILED"
                val message = intent.getStringExtra("message") ?: ""
                handleInstallStatusUpdate(packageName, status, message)
            }
        }
    }

    init {
        refreshInstalledApps()
        checkInstallPermission()

        // Register installer receiver dynamically to update active queue
        val filter = IntentFilter("com.example.gravityinstaller.INSTALL_STATUS_UPDATE")
        androidx.core.content.ContextCompat.registerReceiver(
            context, 
            installStatusReceiver, 
            filter, 
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(installStatusReceiver)
        } catch (e: Exception) {}
    }

    fun selectAndParseApk(uris: List<Uri>, isZip: Boolean, label: String) {
        viewModelScope.launch {
            _parsingMetadata.value = true
            _selectedApkMetadata.value = null
            _selectedUris.value = uris
            _isZipSelection.value = isZip
            _selectedLabel.value = label

            val metadata = withContext(Dispatchers.IO) {
                if (isZip) {
                    ApkMetadata.parseApkMetadataFromZip(context, uris.first())
                } else {
                    ApkMetadata.parseApkMetadataFromUris(context, uris)
                }
            }

            _parsingMetadata.value = false
            _selectedApkMetadata.value = metadata
        }
    }

    fun clearSelectedApk() {
        _selectedApkMetadata.value = null
        _selectedUris.value = emptyList()
        _isZipSelection.value = false
        _selectedLabel.value = ""
    }

    // Sequential Batch Queue Management Functions
    fun addToQueue(name: String, uris: List<Uri>, isZip: Boolean, metadata: ApkMetadata?) {
        val newItem = QueueItem(
            id = java.util.UUID.randomUUID().toString(),
            name = metadata?.label ?: name,
            uris = uris,
            isZip = isZip,
            status = "QUEUED",
            progress = null,
            progressMessage = "In Queue",
            packageName = metadata?.packageName,
            versionName = metadata?.versionName,
            sizeBytes = metadata?.totalSize ?: 0L
        )
        _queue.value = _queue.value + newItem
    }

    fun removeFromQueue(id: String) {
        _queue.value = _queue.value.filter { it.id != id }
    }

    fun clearCompletedQueue() {
        _queue.value = _queue.value.filter { it.status == "QUEUED" || it.status == "INSTALLING" }
    }

    fun clearAllQueue() {
        _queue.value = emptyList()
        _isProcessingQueue.value = false
    }

    fun startQueueProcessing() {
        if (_isProcessingQueue.value) return
        _isProcessingQueue.value = true
        processNextInQueue()
    }

    private fun processNextInQueue() {
        viewModelScope.launch {
            val nextItem = _queue.value.find { it.status == "QUEUED" }
            if (nextItem == null) {
                _isProcessingQueue.value = false
                return@launch
            }

            updateQueueItemStatus(nextItem.id, "INSTALLING", 0.05f, "Preparing cache...")

            val result = withContext(Dispatchers.IO) {
                if (nextItem.isZip) {
                    ApkInstaller.installSplitApksFromZip(
                        context = context,
                        zipUri = nextItem.uris.first(),
                        engine = _installerEngine.value,
                        allowDowngrade = _allowDowngrade.value,
                        allowTestOnly = _allowTestOnly.value,
                        signApks = _signApks.value
                    ) { progressMsg, pct ->
                        updateQueueItemStatus(nextItem.id, "INSTALLING", pct, progressMsg)
                    }
                } else {
                    ApkInstaller.installSplitApksFromUris(
                        context = context,
                        uris = nextItem.uris,
                        engine = _installerEngine.value,
                        allowDowngrade = _allowDowngrade.value,
                        allowTestOnly = _allowTestOnly.value,
                        signApks = _signApks.value
                    ) { progressMsg, pct ->
                        updateQueueItemStatus(nextItem.id, "INSTALLING", pct, progressMsg)
                    }
                }
            }

            result.fold(
                onSuccess = {
                    updateQueueItemStatus(nextItem.id, "INSTALLING", 0.95f, "Awaiting system confirmation...")
                },
                onFailure = { error ->
                    updateQueueItemStatus(nextItem.id, "FAILED", null, null, error.message ?: "Unknown error")
                    saveFailedInstallHistory(nextItem.name, error.message ?: "Unknown error")
                    // Since it failed to trigger installer, move to next item
                    processNextInQueue()
                }
            )
        }
    }

    private fun updateQueueItemStatus(
        id: String,
        status: String,
        progress: Float?,
        progressMessage: String?,
        error: String? = null
    ) {
        _queue.value = _queue.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = status,
                    progress = progress,
                    progressMessage = progressMessage,
                    errorMessage = error
                )
            } else {
                item
            }
        }
    }

    private fun handleInstallStatusUpdate(packageName: String, status: String, message: String) {
        viewModelScope.launch {
            val installingItem = _queue.value.find { it.status == "INSTALLING" }
            if (installingItem != null) {
                updateQueueItemStatus(
                    id = installingItem.id,
                    status = status,
                    progress = if (status == "SUCCESS") 1f else null,
                    progressMessage = if (status == "SUCCESS") "Completed" else "Failed",
                    error = if (status == "FAILED") message else null
                )

                if (_isProcessingQueue.value) {
                    processNextInQueue()
                }
            }

            // Direct (single) installation status tracking
            if (_operationLoading.value) {
                if (status == "SUCCESS") {
                    _progressMessage.value = "Installation completed successfully!"
                    _progressPercent.value = 1.0f
                    showToast("Installation completed successfully!", NotificationType.SUCCESS)
                    _showInterstitialTrigger.tryEmit(Unit)
                    kotlinx.coroutines.delay(1500)
                } else {
                    _progressMessage.value = "Installation failed: $message"
                    _progressPercent.value = null
                    showToast("Installation failed: $message", NotificationType.ERROR)
                    kotlinx.coroutines.delay(3000)
                }
                _operationLoading.value = false
                _progressMessage.value = null
                _progressPercent.value = null
                refreshInstalledApps()
            }
        }
    }

    fun checkInstallPermission() {
        _canRequestInstalls.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apps = AppExtractor.getInstalledApps(context)
                _rawApps.value = apps
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching installed apps", e)
            }
        }
    }


    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Install Split APKs from Multiple Selected URIs
    fun installFromUris(uris: List<Uri>, fileLabel: String) {
        viewModelScope.launch {
            _operationLoading.value = true
            _progressPercent.value = 0f
            _progressMessage.value = "Starting bundle installation..."
            
            val result = withContext(Dispatchers.IO) {
                ApkInstaller.installSplitApksFromUris(
                    context = context,
                    uris = uris,
                    engine = _installerEngine.value,
                    allowDowngrade = _allowDowngrade.value,
                    allowTestOnly = _allowTestOnly.value,
                    signApks = _signApks.value
                ) { progress, pct ->
                    _progressMessage.value = progress
                    _progressPercent.value = pct
                }
            }

            result.fold(
                onSuccess = {
                    _progressMessage.value = "Awaiting system installation confirmation..."
                    _progressPercent.value = 0.95f
                    Log.d("MainViewModel", "Install session successfully started from URIs")
                },
                onFailure = { error ->
                    Log.e("MainViewModel", "URI Install session error", error)
                    saveFailedInstallHistory(fileLabel, error.message ?: "Unknown error")
                    _progressMessage.value = "Trigger failed: ${error.message}"
                    _progressPercent.value = null
                    kotlinx.coroutines.delay(3000)
                    _operationLoading.value = false
                    _progressMessage.value = null
                }
            )
        }
    }

    // Install Split APKs from a single .apks/.xapk/.zip URI
    fun installFromZip(zipUri: Uri, fileLabel: String) {
        viewModelScope.launch {
            _operationLoading.value = true
            _progressPercent.value = 0f
            _progressMessage.value = "Initializing package stream..."

            val result = withContext(Dispatchers.IO) {
                ApkInstaller.installSplitApksFromZip(
                    context = context,
                    zipUri = zipUri,
                    engine = _installerEngine.value,
                    allowDowngrade = _allowDowngrade.value,
                    allowTestOnly = _allowTestOnly.value,
                    signApks = _signApks.value
                ) { progress, pct ->
                    _progressMessage.value = progress
                    _progressPercent.value = pct
                }
            }

            result.fold(
                onSuccess = {
                    _progressMessage.value = "Awaiting system installation confirmation..."
                    _progressPercent.value = 0.95f
                    Log.d("MainViewModel", "Install session successfully started from zip")
                },
                onFailure = { error ->
                    Log.e("MainViewModel", "Zip Install session error", error)
                    saveFailedInstallHistory(fileLabel, error.message ?: "Unknown error")
                    _progressMessage.value = "Trigger failed: ${error.message}"
                    _progressPercent.value = null
                    kotlinx.coroutines.delay(3000)
                    _operationLoading.value = false
                    _progressMessage.value = null
                }
            )
        }
    }

    private fun saveFailedInstallHistory(fileName: String, error: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.insertHistory(
                HistoryEntity(
                    appName = fileName.substringAfterLast("/").substringBeforeLast("."),
                    packageName = "Unknown (Session Aborted)",
                    version = "N/A",
                    timestamp = System.currentTimeMillis(),
                    sizeBytes = 0L,
                    status = "FAILED",
                    operationType = "INSTALL",
                    fileSource = fileName,
                    errorMessage = error
                )
            )
        }
    }

    // Backup and Share Selected Application
    fun backupApp(app: InstalledApp, shareAfterExtraction: Boolean = true, onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            _operationLoading.value = true
            _progressPercent.value = 0f
            _progressMessage.value = "Analyzing target packages..."

            val result = withContext(Dispatchers.IO) {
                AppExtractor.extractApp(
                    context = context,
                    app = app,
                    saveToDownloads = _saveToDownloads.value
                ) { progress, pct ->
                    _progressMessage.value = progress
                    _progressPercent.value = pct
                }
            }

            _operationLoading.value = false
            _progressMessage.value = null
            _progressPercent.value = null

            result.fold(
                onSuccess = { file ->
                    viewModelScope.launch(Dispatchers.IO) {
                        historyDao.insertHistory(
                            HistoryEntity(
                                appName = app.name,
                                packageName = app.packageName,
                                version = app.versionName,
                                timestamp = System.currentTimeMillis(),
                                sizeBytes = file.length(),
                                status = "SUCCESS",
                                operationType = "BACKUP",
                                fileSource = file.name,
                                errorMessage = null
                            )
                        )
                    }

                    if (shareAfterExtraction) {
                        shareFile(file)
                    }
                    onComplete(file)
                },
                onFailure = { error ->
                    viewModelScope.launch(Dispatchers.IO) {
                        historyDao.insertHistory(
                            HistoryEntity(
                                appName = app.name,
                                packageName = app.packageName,
                                version = app.versionName,
                                timestamp = System.currentTimeMillis(),
                                sizeBytes = 0L,
                                status = "FAILED",
                                operationType = "BACKUP",
                                fileSource = "${app.packageName}.apks",
                                errorMessage = error.message ?: "Extraction failed"
                            )
                        )
                    }
                    onComplete(null)
                }
            )
        }
    }

    private fun shareFile(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(shareIntent, "Share Rebranded Installer Package").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error sharing file ${file.name}", e)
        }
    }

    private fun shareMultipleFiles(files: List<File>) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
            
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/octet-stream"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(shareIntent, "Share Extracted Packages").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error sharing multiple files", e)
        }
    }

    fun backupMultipleApps(apps: List<InstalledApp>, shareAfterExtraction: Boolean = false, onComplete: (List<File>) -> Unit) {
        viewModelScope.launch {
            _operationLoading.value = true
            _progressPercent.value = 0f
            val backedUpFiles = mutableListOf<File>()
            val totalApps = apps.size

            for (index in apps.indices) {
                val app = apps[index]
                _progressMessage.value = "Extracting app ${index + 1} of $totalApps: ${app.name}..."
                _progressPercent.value = index.toFloat() / totalApps.toFloat()

                val result = withContext(Dispatchers.IO) {
                    AppExtractor.extractApp(
                        context = context,
                        app = app,
                        saveToDownloads = _saveToDownloads.value
                    ) { progress, pct ->
                        val scaledPct = (index.toFloat() / totalApps.toFloat()) + ((pct ?: 0f) / totalApps.toFloat())
                        _progressMessage.value = "App ${index + 1}/$totalApps: $progress"
                        _progressPercent.value = scaledPct
                    }
                }

                result.fold(
                    onSuccess = { file ->
                        backedUpFiles.add(file)
                        viewModelScope.launch(Dispatchers.IO) {
                            historyDao.insertHistory(
                                HistoryEntity(
                                    appName = app.name,
                                    packageName = app.packageName,
                                    version = app.versionName,
                                    timestamp = System.currentTimeMillis(),
                                    sizeBytes = file.length(),
                                    status = "SUCCESS",
                                    operationType = "BACKUP",
                                    fileSource = file.name,
                                    errorMessage = null
                                )
                            )
                        }
                    },
                    onFailure = { error ->
                        viewModelScope.launch(Dispatchers.IO) {
                            historyDao.insertHistory(
                                HistoryEntity(
                                    appName = app.name,
                                    packageName = app.packageName,
                                    version = app.versionName,
                                    timestamp = System.currentTimeMillis(),
                                    sizeBytes = 0L,
                                    status = "FAILED",
                                    operationType = "BACKUP",
                                    fileSource = "${app.packageName}.apks",
                                    errorMessage = error.message ?: "Extraction failed"
                                )
                            )
                        }
                    }
                )
            }

            _operationLoading.value = false
            _progressMessage.value = null
            _progressPercent.value = null

            if (shareAfterExtraction && backedUpFiles.isNotEmpty()) {
                shareMultipleFiles(backedUpFiles)
            }
            onComplete(backedUpFiles)
        }
    }

    fun handleIncomingIntent(intent: Intent) {
        val action = intent.action ?: return
        val type = intent.type

        viewModelScope.launch {
            try {
                _parsingMetadata.value = true
                _selectedApkMetadata.value = null

                val uris = mutableListOf<Uri>()
                var isZip = false

                if (action == Intent.ACTION_VIEW) {
                    intent.data?.let { uri ->
                        uris.add(uri)
                        val fileName = getFileNameFromUri(context, uri)?.lowercase() ?: ""
                        isZip = fileName.endsWith(".zip") || fileName.endsWith(".apks") || fileName.endsWith(".apkm") || fileName.endsWith(".xapk")
                    }
                } else if (action == Intent.ACTION_SEND) {
                    val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (streamUri != null) {
                        uris.add(streamUri)
                        val fileName = getFileNameFromUri(context, streamUri)?.lowercase() ?: ""
                        isZip = fileName.endsWith(".zip") || fileName.endsWith(".apks") || fileName.endsWith(".apkm") || fileName.endsWith(".xapk")
                    }
                } else if (action == Intent.ACTION_SEND_MULTIPLE) {
                    val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    if (streamUris != null) {
                        uris.addAll(streamUris)
                    }
                }

                if (uris.isNotEmpty()) {
                    _selectedUris.value = uris
                    _isZipSelection.value = isZip
                    val label = if (uris.size == 1) {
                        getFileNameFromUri(context, uris.first()) ?: "Imported File"
                    } else {
                        "${uris.size} Imported Split Slices"
                    }
                    _selectedLabel.value = label

                    val metadata = withContext(Dispatchers.IO) {
                        if (isZip) {
                            ApkMetadata.parseApkMetadataFromZip(context, uris.first())
                        } else {
                            ApkMetadata.parseApkMetadataFromUris(context, uris)
                        }
                    }

                    _parsingMetadata.value = false
                    _selectedApkMetadata.value = metadata
                } else {
                    _parsingMetadata.value = false
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error handling incoming intent", e)
                _parsingMetadata.value = false
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearHistory()
            _showInterstitialTrigger.tryEmit(Unit)
        }
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
