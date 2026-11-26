package top.linkora.mobile.update

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.linkora.mobile.BuildConfig

enum class AppUpdatePhase {
    Idle,
    Checking,
    UpToDate,
    Available,
    Downloading,
    ReadyToInstall,
    Installing,
    Failed,
}

data class AppRelease(
    val versionName: String,
    val title: String,
    val notes: String,
    val assetName: String,
    val downloadUrl: String,
    val sha256: String,
    val assetBytes: Long,
)

data class AppUpdateState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val phase: AppUpdatePhase = AppUpdatePhase.Idle,
    val release: AppRelease? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String = "Check GitHub for a newer release.",
) {
    val progress: Float?
        get() = totalBytes.takeIf { it > 0L }
            ?.let { (downloadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val downloadManager = application.getSystemService(DownloadManager::class.java)
    private val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AppUpdateState())
    val state = _state.asStateFlow()
    private val _installRequests = Channel<Unit>(Channel.BUFFERED)
    val installRequests = _installRequests.receiveAsFlow()
    private var monitorJob: Job? = null

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (_state.value.phase in setOf(
                AppUpdatePhase.Checking,
                AppUpdatePhase.Downloading,
                AppUpdatePhase.Installing,
            )
        ) return
        _state.value = AppUpdateState(
            phase = AppUpdatePhase.Checking,
            message = "Checking the latest GitHub release...",
        )
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadLatestRelease() } }
                .onSuccess { release ->
                    if (AppUpdatePolicy.isNewerVersion(
                            release.versionName,
                            BuildConfig.VERSION_NAME,
                        )
                    ) {
                        _state.value = AppUpdateState(
                            phase = AppUpdatePhase.Available,
                            release = release,
                            totalBytes = release.assetBytes,
                            message = "Linkora ${release.versionName} is available.",
                        )
                        refresh()
                    } else {
                        _state.value = AppUpdateState(
                            phase = AppUpdatePhase.UpToDate,
                            release = release,
                            message = "You have the latest Linkora release.",
                        )
                    }
                }
                .onFailure { error -> fail(error.message ?: "The update check failed.") }
        }
    }

    fun download() {
        val release = _state.value.release ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    removeDownloadRecord()
                    val destination = installerFile(release)
                    val directory = checkNotNull(destination.parentFile)
                    check(directory.isDirectory || directory.mkdirs()) {
                        "The update download directory could not be created."
                    }
                    if (destination.exists()) check(destination.delete()) {
                        "The previous update installer could not be replaced."
                    }
                    val request = DownloadManager.Request(release.downloadUrl.toUri())
                        .setTitle("Linkora ${release.versionName}")
                        .setDescription("Downloading the verified app update")
                        .setMimeType(APK_MIME_TYPE)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                        )
                        .setDestinationInExternalFilesDir(
                            getApplication(),
                            Environment.DIRECTORY_DOWNLOADS,
                            "app-updates/${release.assetName}",
                        )
                    val id = downloadManager.enqueue(request)
                    preferences.edit { putLong(DOWNLOAD_ID, id) }
                }
            }.onSuccess {
                _state.value = _state.value.copy(
                    phase = AppUpdatePhase.Downloading,
                    downloadedBytes = 0L,
                    totalBytes = release.assetBytes,
                    message = "Starting download...",
                )
                ensureMonitor()
            }.onFailure { error -> fail(error.message ?: "The update download could not start.") }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            removeDownloadRecord()
            _state.value.release?.let(::installerFile)?.delete()
            _state.value = _state.value.copy(
                phase = AppUpdatePhase.Available,
                downloadedBytes = 0L,
                message = "Download canceled.",
            )
        }
    }

    fun refresh() {
        val release = _state.value.release ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val id = preferences.getLong(DOWNLOAD_ID, NO_DOWNLOAD)
            when {
                id != NO_DOWNLOAD -> refreshDownload(release)
                installerFile(release).isFile -> validateInstaller(release, requestInstall = false)
            }
            ensureMonitor()
        }
    }

    fun createInstallIntent(): Intent? {
        val release = _state.value.release ?: return null
        val archive = installerFile(release)
        return runCatching {
            validateArchive(archive, release)
            _state.value = _state.value.copy(
                phase = AppUpdatePhase.Installing,
                message = "Waiting for Android installation approval...",
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
            fail(error.message ?: "The update installer is invalid.")
            null
        }
    }

    private fun loadLatestRelease(): AppRelease {
        val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Linkora-Mobile/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub could not provide the latest release (${connection.responseCode})."
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            check(!release.optBoolean("draft") && !release.optBoolean("prerelease")) {
                "The latest GitHub release is not a stable public release."
            }
            val version = AppUpdatePolicy.versionFromTag(release.getString("tag_name"))
            val expectedAssetName = "linkora-mobile-v$version.apk"
            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length())
                .map(assets::getJSONObject)
                .firstOrNull { item -> item.getString("name") == expectedAssetName }
                ?: error("The latest release does not include $expectedAssetName.")
            val downloadUrl = AppUpdatePolicy.allowedDownloadUrl(
                asset.getString("browser_download_url"),
            ) ?: error("The release APK uses an untrusted download address.")
            val digest = AppUpdatePolicy.normalizedSha256(asset.optString("digest"))
                ?: error("The release APK does not include a valid SHA-256 digest.")
            AppRelease(
                versionName = version,
                title = release.optString("name").ifBlank { "Linkora $version" },
                notes = release.optString("body").trim(),
                assetName = asset.getString("name"),
                downloadUrl = downloadUrl,
                sha256 = digest,
                assetBytes = asset.optLong("size").coerceAtLeast(0L),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureMonitor() {
        if (monitorJob?.isActive == true || _state.value.phase != AppUpdatePhase.Downloading) return
        monitorJob = viewModelScope.launch {
            while (_state.value.phase == AppUpdatePhase.Downloading) {
                _state.value.release?.let { release ->
                    withContext(Dispatchers.IO) { refreshDownload(release) }
                }
                delay(PROGRESS_REFRESH_MS)
            }
        }
    }

    private fun refreshDownload(release: AppRelease) {
        val id = preferences.getLong(DOWNLOAD_ID, NO_DOWNLOAD)
        if (id == NO_DOWNLOAD) return
        downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) {
                preferences.edit { remove(DOWNLOAD_ID) }
                fail("Android no longer has this download. Start it again.")
                return
            }
            val status = cursor.longColumn(DownloadManager.COLUMN_STATUS).toInt()
            val downloaded = cursor.longColumn(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                .coerceAtLeast(0L)
            val reportedTotal = cursor.longColumn(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val total = reportedTotal.takeIf { it > 0L } ?: release.assetBytes
            when (status) {
                DownloadManager.STATUS_PENDING -> updateProgress(downloaded, total, "Waiting for Android Download Manager...")
                DownloadManager.STATUS_RUNNING -> updateProgress(
                    downloaded,
                    total,
                    "Downloading ${formatBytes(downloaded)} of ${formatBytes(total)}",
                )
                DownloadManager.STATUS_PAUSED -> updateProgress(
                    downloaded,
                    total,
                    "Download paused by Android. It will resume automatically.",
                )
                DownloadManager.STATUS_SUCCESSFUL -> validateInstaller(release, requestInstall = true)
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.longColumn(DownloadManager.COLUMN_REASON).toInt()
                    removeDownloadRecord()
                    installerFile(release).delete()
                    fail(downloadFailureMessage(reason))
                }
            }
        }
    }

    private fun updateProgress(downloaded: Long, total: Long, message: String) {
        _state.value = _state.value.copy(
            phase = AppUpdatePhase.Downloading,
            downloadedBytes = downloaded,
            totalBytes = total,
            message = message,
        )
    }

    private fun validateInstaller(release: AppRelease, requestInstall: Boolean) {
        runCatching { validateArchive(installerFile(release), release) }
            .onSuccess {
                val bytes = installerFile(release).length()
                preferences.edit { remove(DOWNLOAD_ID) }
                _state.value = _state.value.copy(
                    phase = AppUpdatePhase.ReadyToInstall,
                    downloadedBytes = bytes,
                    totalBytes = bytes,
                    message = "Download verified. Android will confirm the update.",
                )
                if (requestInstall) _installRequests.trySend(Unit)
            }
            .onFailure { error ->
                removeDownloadRecord()
                installerFile(release).delete()
                fail(error.message ?: "The downloaded update is invalid.")
            }
    }

    private fun validateArchive(archive: File, release: AppRelease) {
        check(archive.isFile && archive.length() > 0L) { "The update APK is missing." }
        check(archive.sha256() == release.sha256) { "The update checksum does not match GitHub." }
        val packageManager = getApplication<Application>().packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archiveInfo = checkNotNull(packageManager.getPackageArchiveInfo(archive.path, flags)) {
            "Android could not inspect the update APK."
        }
        val installedInfo = packageManager.getPackageInfo(
            getApplication<Application>().packageName,
            flags,
        )
        check(archiveInfo.packageName == installedInfo.packageName) {
            "The downloaded APK is not Linkora Mobile."
        }
        check(archiveInfo.longVersionCodeCompat() > installedInfo.longVersionCodeCompat()) {
            "This APK is not newer than the installed app."
        }
        val archiveSigners = archiveInfo.signerDigests()
        val installedSigners = installedInfo.signerDigests()
        check(
            archiveSigners.isNotEmpty() &&
                installedSigners.isNotEmpty() &&
                archiveSigners == installedSigners,
        ) {
            "The update is not signed by the installed Linkora release key."
        }
    }

    private fun PackageInfo.signerDigests(): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            signatures?.map { it.toByteArray() }.orEmpty()
        }
        return certificates.map { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        }.toSet()
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun installerFile(release: AppRelease): File {
        val downloads = checkNotNull(
            getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        ) { "Android external app storage is unavailable." }
        return File(File(downloads, "app-updates"), release.assetName)
    }

    private fun removeDownloadRecord() {
        val id = preferences.getLong(DOWNLOAD_ID, NO_DOWNLOAD)
        if (id != NO_DOWNLOAD) downloadManager.remove(id)
        preferences.edit { remove(DOWNLOAD_ID) }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(phase = AppUpdatePhase.Failed, message = message)
    }

    private fun android.database.Cursor.longColumn(name: String): Long =
        getLong(getColumnIndexOrThrow(name))

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }

    private fun downloadFailureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Android could not resume this download. Try again."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage is unavailable."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "GitHub returned incomplete download data."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "There is not enough storage for this update."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The download was redirected too many times."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The GitHub release APK is unavailable."
        else -> "The update download failed. Check your connection and try again."
    }

    companion object {
        private const val RELEASE_API =
            "https://api.github.com/repos/x1n-Q/linkora-android/releases/latest"
        private const val PREFERENCES = "app_updates"
        private const val DOWNLOAD_ID = "download_id"
        private const val NO_DOWNLOAD = -1L
        private const val NETWORK_TIMEOUT_MS = 15_000
        private const val PROGRESS_REFRESH_MS = 750L
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
