package top.linkora.mobile.node

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
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
import top.linkora.mobile.project.ProjectServerService
import top.linkora.mobile.project.ProjectWorkspaceManager

class NodeRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operationJob: Job? = null
    private var childProcess: Process? = null
    private var stoppedByUser = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESTART -> startRuntime()
            ACTION_INSTALL -> installDependencies()
            ACTION_STOP -> stopRuntime()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        operationJob?.cancel()
        terminateChildProcess()
        operationJob = null
        scope.cancel()
        if (stoppedByUser) {
            NodeRuntimeStatus.update(
                NodeRuntimePhase.Idle,
                "Node.js ${NodeBinary.VERSION} is ready.",
            )
        } else if (NodeRuntimeStatus.state.value.phase !in setOf(
                NodeRuntimePhase.Idle,
                NodeRuntimePhase.Error,
            )
        ) {
            NodeRuntimeStatus.update(
                NodeRuntimePhase.Error,
                "Android stopped the Node.js runtime. Start the project again.",
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRuntime() {
        stoppedByUser = false
        operationJob?.cancel()
        terminateChildProcess()
        stopService(Intent(this, ProjectServerService::class.java))
        startForeground(NOTIFICATION_ID, buildNotification("Preparing project"))

        operationJob = scope.launch {
            try {
                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Syncing,
                    "Syncing the selected project from Android Files...",
                    clearLogs = true,
                )
                updateNotification("Syncing project")
                val launchConfig = ProjectWorkspaceManager(applicationContext).prepareNodeLaunch()
                val executable = NodeBinary.resolve(applicationContext)
                val commandDirectory = NodeBinary.prepareCommandDirectory(applicationContext, executable)
                waitForPortRelease()

                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Starting,
                    "Starting ${launchConfig.entryPoint} on ${ProjectWorkspaceManager.LOCAL_URL}...",
                )
                updateNotification("Starting Node.js")
                NodeRuntimeStatus.appendLog("node ${launchConfig.entryPoint}")
                val process = startNodeProcess(
                    executable,
                    commandDirectory,
                    launchConfig.projectDirectory,
                    launchConfig.entryPoint,
                )
                childProcess = process
                val logJob = launch {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach(NodeRuntimeStatus::appendLog)
                    }
                }

                waitForLocalServer(process)
                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Running,
                    "Running at ${ProjectWorkspaceManager.LOCAL_URL}",
                )
                updateNotification("Running at ${ProjectWorkspaceManager.LOCAL_URL}")

                val exitCode = process.waitFor()
                logJob.join()
                if (childProcess === process) childProcess = null
                currentCoroutineContext().ensureActive()
                TunnelConnectorService.stop(applicationContext)
                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Error,
                    "Node.js stopped with exit code $exitCode. Review the runtime log.",
                )
                updateNotification("Runtime stopped")
                finishOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                terminateChildProcess()
                TunnelConnectorService.stop(applicationContext)
                NodeRuntimeStatus.appendLog(error.message ?: "Node.js could not start.")
                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Error,
                    error.message ?: "Node.js could not start.",
                )
                updateNotification("Runtime failed")
                finishOperation()
            }
        }
    }

    private fun stopRuntime() {
        stoppedByUser = true
        NodeRuntimeStatus.update(NodeRuntimePhase.Stopping, "Stopping Node.js...")
        operationJob?.cancel()
        terminateChildProcess()
        operationJob = null
        TunnelConnectorService.stop(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun installDependencies() {
        stoppedByUser = false
        operationJob?.cancel()
        terminateChildProcess()
        TunnelConnectorService.stop(applicationContext)
        stopService(Intent(this, ProjectServerService::class.java))
        startForeground(NOTIFICATION_ID, buildNotification("Preparing npm"))

        operationJob = scope.launch {
            try {
                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Syncing,
                    "Syncing the selected project from Android Files...",
                    clearLogs = true,
                )
                val launchConfig = ProjectWorkspaceManager(applicationContext).prepareNodeLaunch()
                val executable = NodeBinary.resolve(applicationContext)
                val commandDirectory = NodeBinary.prepareCommandDirectory(applicationContext, executable)
                val npmCli = NpmBundle.resolveCli(applicationContext)

                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Installing,
                    "Installing dependencies with npm ${NpmBundle.VERSION}...",
                )
                updateNotification("Installing dependencies")
                NodeRuntimeStatus.appendLog("npm install --no-audit --no-fund")
                val process = startNpmProcess(
                    executable,
                    commandDirectory,
                    npmCli,
                    launchConfig.projectDirectory,
                )
                childProcess = process
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach(NodeRuntimeStatus::appendLog)
                }
                val exitCode = process.waitFor()
                if (childProcess === process) childProcess = null
                currentCoroutineContext().ensureActive()
                check(exitCode == 0) { "npm install stopped with exit code $exitCode." }

                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Idle,
                    "Dependencies are installed. Start the project when you are ready.",
                )
                updateNotification("Dependencies installed")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                terminateChildProcess()
                TunnelConnectorService.stop(applicationContext)
                NodeRuntimeStatus.appendLog(error.message ?: "npm install failed.")
                NodeRuntimeStatus.update(
                    NodeRuntimePhase.Error,
                    error.message ?: "npm install failed. Review the installation log.",
                )
                updateNotification("Dependency install failed")
                finishOperation()
            }
        }
    }

    private fun finishOperation() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun terminateChildProcess() {
        val process = childProcess ?: return
        runCatching { process.destroy() }
        if (process.isAlive) runCatching { process.destroyForcibly() }
        childProcess = null
    }

    private fun startNodeProcess(
        executable: File,
        commandDirectory: File,
        directory: File,
        entryPoint: String,
    ): Process =
        ProcessBuilder(executable.absolutePath, entryPoint)
            .directory(directory)
            .redirectErrorStream(true)
            .apply {
                val home = File(filesDir, "node/home").apply { mkdirs() }
                val temporary = File(cacheDir, "node").apply { mkdirs() }
                environment().apply {
                    put("HOME", home.absolutePath)
                    put("TMPDIR", temporary.absolutePath)
                    put("HOST", "127.0.0.1")
                    put("PORT", ProjectWorkspaceManager.LOCAL_PORT.toString())
                    put("NODE_ENV", "production")
                    put("NODE_PATH", File(directory, "node_modules").absolutePath)
                    prependToPath(commandDirectory)
                }
            }
            .start()

    private fun startNpmProcess(
        executable: File,
        commandDirectory: File,
        npmCli: File,
        directory: File,
    ): Process =
        ProcessBuilder(
            executable.absolutePath,
            npmCli.absolutePath,
            "install",
            "--no-audit",
            "--no-fund",
            "--loglevel=notice",
        )
            .directory(directory)
            .redirectErrorStream(true)
            .apply {
                val home = File(filesDir, "node/home").apply { mkdirs() }
                val temporary = File(cacheDir, "node").apply { mkdirs() }
                val npmCache = File(cacheDir, "npm").apply { mkdirs() }
                environment().apply {
                    put("HOME", home.absolutePath)
                    put("TMPDIR", temporary.absolutePath)
                    put("npm_config_cache", npmCache.absolutePath)
                    put("npm_config_update_notifier", "false")
                    prependToPath(commandDirectory)
                }
            }
            .start()

    private fun MutableMap<String, String>.prependToPath(directory: File) {
        val inherited = get("PATH").orEmpty()
        put(
            "PATH",
            if (inherited.isBlank()) directory.absolutePath
            else "${directory.absolutePath}${File.pathSeparator}$inherited",
        )
    }

    private suspend fun waitForLocalServer(process: Process) {
        repeat(LOCAL_SERVER_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            check(process.isAlive) { "Node.js stopped before the local server became ready." }
            val ready = runCatching {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress("127.0.0.1", ProjectWorkspaceManager.LOCAL_PORT),
                        LOCAL_SERVER_CONNECT_TIMEOUT_MS,
                    )
                }
            }.isSuccess
            if (ready) return
            delay(LOCAL_SERVER_RETRY_MS)
        }
        error("Node.js did not open port ${ProjectWorkspaceManager.LOCAL_PORT} within 15 seconds.")
    }

    private suspend fun waitForPortRelease() {
        repeat(PORT_RELEASE_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            val inUse = runCatching {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress("127.0.0.1", ProjectWorkspaceManager.LOCAL_PORT),
                        LOCAL_SERVER_CONNECT_TIMEOUT_MS,
                    )
                }
            }.isSuccess
            if (!inUse) return
            delay(PORT_RELEASE_RETRY_MS)
        }
        error("Port ${ProjectWorkspaceManager.LOCAL_PORT} is still in use by another local server.")
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Node.js project runtime",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the active Node.js project running on this phone."
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
            .setSmallIcon(R.drawable.ic_linkora_mark)
            .setContentTitle("Linkora Node.js")
            .setContentText(status)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                0,
                "Stop",
                PendingIntent.getService(
                    this,
                    2,
                    Intent(this, NodeRuntimeService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        private const val ACTION_START = "top.linkora.mobile.node.START"
        private const val ACTION_RESTART = "top.linkora.mobile.node.RESTART"
        private const val ACTION_INSTALL = "top.linkora.mobile.node.INSTALL"
        private const val ACTION_STOP = "top.linkora.mobile.node.STOP"
        private const val CHANNEL_ID = "linkora_node_runtime"
        private const val NOTIFICATION_ID = 8789
        private const val LOCAL_SERVER_ATTEMPTS = 60
        private const val LOCAL_SERVER_CONNECT_TIMEOUT_MS = 200
        private const val LOCAL_SERVER_RETRY_MS = 250L
        private const val PORT_RELEASE_ATTEMPTS = 20
        private const val PORT_RELEASE_RETRY_MS = 100L

        fun start(context: Context) = send(context, ACTION_START)

        fun restart(context: Context) = send(context, ACTION_RESTART)

        fun installDependencies(context: Context) = send(context, ACTION_INSTALL)

        fun stop(context: Context) {
            context.startService(Intent(context, NodeRuntimeService::class.java).setAction(ACTION_STOP))
        }

        private fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NodeRuntimeService::class.java).setAction(action),
            )
        }
    }
}
