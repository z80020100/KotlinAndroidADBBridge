package com.example.adbbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Intent extra carrying the shell command for the service to run as root. */
const val EXTRA_CMD = "cmd"

class AdbBridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keyPair by lazy { loadKeyPair() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForegroundService()
        Logger.i(TAG, "service alive")
        intent?.getStringExtra(EXTRA_CMD)?.let { runCommand(it) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun runCommand(cmd: String) {
        scope.launch {
            Logger.i(TAG, "running command: $cmd")
            try {
                connectRoot().use { dadb ->
                    Logger.command(TAG, cmd, dadb.shell(cmd))
                }
            } catch (e: Exception) {
                Logger.e(TAG, "command execution failed: $cmd", e)
            }
        }
    }

    /** Connects to the local adbd and elevates it to root if needed. Caller must close. */
    private suspend fun connectRoot(): Dadb {
        val dadb = Dadb.create(ADBD_HOST, ADBD_PORT, keyPair)
        if (dadb.isRoot()) return dadb
        Logger.i(TAG, "adbd running as shell, requesting root")
        runCatching { dadb.open("root:").close() }
        dadb.close()
        repeat(ROOT_CONNECT_ATTEMPTS) {
            delay(ROOT_CONNECT_DELAY_MS)
            val rooted = runCatching { Dadb.create(ADBD_HOST, ADBD_PORT, keyPair) }.getOrNull() ?: return@repeat
            if (rooted.isRoot()) return rooted
            rooted.close()
        }
        error("adbd did not become root after $ROOT_CONNECT_ATTEMPTS attempts")
    }

    // Each retry reconnects with a fresh Dadb on purpose. A reused instance can't survive adbd's root
    // restart: DadbImpl reconnects only after a local close(), not on adbd's peer-side drop, so the
    // built-in dadb.root() (which waits on one instance) never observes the restart on this device.
    private fun Dadb.isRoot(): Boolean = shell("id").allOutput.contains("uid=0")

    /** Loads the adb keypair from app storage, generating it on first run. */
    private fun loadKeyPair(): AdbKeyPair {
        val privateKey = File(filesDir, "adbkey")
        val publicKey = File(filesDir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }

    private fun startAsForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_service_title))
            .setContentText(getString(R.string.notification_service_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "adb_bridge_service"
        const val NOTIFICATION_ID = 1
        const val TAG = "AdbBridgeService"
        const val ADBD_HOST = "127.0.0.1"
        const val ADBD_PORT = 5555
        const val ROOT_CONNECT_ATTEMPTS = 10
        const val ROOT_CONNECT_DELAY_MS = 500L
    }
}
