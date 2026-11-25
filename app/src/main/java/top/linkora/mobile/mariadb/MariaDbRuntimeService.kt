package top.linkora.mobile.mariadb

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
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
import top.linkora.mobile.runtime.RuntimeProcessTerminator

class MariaDbRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operationJob: Job? = null
    private var monitorJob: Job? = null
    private var childProcess: Process? = null
    private var startedAtMs: Long? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stoppedByUser = false
    private val preferences by lazy {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private var runtimeRequested: Boolean
        get() = preferences.getBoolean(KEY_RUNTIME_REQUESTED, false)
        set(value) = preferences.edit().putBoolean(KEY_RUNTIME_REQUESTED, value).apply()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        MariaDbRuntimeStatus.setCredentials(MariaDbCredentialStore.load(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESTART -> {
                runtimeRequested = true
                startDatabase()
            }
            ACTION_STOP -> {
                runtimeRequested = false
                stopDatabase()
            }
            null -> if (runtimeRequested) startDatabase() else stopSelf()
            else -> stopSelf()
        }
        return if (runtimeRequested) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        operationJob?.cancel()
        stopMonitor()
        terminateProcess()
        releaseWakeLock()
        scope.cancel()
        if (stoppedByUser) {
            MariaDbRuntimeStatus.update(
                MariaDbRuntimePhase.Idle,
                "Optional database is ready to start.",
            )
        } else if (MariaDbRuntimeStatus.state.value.phase !in setOf(
                MariaDbRuntimePhase.Idle,
                MariaDbRuntimePhase.Error,
            )
        ) {
            MariaDbRuntimeStatus.update(
                MariaDbRuntimePhase.Error,
                "Android stopped MariaDB. Start the database again.",
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDatabase() {
        stoppedByUser = false
        operationJob?.cancel()
        stopMonitor()
        terminateProcess()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing database"))

        operationJob = scope.launch {
            try {
                MariaDbRuntimeStatus.update(
                    MariaDbRuntimePhase.Preparing,
                    "Preparing the private database directory...",
                    clearLogs = true,
                )
                val installation = MariaDbBinary.resolve(applicationContext)
                val layout = MariaDbLayoutManager.prepare(applicationContext, installation)
                val credentials = MariaDbCredentialStore.load(applicationContext)
                MariaDbRuntimeStatus.setCredentials(credentials)
                MariaDbRuntimeStatus.appendLog(
                    "Runtime: verified MariaDB Runtime Pack ${MariaDbBinary.VERSION}",
                )
                if (!layout.initializedMarker.isFile) {
                    check(layout.data.deleteRecursively()) {
                        "An incomplete MariaDB data directory could not be replaced."
                    }
                    check(layout.data.mkdirs()) {
                        "MariaDB data directory could not be created."
                    }
                    updateNotification("Creating database")
                    bootstrapDatabase(installation.executable, layout, credentials)
                }

                waitForPortRelease()
                MariaDbRuntimeStatus.update(
                    MariaDbRuntimePhase.Starting,
                    "Starting MariaDB on ${credentials.host}:${credentials.port}...",
                )
                updateNotification("Starting database")
                val process = startServer(installation.executable, layout)
                childProcess = process
                startedAtMs = SystemClock.elapsedRealtime()
                val logJob = launch { readProcessOutput(process) }
                waitForDatabase(process)

                MariaDbRuntimeStatus.update(
                    MariaDbRuntimePhase.Running,
                    "Available to projects on this phone.",
                )
                startMonitor(process)
                val exitCode = process.waitFor()
                logJob.join()
                if (childProcess === process) childProcess = null
                currentCoroutineContext().ensureActive()
                MariaDbRuntimeStatus.update(
                    MariaDbRuntimePhase.Error,
                    "MariaDB stopped with exit code $exitCode. Review the database log.",
                )
                updateNotification("Database stopped")
                runtimeRequested = false
                finishOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "MariaDB runtime failed", error)
                terminateProcess()
                MariaDbRuntimeStatus.appendLog(error.message ?: "MariaDB could not start.")
                MariaDbRuntimeStatus.update(
                    MariaDbRuntimePhase.Error,
                    error.message ?: "MariaDB could not start.",
                )
                updateNotification("Database failed")
                runtimeRequested = false
                finishOperation()
            }
        }
    }

    private fun stopDatabase() {
        stoppedByUser = true
        MariaDbRuntimeStatus.update(MariaDbRuntimePhase.Stopping, "Stopping MariaDB...")
        operationJob?.cancel()
        stopMonitor()
        terminateProcess()
        releaseWakeLock()
        operationJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun bootstrapDatabase(
        executable: File,
        layout: MariaDbLayout,
        credentials: MariaDbCredentials,
    ) {
        MariaDbRuntimeStatus.appendLog("Creating MariaDB system tables...")
        val process = ProcessBuilder(
            commonArguments(executable, layout) + listOf(
                "--bootstrap",
                "--silent-startup",
                "--skip-networking",
                "--log-warnings=0",
                "--max-allowed-packet=8M",
                "--net-buffer-length=16K",
            ),
        )
            .directory(layout.root)
            .redirectErrorStream(true)
            .withRuntimeEnvironment(executable, layout)
            .start()

        process.outputStream.bufferedWriter().use { writer ->
            writer.appendLine("CREATE DATABASE IF NOT EXISTS mysql;")
            writer.appendLine("USE mysql;")
            writer.appendLine("SET @auth_root_socket=NULL;")
            BOOTSTRAP_FILES.forEach { relativePath ->
                File(layout.share, relativePath).bufferedReader().use { it.copyTo(writer) }
                writer.newLine()
            }
            writer.appendLine(
                "CREATE DATABASE IF NOT EXISTS `${credentials.database}` " +
                    "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
            )
            writer.appendLine("DELETE FROM mysql.global_priv WHERE User='root';")
            writer.appendLine(
                "REPLACE INTO mysql.global_priv (Host, User, Priv) VALUES " +
                    "('localhost','root','${fullAccessAccount(credentials.rootPassword)}');",
            )
            writer.appendLine(
                "REPLACE INTO mysql.global_priv (Host, User, Priv) VALUES " +
                    "('127.0.0.1','${credentials.username}'," +
                    "'${fullAccessAccount(credentials.password)}');",
            )
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        output.lineSequence().forEach(MariaDbRuntimeStatus::appendLog)
        if (exitCode != 0) Log.e(TAG, "MariaDB bootstrap output:\n$output")
        check(exitCode == 0) { "MariaDB could not create its system tables (exit $exitCode)." }
        layout.initializedMarker.writeText(MariaDbBinary.VERSION)
        MariaDbRuntimeStatus.appendLog("Private database initialized.")
    }

    private fun startServer(executable: File, layout: MariaDbLayout): Process = ProcessBuilder(
        commonArguments(executable, layout) + listOf(
            "--bind-address=${MariaDbCredentialStore.HOST}",
            "--port=${MariaDbCredentialStore.PORT}",
            "--skip-name-resolve",
            "--skip-host-cache",
            "--max-connections=16",
            "--innodb-buffer-pool-size=32M",
            "--innodb-use-native-aio=0",
            "--key-buffer-size=4M",
            "--performance-schema=OFF",
            "--table-open-cache=128",
            "--thread-cache-size=2",
            "--skip-log-bin",
        ),
    )
        .directory(layout.root)
        .redirectErrorStream(true)
        .withRuntimeEnvironment(executable, layout)
        .start()

    private fun commonArguments(executable: File, layout: MariaDbLayout) = listOf(
        executable.absolutePath,
        "--no-defaults",
        "--basedir=${layout.root.absolutePath}",
        "--datadir=${layout.data.absolutePath}",
        "--lc-messages-dir=${layout.share.absolutePath}",
        "--character-sets-dir=${File(layout.share, "charsets").absolutePath}",
        "--plugin-dir=${layout.plugins.absolutePath}",
        "--socket=${layout.socket.absolutePath}",
        "--pid-file=${layout.pidFile.absolutePath}",
        "--tmpdir=${layout.temporary.absolutePath}",
        "--skip-log-error",
    )

    private fun ProcessBuilder.withRuntimeEnvironment(
        executable: File,
        layout: MariaDbLayout,
    ): ProcessBuilder = apply {
        environment().apply {
            put("HOME", layout.root.absolutePath)
            put("TMPDIR", layout.temporary.absolutePath)
            val inherited = get("LD_LIBRARY_PATH").orEmpty()
            val nativeDirectory = checkNotNull(executable.parentFile).absolutePath
            put(
                "LD_LIBRARY_PATH",
                if (inherited.isBlank()) nativeDirectory
                else "$nativeDirectory${File.pathSeparator}$inherited",
            )
        }
    }

    private suspend fun waitForDatabase(process: Process) {
        repeat(SERVER_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            check(process.isAlive) { "MariaDB stopped before accepting connections." }
            if (canConnect()) return
            delay(SERVER_RETRY_MS)
        }
        error("MariaDB did not open port ${MariaDbCredentialStore.PORT} within 20 seconds.")
    }

    private suspend fun waitForPortRelease() {
        repeat(PORT_RELEASE_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            if (!canConnect()) return
            delay(PORT_RELEASE_RETRY_MS)
        }
        error("Port ${MariaDbCredentialStore.PORT} is already in use.")
    }

    private fun canConnect(): Boolean = runCatching {
        Socket().use {
            it.connect(
                InetSocketAddress(MariaDbCredentialStore.HOST, MariaDbCredentialStore.PORT),
                CONNECT_TIMEOUT_MS,
            )
        }
    }.isSuccess

    private suspend fun readProcessOutput(process: Process) {
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach(MariaDbRuntimeStatus::appendLog)
            }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            if (process.isAlive) throw error
        }
    }

    private fun startMonitor(process: Process) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (process.isAlive) {
                val uptime = ((SystemClock.elapsedRealtime() -
                    (startedAtMs ?: SystemClock.elapsedRealtime())) / 1_000L).coerceAtLeast(0L)
                val memory = processMemoryMb()
                MariaDbRuntimeStatus.updateMetrics(uptime, memory)
                updateNotification("Uptime ${formatDuration(uptime)} | RAM $memory MB")
                delay(NOTIFICATION_REFRESH_MS)
            }
        }
    }

    private fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        startedAtMs = null
    }

    private fun processMemoryMb(): Long {
        val appPid = AndroidProcess.myPid()
        val childPids = runCatching {
            File("/proc/$appPid/task/$appPid/children").readText().trim()
                .split(Regex("\\s+")).mapNotNull(String::toIntOrNull)
        }.getOrDefault(emptyList())
        val totalKb = childPids.sumOf { pid ->
            runCatching {
                File("/proc/$pid/status").useLines { lines ->
                    lines.firstOrNull { it.startsWith("VmRSS:") }
                        ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: 0L
                }
            }.getOrDefault(0L)
        }
        return ((totalKb + 1_023L) / 1_024L).coerceAtLeast(1L)
    }

    private fun formatDuration(seconds: Long): String = "%02d:%02d:%02d".format(
        seconds / 3_600,
        seconds % 3_600 / 60,
        seconds % 60,
    )

    private fun fullAccessAccount(password: String): String =
        "{\"access\":18446744073709551615," +
            "\"plugin\":\"mysql_native_password\"," +
            "\"authentication_string\":\"${mysqlNativePassword(password)}\"}"

    private fun mysqlNativePassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val firstPass = digest.digest(password.toByteArray(Charsets.UTF_8))
        val secondPass = digest.digest(firstPass)
        return "*" + secondPass.joinToString(separator = "") {
            "%02X".format(it.toInt() and 0xff)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:mariadb-runtime")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun terminateProcess() {
        val process = childProcess ?: return
        RuntimeProcessTerminator.terminate(process)
        childProcess = null
    }

    private fun finishOperation() {
        stopMonitor()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "MariaDB database",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the optional local project database running."
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
            .setContentTitle("MariaDB running")
            .setContentText(status)
            .setSubText("127.0.0.1:${MariaDbCredentialStore.PORT}")
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
                    4,
                    Intent(this, MariaDbRuntimeService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        private const val ACTION_START = "top.linkora.mobile.mariadb.START"
        private const val ACTION_RESTART = "top.linkora.mobile.mariadb.RESTART"
        private const val ACTION_STOP = "top.linkora.mobile.mariadb.STOP"
        private const val CHANNEL_ID = "linkora_mariadb_runtime"
        private const val NOTIFICATION_ID = 8791
        private const val PREFERENCES_NAME = "mariadb_runtime_service"
        private const val KEY_RUNTIME_REQUESTED = "runtime_requested"
        private const val TAG = "LinkoraMariaDb"
        private const val NOTIFICATION_REFRESH_MS = 5_000L
        private const val SERVER_ATTEMPTS = 80
        private const val SERVER_RETRY_MS = 250L
        private const val CONNECT_TIMEOUT_MS = 200
        private const val PORT_RELEASE_ATTEMPTS = 30
        private const val PORT_RELEASE_RETRY_MS = 100L
        private val BOOTSTRAP_FILES = listOf(
            "mariadb_system_tables.sql",
            "mariadb_performance_tables.sql",
            "mariadb_system_tables_data.sql",
            "fill_help_tables.sql",
            "maria_add_gis_sp_bootstrap.sql",
            "mariadb_sys_schema.sql",
        )

        fun start(context: Context) = send(context, ACTION_START)

        fun restart(context: Context) = send(context, ACTION_RESTART)

        fun stop(context: Context) {
            context.startService(
                Intent(context, MariaDbRuntimeService::class.java).setAction(ACTION_STOP),
            )
        }

        private fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MariaDbRuntimeService::class.java).setAction(action),
            )
        }
    }
}
