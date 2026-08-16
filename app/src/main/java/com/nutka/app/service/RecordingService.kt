package com.nutka.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nutka.app.MainActivity
import com.nutka.app.R
import com.nutka.app.data.AudioRecorderManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Foreground service so recording — and the transcription upload that
 * follows it — survives a locked screen or the user switching to another
 * app. Without this, Android suspends the process's network sockets once
 * it's backgrounded and the ElevenLabs upload dies mid-request with a
 * "Broken pipe". [startRecording]/[stopRecording] cover the mic session;
 * [beginWork]/[updateWork]/[endWork] extend the same foreground promotion
 * through the transcription network call that happens afterward (see
 * NutkaViewModel.finalizeTranscription), including for imported files that
 * never actually recorded anything here.
 */
class RecordingService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): RecordingService = this@RecordingService
    }

    private val binder = LocalBinder()
    private var recorder: AudioRecorderManager? = null
    private var ticker: CountDownTimer? = null
    private var isForeground = false

    private val _elapsedSec = MutableStateFlow(0)
    val elapsedSec: StateFlow<Int> = _elapsedSec.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Int>>(emptyList())
    val bookmarks: StateFlow<List<Int>> = _bookmarks.asStateFlow()

    private val _startFailed = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val startFailed: SharedFlow<String> = _startFailed.asSharedFlow()

    override fun onBind(intent: Intent?): IBinder = binder

    /** Returns true if the recorder actually started. */
    fun startRecording(outputFile: File): Boolean {
        _elapsedSec.value = 0
        _isPaused.value = false
        _bookmarks.value = emptyList()

        promote("Nutka nagrywa", "Stuknij, aby wrócić do transkrypcji")
        val manager = AudioRecorderManager(this)
        val started = runCatching { manager.start(outputFile) }
        if (started.isFailure) {
            _startFailed.tryEmit(started.exceptionOrNull()?.message ?: "Nie udało się uruchomić mikrofonu")
            endWork()
            return false
        }
        recorder = manager
        startTicker()
        return true
    }

    fun pauseRecording() {
        _isPaused.value = true
        recorder?.pause()
        ticker?.cancel()
    }

    fun resumeRecording() {
        _isPaused.value = false
        recorder?.resume()
        startTicker()
    }

    fun addBookmark() {
        _bookmarks.value = _bookmarks.value + _elapsedSec.value
    }

    /**
     * Stops the mic session and returns the finished file — but deliberately
     * does NOT tear the foreground service down; the transcription upload
     * that follows needs the same protection, so the caller keeps the
     * service alive via [updateWork] and finishes it with [endWork].
     */
    fun stopRecording(): File? {
        ticker?.cancel()
        val file = recorder?.stop()
        recorder = null
        updateWork("Przetwarzanie nagrania…")
        return file
    }

    /** Promotes (or keeps) this service in the foreground for a bounded background task. */
    fun beginWork(title: String, text: String) = promote(title, text)

    fun updateWork(text: String) = promote("Nutka", text)

    /** Ends the foreground session — only when nothing else needs it (e.g. not mid-recording). */
    fun endWork() {
        if (recorder != null) return
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promote(title: String, text: String) {
        val notification = buildNotification(title, text)
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!_isPaused.value) _elapsedSec.value += 1
            }
            override fun onFinish() {}
        }.start()
    }

    private fun buildNotification(title: String, text: String): Notification {
        val channelId = "recording"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Nagrywanie i transkrypcja", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        ticker?.cancel()
        recorder?.stop()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 421
    }
}
