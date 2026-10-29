package top.linkora.mobile.connector

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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.linkora.mobile.MainActivity
import top.linkora.mobile.R
import top.linkora.mobile.node.NodeRuntimeStatus
import top.linkora.mobile.project.ProjectKind
import top.linkora.mobile.project.ProjectServerService
import top.linkora.mobile.project.ProjectWorkspaceManager

class TunnelConnectorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectorJob: Job? = null
    private var connectorProcess: Process? = null
    private var stoppedByUser = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stoppedByUser = true
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val hostname = intent.getStringExtra(EXTRA_HOSTNAME).orEmpty()
                val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
                if (hostname.isBlank() || token.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startConnector(hostname, token)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        connectorJob?.cancel()
        connectorJob = null
        connectorProcess?.destroy()
        connectorProcess = null
        scope.cancel()
        if (stoppedByUser || TunnelConnectorStatus.state.value !is TunnelConnectorState.Error) {
            TunnelConnectorStatus.update(TunnelConnectorState.Stopped)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startConnector(hostname: String, token: String) {
        stoppedByUser = false
        connectorJob?.cancel()
        connectorProcess?.destroy()
        TunnelConnectorStatus.update(TunnelConnectorState.Starting(hostname))
        startForeground(NOTIFICATION_ID, buildNotification(hostname, "Connecting"))

        connectorJob = scope.launch {
            runConnector(hostname, token)
        }
    }

    private suspend fun runConnector(hostname: String, token: String) {
        try {
            ensureLocalOrigin()
            val executable = CloudflaredBinary.resolve(applicationContext)
            waitForLocalServer()
            var attempt = 0
            while (currentCoroutineContext().isActive && attempt < MAX_ATTEMPTS) {
                if (attempt > 0) {
                    TunnelConnectorStatus.update(
                        TunnelConnectorState.Reconnecting(hostname, attempt),
                    )
                    updateNotification(hostname, "Reconnecting")
                    delay(RETRY_DELAYS_MS.getOrElse(attempt - 1) { RETRY_DELAYS_MS.last() })
                }

                attempt += 1
                val process = startProcess(executable, token)
                connectorProcess = process
                var registered = false
                val connectorContext = currentCoroutineContext()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        connectorContext.ensureActive()
                        if (!registered && line.indicatesRegistration()) {
                            registered = true
                            TunnelConnectorStatus.update(TunnelConnectorState.Running(hostname))
                            updateNotification(hostname, "Online")
                        }
                    }
                }
                val exitCode = process.waitFor()
                connectorProcess = null
                currentCoroutineContext().ensureActive()
                if (exitCode == 0) break
            }

            if (currentCoroutineContext().isActive) {
                TunnelConnectorStatus.update(
                    TunnelConnectorState.Error(
                        hostname,
                        "The built-in connector stopped. Open Linkora and connect the hostname again.",
                    ),
                )
                updateNotification(hostname, "Connection stopped")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TunnelConnectorStatus.update(
                TunnelConnectorState.Error(
                    hostname,
                    error.message ?: "The built-in connector could not start.",
                ),
            )
            updateNotification(hostname, "Connection failed")
        }
    }

    private fun ensureLocalOrigin() {
        when (ProjectWorkspaceManager(applicationContext).activeKind()) {
            ProjectKind.Static -> ProjectServerService.start(applicationContext)
            ProjectKind.Node -> check(NodeRuntimeStatus.state.value.isRunning) {
                "Start the Node.js project before publishing it."
            }
        }
    }

    private fun startProcess(executable: File, token: String): Process =
        ProcessBuilder(
            executable.absolutePath,
            "tunnel",
            "--no-autoupdate",
            "--protocol",
            "http2",
            "run",
            "--token",
            token,
        )
            .redirectErrorStream(true)
            .directory(filesDir)
            .apply {
                environment()["HOME"] = filesDir.absolutePath
                environment()["TMPDIR"] = cacheDir.absolutePath
            }
            .start()

    private suspend fun waitForLocalServer() {
        repeat(LOCAL_SERVER_ATTEMPTS) {
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
        error("The local project server did not become ready.")
    }

    private fun String.indicatesRegistration(): Boolean =
        contains("Registered tunnel connection", ignoreCase = true) ||
            contains("Connection registered", ignoreCase = true)

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Public project connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps a Linkora hostname connected to this phone."
            },
        )
    }

    private fun updateNotification(hostname: String, status: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(hostname, status),
        )
    }

    private fun buildNotification(hostname: String, status: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_linkora_mark)
            .setContentTitle(hostname)
            .setContentText("Linkora connector: $status")
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
                    1,
                    Intent(this, TunnelConnectorService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        private const val ACTION_START = "top.linkora.mobile.connector.START"
        private const val ACTION_STOP = "top.linkora.mobile.connector.STOP"
        private const val EXTRA_HOSTNAME = "hostname"
        private const val EXTRA_TOKEN = "token"
        private const val CHANNEL_ID = "linkora_tunnel_connector"
        private const val NOTIFICATION_ID = 8788
        private const val MAX_ATTEMPTS = 5
        private const val LOCAL_SERVER_ATTEMPTS = 40
        private const val LOCAL_SERVER_CONNECT_TIMEOUT_MS = 200
        private const val LOCAL_SERVER_RETRY_MS = 250L
        private val RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 5_000L, 10_000L)

        fun start(context: Context, hostname: String, token: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TunnelConnectorService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_HOSTNAME, hostname)
                    .putExtra(EXTRA_TOKEN, token),
            )
        }

        fun stop(context: Context) {
            TunnelConnectorStatus.update(TunnelConnectorState.Stopped)
            context.stopService(Intent(context, TunnelConnectorService::class.java))
        }
    }
}
