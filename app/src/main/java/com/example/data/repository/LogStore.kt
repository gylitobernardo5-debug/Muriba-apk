package com.example.data.repository

import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class LogStore {
    companion object {
        private const val MAX_LOGS = 350
    }

    private val idCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val nowStr = synchronized(timeFormat) { timeFormat.format(Date()) }
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = nowStr,
            level = level,
            message = message
        )
        _logs.update { current ->
            val updated = current + entry
            if (updated.size > MAX_LOGS) updated.takeLast(MAX_LOGS) else updated
        }
    }

    fun info(message: String) = log(message, LogLevel.INFO)
    fun success(message: String) = log(message, LogLevel.SUCCESS)
    fun warn(message: String) = log(message, LogLevel.WARN)
    fun error(message: String) = log(message, LogLevel.ERROR)

    fun clear() {
        _logs.value = emptyList()
    }
}
