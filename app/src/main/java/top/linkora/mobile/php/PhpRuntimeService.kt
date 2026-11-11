package top.linkora.mobile.php

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.Process as AndroidProcess
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import top.linkora.mobile.MainActivity
import top.linkora.mobile.R
import top.linkora.mobile.connector.TunnelConnectorService
import top.linkora.mobile.mariadb.putMariaDbEnvironment
import top.linkora.mobile.node.NodeRuntimeService
import top.linkora.mobile.project.ProjectServerService
import top.linkora.mobile.project.ProjectWorkspaceManager

class PhpRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operationJob: Job? = null
    private var monitorJob: Job? = null
    private var childProcess: Process? = null
    private var runtimeStartedAtMs: Long? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stoppedByUser = false
    private val preferences by lazy {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private var runtimeRequested: Boolean
        get() = preferences.getBoolean(KEY_RUNTIME_REQUESTED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_RUNTIME_REQUESTED, value).apply()
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESTART -> {
                runtimeRequested = true
                startRuntime()
            }
            ACTION_STOP -> {
                runtimeRequested = false
                stopRuntime()
            }
            null -> if (runtimeRequested) startRuntime() else stopSelf()
            else -> stopSelf()
        }
        return if (runtimeRequested) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        operationJob?.cancel()
        stopRuntimeMonitor()
        terminateChildProcess()
        releaseWakeLock()
        operationJob = null
        scope.cancel()
        if (stoppedByUser) {
            PhpRuntimeStatus.update(
                PhpRuntimePhase.Idle,
                "PHP ${PhpBinary.VERSION} is ready.",
            )
        } else if (PhpRuntimeStatus.state.value.phase !in setOf(
                PhpRuntimePhase.Idle,
                PhpRuntimePhase.Error,
            )
        ) {
            PhpRuntimeStatus.update(
                PhpRuntimePhase.Error,
                "Android stopped the PHP runtime. Start the project again.",
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRuntime() {
        stoppedByUser = false
        operationJob?.cancel()
        stopRuntimeMonitor()
        terminateChildProcess()
        NodeRuntimeService.stop(applicationContext)
        stopService(Intent(this, ProjectServerService::class.java))
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing project"))

        operationJob = scope.launch {
            try {
                PhpRuntimeStatus.update(
                    PhpRuntimePhase.Syncing,
                    "Syncing the selected project from Android Files...",
                    clearLogs = true,
                )
                updateNotification("Syncing project")
                val launchConfig = ProjectWorkspaceManager(applicationContext).preparePhpLaunch()
                val executable = PhpBinary.resolve(applicationContext)
                PhpRuntimeStatus.appendLog(
                    "Runtime: verified PHP Runtime Pack ${PhpBinary.VERSION}",
                )
                waitForPortRelease()

                PhpRuntimeStatus.update(
                    PhpRuntimePhase.Starting,
                    "Starting ${launchConfig.entryPoint} on ${ProjectWorkspaceManager.LOCAL_URL}...",
                )
                updateNotification("Starting PHP")
                PhpRuntimeStatus.appendLog(
                    "php -S 127.0.0.1:${ProjectWorkspaceManager.LOCAL_PORT} " +
                        "-t ${launchConfig.projectDirectory.name}",
                )
                val process = startPhpProcess(executable, launchConfig.projectDirectory)
                childProcess = process
                runtimeStartedAtMs = SystemClock.elapsedRealtime()
                val logJob = launch { readProcessOutput(process) }

                waitForLocalServer(process)
                PhpRuntimeStatus.update(
                    PhpRuntimePhase.Running,
                    "Running at ${ProjectWorkspaceManager.LOCAL_URL}",
                )
                startRuntimeMonitor(process)

                val exitCode = process.waitFor()
                logJob.join()
                if (childProcess === process) childProcess = null
                currentCoroutineContext().ensureActive()
                TunnelConnectorService.stop(applicationContext)
                PhpRuntimeStatus.update(
                    PhpRuntimePhase.Error,
                    "PHP stopped with exit code $exitCode. Review the runtime log.",
                )
                updateNotification("Runtime stopped")
                runtimeRequested = false
                finishOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                terminateChildProcess()
                TunnelConnectorService.stop(applicationContext)
                PhpRuntimeStatus.appendLog(error.message ?: "PHP could not start.")
                PhpRuntimeStatus.update(
                    PhpRuntimePhase.Error,
                    error.message ?: "PHP could not start.",
                )
                updateNotification("Runtime failed")
                runtimeRequested = false
                finishOperation()
            }
        }
    }

    private fun stopRuntime() {
        stoppedByUser = true
        PhpRuntimeStatus.update(PhpRuntimePhase.Stopping, "Stopping PHP...")
        operationJob?.cancel()
        stopRuntimeMonitor()
        terminateChildProcess()
        releaseWakeLock()
        operationJob = null
        TunnelConnectorService.stop(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishOperation() {
        stopRuntimeMonitor()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRuntimeMonitor(process: Process) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (process.isAlive) {
                val uptimeSeconds = runtimeUptimeSeconds()
                val memoryMb = runtimeMemoryMb()
                PhpRuntimeStatus.updateMetrics(uptimeSeconds, memoryMb)
                updateNotification(
                    "Uptime ${formatDuration(uptimeSeconds)} | RAM $memoryMb MB",
                )
                delay(NOTIFICATION_REFRESH_MS)
            }
        }
    }

    private fun stopRuntimeMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        runtimeStartedAtMs = null
    }

    private fun runtimeUptimeSeconds(): Long {
        val startedAt = runtimeStartedAtMs ?: SystemClock.elapsedRealtime()
        return ((SystemClock.elapsedRealtime() - startedAt) / 1_000L).coerceAtLeast(0L)
    }

    private fun runtimeMemoryMb(): Long {
        val appPid = AndroidProcess.myPid()
        val childPids = runCatching {
            File("/proc/$appPid/task/$appPid/children")
                .readText()
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull(String::toIntOrNull)
        }.getOrDefault(emptyList())
        val totalKb = (listOf(appPid) + childPids).sumOf(::residentMemoryKb)
        return ((totalKb + 1_023L) / 1_024L).coerceAtLeast(1L)
    }

    private fun residentMemoryKb(processId: Int): Long = runCatching {
        File("/proc/$processId/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmRSS:") }
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: 0L
        }
    }.getOrDefault(0L)

    private fun formatDuration(totalSeconds: Long): String {
        val days = totalSeconds / 86_400
        val hours = totalSeconds % 86_400 / 3_600
        val minutes = totalSeconds % 3_600 / 60
        val seconds = totalSeconds % 60
        return if (days > 0) {
            "%dd %02d:%02d:%02d".format(days, hours, minutes, seconds)
        } else {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:php-runtime")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun terminateChildProcess() {
        val process = childProcess ?: return
        runCatching { process.destroy() }
        if (process.isAlive) runCatching { process.destroyForcibly() }
        childProcess = null
    }

    private fun startPhpProcess(executable: File, directory: File): Process {
        val home = File(filesDir, "php/home").apply { mkdirs() }
        val temporary = File(cacheDir, "php").apply { mkdirs() }
        val sessions = File(filesDir, "php/sessions").apply { mkdirs() }
        return ProcessBuilder(
            executable.absolutePath,
            "-d",
            "display_errors=1",
            "-d",
            "log_errors=1",
            "-d",
            "session.save_path=${sessions.absolutePath}",
            "-d",
            "sys_temp_dir=${temporary.absolutePath}",
            "-d",
            "upload_tmp_dir=${temporary.absolutePath}",
            "-S",
            "127.0.0.1:${ProjectWorkspaceManager.LOCAL_PORT}",
            "-t",
            directory.absolutePath,
        )
            .directory(directory)
            .redirectErrorStream(true)
            .apply {
                environment().apply {
                    put("HOME", home.absolutePath)
                    put("TMPDIR", temporary.absolutePath)
                    put("LINKORA_HOST", "127.0.0.1")
                    put("LINKORA_PORT", ProjectWorkspaceManager.LOCAL_PORT.toString())
                    putMariaDbEnvironment(applicationContext)
                    prependToNativeLibraryPath(checkNotNull(executable.parentFile))
                }
            }
            .start()
    }

    private fun MutableMap<String, String>.prependToNativeLibraryPath(directory: File) {
        val inherited = get("LD_LIBRARY_PATH").orEmpty()
        put(
            "LD_LIBRARY_PATH",
            if (inherited.isBlank()) directory.absolutePath
            else "${directory.absolutePath}${File.pathSeparator}$inherited",
        )
    }

    private suspend fun readProcessOutput(process: Process) {
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach(PhpRuntimeStatus::appendLog)
            }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            if (process.isAlive) throw error
        }
    }

    private suspend fun waitForLocalServer(process: Process) {
        repeat(LOCAL_SERVER_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            check(process.isAlive) { "PHP stopped before the local server became ready." }
            val ready = canConnectToLocalServer()
            if (ready) return
            delay(LOCAL_SERVER_RETRY_MS)
        }
        error("PHP did not open port ${ProjectWorkspaceManager.LOCAL_PORT} within 15 seconds.")
    }

    private suspend fun waitForPortRelease() {
        repeat(PORT_RELEASE_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            if (!canConnectToLocalServer()) return
            delay(PORT_RELEASE_RETRY_MS)
        }
        error("Port ${ProjectWorkspaceManager.LOCAL_PORT} is still in use by another local server.")
    }

    private fun canConnectToLocalServer(): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress("127.0.0.1", ProjectWorkspaceManager.LOCAL_PORT),
                LOCAL_SERVER_CONNECT_TIMEOUT_MS,
            )
        }
    }.isSuccess

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "PHP project runtime",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the active PHP project running on this phone."
            },
        )
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(status),
        )
    }

    private fun buildNotification(status: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_linkora_notification)
            .setContentTitle("PHP project running")
            .setContentText(status)
            .setSubText(ProjectWorkspaceManager.LOCAL_URL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                R.drawable.ic_linkora_notification,
                "Stop",
                PendingIntent.getService(
                    this,
                    3,
                    Intent(this, PhpRuntimeService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        private const val ACTION_START = "top.linkora.mobile.php.START"
        private const val ACTION_RESTART = "top.linkora.mobile.php.RESTART"
        private const val ACTION_STOP = "top.linkora.mobile.php.STOP"
        private const val CHANNEL_ID = "linkora_php_runtime"
        private const val NOTIFICATION_ID = 8790
        private const val PREFERENCES_NAME = "php_runtime_service"
        private const val KEY_RUNTIME_REQUESTED = "runtime_requested"
        private const val NOTIFICATION_REFRESH_MS = 5_000L
        private const val LOCAL_SERVER_ATTEMPTS = 60
        private const val LOCAL_SERVER_CONNECT_TIMEOUT_MS = 200
        private const val LOCAL_SERVER_RETRY_MS = 250L
        private const val PORT_RELEASE_ATTEMPTS = 30
        private const val PORT_RELEASE_RETRY_MS = 100L

        fun start(context: Context) = send(context, ACTION_START)

        fun restart(context: Context) = send(context, ACTION_RESTART)

        fun stop(context: Context) {
            context.startService(Intent(context, PhpRuntimeService::class.java).setAction(ACTION_STOP))
        }

        private fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PhpRuntimeService::class.java).setAction(action),
            )
        }
    }
}
