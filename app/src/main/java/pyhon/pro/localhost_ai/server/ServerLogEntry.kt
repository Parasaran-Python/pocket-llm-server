package pyhon.pro.localhost_ai.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Represents a single HTTP request log item for live monitor.
 */
data class ServerLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val method: String,
    val path: String,
    val clientIp: String,
    val statusCode: Int,
    val durationMs: Long,
    val tokensCount: Int = 0,
    val speedTps: Double = 0.0,
    val isStream: Boolean = false,
    val error: String? = null
)

object ServerLogManager {
    private const val MAX_LOGS = 100
    private val logsList = mutableListOf<ServerLogEntry>()

    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun addLog(entry: ServerLogEntry) {
        logsList.add(0, entry)
        if (logsList.size > MAX_LOGS) {
            logsList.removeAt(logsList.size - 1)
        }
        _logs.value = logsList.toList()
    }

    @Synchronized
    fun clearLogs() {
        logsList.clear()
        _logs.value = emptyList()
    }
}
