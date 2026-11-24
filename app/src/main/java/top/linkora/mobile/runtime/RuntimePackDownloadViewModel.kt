package top.linkora.mobile.runtime

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RuntimeDownloadPhase {
    Unavailable,
    Idle,
    Downloading,
    ReadyToInstall,
    Installing,
    Failed,
}

data class RuntimeDownloadState(
    val artifact: RuntimePackArtifact,
    val phase: RuntimeDownloadPhase = RuntimeDownloadPhase.Idle,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = artifact.estimatedBytes,
    val message: String = "Not downloaded",
) {
    val progress: Float?
        get() = totalBytes.takeIf { it > 0L }
            ?.let { (downloadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
}

data class RuntimeDownloadUiState(
    val downloads: Map<String, RuntimeDownloadState> = RuntimePackCatalog.releases.associate {
        val artifact = RuntimePackCatalog.deviceEntry(it)
        it.spec.id to RuntimeDownloadState(
            artifact = artifact,
            phase = if (artifact.downloadUrl.isBlank()) {
                RuntimeDownloadPhase.Unavailable
            } else {
                RuntimeDownloadPhase.Idle
            },
            message = if (artifact.downloadUrl.isBlank()) {
                "This runtime is not available for ${artifact.abi} yet."
            } else {
                "Not downloaded"
            },
        )
    },
) {
    operator fun get(runtimeId: String): RuntimeDownloadState =
        checkNotNull(downloads[runtimeId])
}

class RuntimePackDownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val downloadManager = application.getSystemService(DownloadManager::class.java)
    private val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val validator = RuntimePackManager(application)
    private val _uiState = MutableStateFlow(RuntimeDownloadUiState())
    val uiState = _uiState.asStateFlow()
    private val _installRequests = Channel<String>(Channel.BUFFERED)
    val installRequests = _installRequests.receiveAsFlow()
    private var monitorJob: Job? = null

    init {
        refresh()
    }

    fun download(runtimeId: String) {
        val artifact = RuntimePackCatalog.resolve(runtimeId) ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    removeDownloadRecord(artifact)
                    val destination = installerFile(artifact)
                    val downloadDirectory = checkNotNull(destination.parentFile)
                    if (!downloadDirectory.isDirectory) {
                        check(downloadDirectory.mkdirs() || downloadDirectory.isDirectory) {
                            "The runtime download directory could not be created."
                        }
                    }
                    if (destination.exists()) check(destination.delete()) {
                        "The old runtime installer could not be replaced."
                    }
                    val request = DownloadManager.Request(Uri.parse(artifact.downloadUrl))
                        .setTitle(artifact.spec.displayName)
                        .setDescription("Downloading ${artifact.spec.runtimeVersion}")
                        .setMimeType(APK_MIME_TYPE)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                        )
                        .setDestinationInExternalFilesDir(
                            getApplication(),
                            Environment.DIRECTORY_DOWNLOADS,
                            "runtime-packs/${artifact.fileName}",
                        )
                    val downloadId = downloadManager.enqueue(request)
                    preferences.edit().putLong(downloadKey(artifact), downloadId).apply()
                }
            }.onSuccess {
                update(
                    artifact,
                    RuntimeDownloadState(
                        artifact = artifact,
                        phase = RuntimeDownloadPhase.Downloading,
                        message = "Starting download...",
                    ),
                )
                ensureMonitor()
            }.onFailure { error ->
                fail(artifact, error.message ?: "The runtime download could not start.")
            }
        }
    }

    fun cancel(runtimeId: String) {
        val artifact = RuntimePackCatalog.resolve(runtimeId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            removeDownloadRecord(artifact)
            installerFile(artifact).delete()
            update(artifact, RuntimeDownloadState(artifact))
        }
    }

    fun removeInstaller(runtimeId: String) = cancel(runtimeId)

    fun createInstallIntent(runtimeId: String): Intent? {
        val artifact = RuntimePackCatalog.resolve(runtimeId) ?: return null
        val archive = installerFile(artifact)
        return runCatching {
            validator.validateArchive(archive, artifact.spec, artifact.abi)
            update(
                artifact,
                _uiState.value[runtimeId].copy(
                    phase = RuntimeDownloadPhase.Installing,
                    message = "Waiting for Android installation approval...",
                ),
            )
            val uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.runtime-files",
                archive,
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrElse { error ->
            fail(artifact, error.message ?: "The runtime installer is invalid.")
            null
        }
    }

    fun refresh() {
        viewModelScope.launch {
            RuntimePackCatalog.releases.mapNotNull { RuntimePackCatalog.resolve(it.spec.id) }
                .forEach { artifact ->
                withContext(Dispatchers.IO) { refreshArtifact(artifact) }
            }
            ensureMonitor()
        }
    }

    private fun ensureMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = viewModelScope.launch {
            while (_uiState.value.downloads.values.any {
                    it.phase == RuntimeDownloadPhase.Downloading
                }
            ) {
                RuntimePackCatalog.releases.mapNotNull { RuntimePackCatalog.resolve(it.spec.id) }
                    .forEach { artifact ->
                    if (_uiState.value[artifact.spec.id].phase == RuntimeDownloadPhase.Downloading) {
                        withContext(Dispatchers.IO) { refreshArtifact(artifact) }
                    }
                }
                delay(PROGRESS_REFRESH_MS)
            }
        }
    }

    private fun refreshArtifact(artifact: RuntimePackArtifact) {
        val downloadId = preferences.getLong(downloadKey(artifact), NO_DOWNLOAD)
        if (downloadId == NO_DOWNLOAD) {
            restoreInstallerOrIdle(artifact)
            return
        }

        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                preferences.edit().remove(downloadKey(artifact)).apply()
                restoreInstallerOrIdle(artifact)
                return
            }
            val status = cursor.longColumn(DownloadManager.COLUMN_STATUS).toInt()
            val downloaded = cursor.longColumn(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                .coerceAtLeast(0L)
            val reportedTotal = cursor.longColumn(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val total = reportedTotal.takeIf { it > 0L } ?: artifact.estimatedBytes
            when (status) {
                DownloadManager.STATUS_PENDING -> update(
                    artifact,
                    RuntimeDownloadState(
                        artifact,
                        RuntimeDownloadPhase.Downloading,
                        downloaded,
                        total,
                        "Waiting for Android Download Manager...",
                    ),
                )
                DownloadManager.STATUS_RUNNING -> update(
                    artifact,
                    RuntimeDownloadState(
                        artifact,
                        RuntimeDownloadPhase.Downloading,
                        downloaded,
                        total,
                        "Downloading ${formatBytes(downloaded)} of ${formatBytes(total)}",
                    ),
                )
                DownloadManager.STATUS_PAUSED -> update(
                    artifact,
                    RuntimeDownloadState(
                        artifact,
                        RuntimeDownloadPhase.Downloading,
                        downloaded,
                        total,
                        "Download paused by Android. It will resume automatically.",
                    ),
                )
                DownloadManager.STATUS_SUCCESSFUL -> validateInstaller(
                    artifact = artifact,
                    requestInstall = true,
                )
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.longColumn(DownloadManager.COLUMN_REASON).toInt()
                    removeDownloadRecord(artifact)
                    installerFile(artifact).delete()
                    fail(artifact, downloadFailureMessage(reason))
                }
            }
        }
    }

    private fun restoreInstallerOrIdle(artifact: RuntimePackArtifact) {
        if (installerFile(artifact).isFile) {
            validateInstaller(artifact, requestInstall = false)
        }
        else update(artifact, RuntimeDownloadState(artifact))
    }

    private fun validateInstaller(
        artifact: RuntimePackArtifact,
        requestInstall: Boolean,
    ) {
        runCatching {
            validator.validateArchive(installerFile(artifact), artifact.spec, artifact.abi)
        }.onSuccess {
            val fileSize = installerFile(artifact).length()
            preferences.edit().remove(downloadKey(artifact)).apply()
            update(
                artifact,
                RuntimeDownloadState(
                    artifact = artifact,
                    phase = RuntimeDownloadPhase.ReadyToInstall,
                    downloadedBytes = fileSize,
                    totalBytes = fileSize,
                    message = "Download verified. Android will ask before installing it.",
                ),
            )
            if (requestInstall) _installRequests.trySend(artifact.spec.id)
        }.onFailure { error ->
            removeDownloadRecord(artifact)
            installerFile(artifact).delete()
            fail(artifact, error.message ?: "The downloaded runtime APK is invalid.")
        }
    }

    private fun removeDownloadRecord(artifact: RuntimePackArtifact) {
        val downloadId = preferences.getLong(downloadKey(artifact), NO_DOWNLOAD)
        if (downloadId != NO_DOWNLOAD) downloadManager.remove(downloadId)
        preferences.edit().remove(downloadKey(artifact)).apply()
    }

    private fun installerFile(artifact: RuntimePackArtifact): File {
        val downloads = checkNotNull(
            getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        ) { "Android external app storage is unavailable." }
        return File(File(downloads, "runtime-packs"), artifact.fileName)
    }

    private fun fail(artifact: RuntimePackArtifact, message: String) {
        update(
            artifact,
            _uiState.value[artifact.spec.id].copy(
                phase = RuntimeDownloadPhase.Failed,
                message = message,
            ),
        )
    }

    private fun update(artifact: RuntimePackArtifact, state: RuntimeDownloadState) {
        _uiState.value = _uiState.value.copy(
            downloads = _uiState.value.downloads + (artifact.spec.id to state),
        )
    }

    private fun android.database.Cursor.longColumn(name: String): Long =
        getLong(getColumnIndexOrThrow(name))

    private fun downloadFailureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Android could not resume this download. Try again."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage is unavailable."
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "An installer with this name already exists."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "The download server returned incomplete data."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "There is not enough storage for this runtime."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The download server redirected too many times."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The runtime is not available from the download server yet."
        else -> "The runtime file is unavailable or the connection failed. Try again later."
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }

    private fun downloadKey(artifact: RuntimePackArtifact) = "download_${artifact.spec.id}"

    companion object {
        private const val PREFERENCES = "runtime_pack_downloads"
        private const val NO_DOWNLOAD = -1L
        private const val PROGRESS_REFRESH_MS = 750L
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
