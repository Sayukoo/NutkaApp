package com.nutka.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-wide event log ("Ustawienia" -> "Dziennik zdarzeń") for diagnosing
 * issues like recording/transcription failures without needing a debugger
 * attached. In-memory ring buffer mirrored to a small file on disk so it
 * survives process death; capped so it can't grow without bound.
 */
object AppLog {
    private const val MAX_LINES = 1000
    private const val MAX_FILE_BYTES = 1024 * 1024

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    private var logFile: File? = null
    private val format = SimpleDateFormat("dd.MM HH:mm:ss", Locale("pl"))
    private var handlerInstalled = false

    fun init(context: Context) {
        if (logFile == null) {
            val file = File(context.applicationContext.filesDir, "app_log.txt")
            logFile = file
            _lines.value = runCatching { file.takeIf { it.exists() }?.readLines() }.getOrNull()?.takeLast(MAX_LINES) ?: emptyList()
            d("App", "Log zainicjalizowany")
        }

        if (!handlerInstalled) {
            handlerInstalled = true
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                e("CRASH", "Nieobsłużony wyjątek w wątku ${thread.name}: ${throwable.javaClass.simpleName} - ${throwable.message}\n${throwable.stackTraceToString()}")
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun d(tag: String, message: String) {
        val line = "${format.format(Date())}  [$tag]  $message"
        _lines.update { (it + line).takeLast(MAX_LINES) }
        val file = logFile ?: return
        runCatching {
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                val trimmed = file.readLines().takeLast(MAX_LINES / 2)
                file.writeText(trimmed.joinToString("\n") + "\n")
            }
            file.appendText(line + "\n")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) {
            "$message: ${throwable.javaClass.simpleName} - ${throwable.message}\n${throwable.stackTraceToString()}"
        } else message
        d("BŁĄD-$tag", fullMsg)
    }

    fun clear() {
        _lines.value = emptyList()
        logFile?.let { runCatching { it.writeText("") } }
    }

    fun asText(): String = _lines.value.joinToString("\n")
}
