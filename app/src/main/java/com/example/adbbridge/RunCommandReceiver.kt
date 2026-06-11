package com.example.adbbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class RunCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN) return
        val cmd = intent.getStringExtra(EXTRA_CMD)
        if (cmd.isNullOrEmpty()) {
            Log.w(TAG, "run requested without a command, ignoring")
            return
        }
        Log.i(TAG, "run requested")
        val serviceIntent = Intent(context, AdbBridgeService::class.java).putExtra(EXTRA_CMD, cmd)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private companion object {
        const val TAG = "RunCommandReceiver"
        const val ACTION_RUN = "com.example.adbbridge.action.RUN"
    }
}
