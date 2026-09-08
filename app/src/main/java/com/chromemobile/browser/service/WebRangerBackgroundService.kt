package com.chromemobile.browser.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.chromemobile.browser.MainActivity
import com.chromemobile.browser.R
import com.chromemobile.browser.mcp.EmbeddedMcpHttpServer
import com.chromemobile.browser.mcp.MobileChromeMcpServer
import com.chromemobile.browser.tab.TabManager

/**
 * Android Foreground Service maintaining WebRanger MCP Server
 * and WebView DOM operations active while app is running in the background.
 * Ensures Termux agents (omp, Claude Code) maintain persistent, unthrottled access.
 */
class WebRangerBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Acquire partial wake lock to prevent CPU sleep during background agent execution
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WebRanger:McpBackgroundServerWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopServerAndService()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, EmbeddedMcpHttpServer.DEFAULT_PORT)
            ?: EmbeddedMcpHttpServer.DEFAULT_PORT

        val notification = buildForegroundNotification(port)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                startForeground(NOTIFICATION_ID, notification, foregroundType)
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startMcpServer(port)

        return START_STICKY
    }

    private fun startMcpServer(port: Int) {
        val server = mcpServerRef
        if (server != null) {
            if (httpServerInstance == null || httpServerInstance?.port != port || httpServerInstance?.isRunning != true) {
                httpServerInstance?.stop()
                httpServerInstance = EmbeddedMcpHttpServer(
                    mcpServer = server,
                    tabManager = tabManagerRef,
                    port = port
                ).apply {
                    start()
                }
            }
        }
    }

    private fun stopServerAndService() {
        httpServerInstance?.stop()
        httpServerInstance = null

        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServerAndService()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebRanger MCP Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service maintaining WebRanger MCP server for external agents"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(port: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WebRangerBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WebRanger MCP Server Active")
            .setContentText("Listening on http://127.0.0.1:$port for external AI agents")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .addAction(0, "Stop Server", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "webranger_mcp_channel"
        const val NOTIFICATION_ID = 4281
        const val ACTION_START = "com.chromemobile.browser.action.START_MCP_SERVER"
        const val ACTION_STOP = "com.chromemobile.browser.action.STOP_MCP_SERVER"
        const val EXTRA_PORT = "extra_mcp_port"

        // Active references passed from MainActivity / Application
        var mcpServerRef: MobileChromeMcpServer? = null
        var tabManagerRef: TabManager? = null
        var httpServerInstance: EmbeddedMcpHttpServer? = null

        val isServerRunning: Boolean
            get() = httpServerInstance?.isRunning == true

        val currentPort: Int
            get() = httpServerInstance?.port ?: EmbeddedMcpHttpServer.DEFAULT_PORT

        fun start(context: Context, mcpServer: MobileChromeMcpServer, tabManager: TabManager?, port: Int = EmbeddedMcpHttpServer.DEFAULT_PORT) {
            mcpServerRef = mcpServer
            tabManagerRef = tabManager

            val intent = Intent(context, WebRangerBackgroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebRangerBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
