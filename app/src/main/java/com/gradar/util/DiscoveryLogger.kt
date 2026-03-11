package com.gradar.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DiscoveryLogger(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryLogger"
        private const val LOG_FILE_PREFIX = "unknown_entities"
        private const val LOG_FILE_EXTENSION = ".log"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private var logFile: File? = null
    private var currentDate: String = ""

    suspend fun logUnknownEntity(
        eventCode: Int,
        typeId: Int,
        uniqueName: String?,
        posX: Float,
        posZ: Float,
        rawParams: Map<Int, Any>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            ensureLogFile()

            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

            val uniqueNameStr = uniqueName ?: "null"
            val paramsStr = rawParams?.entries?.joinToString(";") { "${it.key}=${it.value}" } ?: ""

            val line = "$dateStr|$eventCode|$typeId|$uniqueNameStr|$posX|$posZ|$paramsStr\n"

            logFile?.appendText(line)
            Log.d(TAG, "Logged unknown entity: typeId=$typeId uniqueName=$uniqueName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log unknown entity", e)
        }
    }

    private fun ensureLogFile() {
        val today = DATE_FORMAT.format(Date())

        if (currentDate != today || logFile == null) {
            currentDate = today

            val logsDir = File(context.filesDir, "discovery_logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            logFile = File(logsDir, "$LOG_FILE_PREFIX$today$LOG_FILE_EXTENSION")

            if (logFile?.exists() == false) {
                logFile?.writeText("timestamp|eventCode|typeId|uniqueName|posX|posZ|rawParams\n")
            }

            cleanupOldLogs(logsDir)
        }
    }

    private fun cleanupOldLogs(logsDir: File) {
        try {
            val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

            logsDir.listFiles()?.filter { file ->
                file.name.startsWith(LOG_FILE_PREFIX) &&
                file.name.endsWith(LOG_FILE_EXTENSION) &&
                file.lastModified() < cutoffTime
            }?.forEach { oldFile ->
                oldFile.delete()
                Log.d(TAG, "Deleted old log file: ${oldFile.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup old logs", e)
        }
    }
}

data class UnknownEntityEntry(
    val timestamp: String,
    val eventCode: Int,
    val typeId: Int,
    val uniqueName: String?,
    val posX: Float,
    val posZ: Float
)
