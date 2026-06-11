package com.example.adbbridge

import android.util.Log
import dadb.AdbShellResponse

/**
 * Single logging entry point for the app. Everything goes through one tag so the whole app can be
 * followed with `adb logcat -s AdbBridge`, and each line is prefixed with its source so the origin
 * stays clear. Shell results are formatted consistently so a failure is easy to trace back to the
 * command that caused it.
 */
object Logger {
    private const val TAG = "AdbBridge"

    fun i(source: String, message: String) = Log.i(TAG, "$source: $message")

    fun w(source: String, message: String) = Log.w(TAG, "$source: $message")

    fun e(source: String, message: String, throwable: Throwable? = null) =
        Log.e(TAG, "$source: $message", throwable)

    /** Logs a command's outcome, downgrading to a warning when it exits non-zero. */
    fun command(source: String, cmd: String, response: AdbShellResponse) {
        val output = response.allOutput.trim().ifEmpty { "<no output>" }
        val line = "command (exit ${response.exitCode}): $cmd\n$output"
        if (response.exitCode == 0) i(source, line) else w(source, line)
    }
}
