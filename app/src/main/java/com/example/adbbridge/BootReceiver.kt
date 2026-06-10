package com.example.adbbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "boot completed, starting service")
        val serviceIntent = Intent(context, AdbBridgeService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
