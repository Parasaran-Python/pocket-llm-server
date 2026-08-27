package pyhon.pro.localhost_ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pyhon.pro.localhost_ai.MainActivity
import pyhon.pro.localhost_ai.R
import pyhon.pro.localhost_ai.engine.LocalAiEngine
import pyhon.pro.localhost_ai.server.OpenAiServer
import pyhon.pro.localhost_ai.util.NetworkUtils
import pyhon.pro.localhost_ai.util.PreferencesManager

class LocalServerService : Service() {

    private var server: OpenAiServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "LocalServerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "localhost_ai_server_channel"

        const val ACTION_START = "pyhon.pro.localhost_ai.ACTION_START"
        const val ACTION_STOP = "pyhon.pro.localhost_ai.ACTION_STOP"

        private val _isServerRunning = MutableStateFlow(false)
        val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

        private val _serverEndpoint = MutableStateFlow("")
        val serverEndpoint: StateFlow<String> = _serverEndpoint.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, LocalServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocalServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        observeEngineState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startServer()
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        val port = PreferencesManager.getServerPort(this)
        if (server == null) {
            try {
                server = OpenAiServer(applicationContext, port)
                server?.start()
                _isServerRunning.value = true

                val endpoint = NetworkUtils.getEndpointUrl(this, port)
                _serverEndpoint.value = endpoint
                Log.i(TAG, "OpenAI server started on LAN at $endpoint")

                startForeground(NOTIFICATION_ID, buildNotification(endpoint))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HTTP server on port $port", e)
                _isServerRunning.value = false
            }
        } else {
            val endpoint = NetworkUtils.getEndpointUrl(this, port)
            _serverEndpoint.value = endpoint
            startForeground(NOTIFICATION_ID, buildNotification(endpoint))
        }
    }

    private fun stopServer() {
        try {
            server?.stop()
            server = null
            _isServerRunning.value = false
            _serverEndpoint.value = ""
            Log.i(TAG, "OpenAI server stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    private fun observeEngineState() {
        serviceScope.launch {
            LocalAiEngine.activeModelName.collect {
                if (_isServerRunning.value) {
                    val port = PreferencesManager.getServerPort(this@LocalServerService)
                    val endpoint = NetworkUtils.getEndpointUrl(this@LocalServerService, port)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification(endpoint))
                }
            }
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalHostAI::ServerWakeLock").apply {
                acquire(24 * 60 * 60 * 1000L) // 24 hours
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LocalHostAI::ServerWifiLock").apply {
                acquire()
            }
            Log.i(TAG, "Acquired WakeLock & WifiLock for uninterrupted background server.")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (ignored: Exception) {}
    }

    private fun buildNotification(endpoint: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocalServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val modelName = LocalAiEngine.activeModelName.value ?: "No model loaded"
        val backend = LocalAiEngine.activeBackend.value

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocalHost AI Server Running")
            .setContentText("$endpoint | $modelName ($backend)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop Server", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LocalHost AI Local Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification for LocalHost AI LAN Server"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        releaseLocks()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
