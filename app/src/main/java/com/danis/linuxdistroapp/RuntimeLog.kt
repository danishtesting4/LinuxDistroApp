package com.danis.linuxdistroapp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuntimeLog {
    private const val TAG = "AndistroRuntime"
    private const val FILE_NAME = "andistro-runtime.log"
    private const val MAX_LOG_BYTES = 512 * 1024L

    fun info(context: Context, message: String) {
        write(context, Log.INFO, message, null)
    }

    fun error(context: Context, message: String, error: Throwable) {
        write(context, Log.ERROR, message, error)
    }

    fun read(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.isFile) file.readText() else "No runtime logs are available."
    }

    private fun write(context: Context, priority: Int, message: String, error: Throwable?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val details = error?.let { Log.getStackTraceString(it) }.orEmpty()
        val entry = buildString {
            append(timestamp)
            append(if (priority == Log.ERROR) " ERROR " else " INFO ")
            appendLine(message)
            if (details.isNotEmpty()) {
                appendLine(details)
            }
        }

        if (error == null) Log.i(TAG, message) else Log.e(TAG, message, error)

        synchronized(this) {
            val file = File(context.filesDir, FILE_NAME)
            try {
                if (file.isFile && file.length() > MAX_LOG_BYTES) {
                    file.writeText(file.readText().takeLast((MAX_LOG_BYTES / 2).toInt()))
                }
                file.appendText(entry)
            } catch (writeError: IOException) {
                Log.e(TAG, "Unable to write the runtime log.", writeError)
            }
        }
    }
}
