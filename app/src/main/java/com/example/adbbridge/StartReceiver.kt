package com.example.adbbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START) return
        Logger.i(TAG, "wake requested, starting service")
        val serviceIntent = Intent(context, AdbBridgeService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private companion object {
        const val TAG = "StartReceiver"
        const val ACTION_START = "com.example.adbbridge.action.START"
    }
}
