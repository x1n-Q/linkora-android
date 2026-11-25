package top.linkora.mobile.project

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import top.linkora.mobile.MainActivity
import top.linkora.mobile.R

class ProjectServerService : Service() {
    private var server: StaticSiteServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            server = StaticSiteServer(applicationContext).apply {
                start(5_000, false)
            }
        } catch (error: Exception) {
            server = null
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildFailureNotification(error.message),
            )
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        if (server == null) START_NOT_STICKY else START_STICKY

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Local project server",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the active Linkora project available locally."
            },
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_linkora_notification)
        .setContentTitle("Linkora project is running")
        .setContentText(ProjectWorkspaceManager.LOCAL_URL)
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
        .build()

    private fun buildFailureNotification(detail: String?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_linkora_notification)
            .setContentTitle("Project server could not start")
            .setContentText(detail?.take(120) ?: "Port 8787 may already be in use.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    detail ?: "Port 8787 may already be in use. Open Linkora and try again.",
                ),
            )
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        private const val CHANNEL_ID = "linkora_project_server"
        private const val NOTIFICATION_ID = 8787

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProjectServerService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProjectServerService::class.java))
        }
    }
}
