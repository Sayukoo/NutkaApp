package com.nutka.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.nutka.app.MainActivity
import com.nutka.app.R
import com.nutka.app.data.AppLog
import com.nutka.app.data.AudioRecorderManager
import com.nutka.app.data.Recording
import com.nutka.app.data.RecordingStatus
import com.nutka.app.data.RecordingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10

/**
 * Foreground service that owns a recording session end to end, so it keeps
 * running with the screen off, from the lock screen, and after the app is
 * swiped out of the recents list.
 *
 * Everything about a live session lives here rather than in the ViewModel:
 * the mic, the elapsed clock, the bookmarks, the amplitude meter — and the
 * write of the finished [Recording] into the library. The UI is a pure
 * observer that can die and come back (process death, activity recreation)
 * without the recording noticing; on rebind it simply re-reads
 * [isRecording]/[elapsedSec]/[waveform] and picks up where the user left off.
 *
 * The same service also stays in the foreground through the transcription
 * upload that follows ([beginWork]/[updateWork]/[endWork]) — without it
 * Android suspends the app sockets once backgrounded and the ElevenLabs
 * request dies mid-flight with a "Broken pipe".
 *
 * Phone calls run through the same session machinery ([ACTION_RECORD_CALL] /
 * [ACTION_CALL_ENDED], sent by [CallRecordingAccessibilityService]); they only
 * differ in the audio source and in a monitor that warns when the call is
 * off speaker or the capture is being silenced.
 */
class RecordingService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): RecordingService = this@RecordingService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo by lazy { RecordingsRepository.get(this) }

    private var recorder: AudioRecorderManager? = null
    private var tickerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var idleShutdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false
    private var foregroundType = TYPE_NONE
    private var isDestroying = false
    private var callMonitorJob: Job? = null

    // ---- Session bookkeeping (monotonic clock, immune to wall-clock changes) ----
    private var sessionId: String? = null
    private var sessionStartedAtElapsed = 0L
    private var sessionStartedAtWallClock = 0L
    private var pausedTotalMs = 0L
    private var pauseStartedAtElapsed = 0L
    private var isCallSession = false
    /** Last time the recorder reported any signal at all — a call captured as pure zeros has been silenced. */
    @Volatile private var lastSoundAtElapsed = 0L
    /** Shown instead of the normal notification text while a call is being recorded badly. */
    @Volatile private var callWarning: String? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedSec = MutableStateFlow(0)
    val elapsedSec: StateFlow<Int> = _elapsedSec.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    /** Smoothed 0..1 loudness, for the pulse rings. */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    /** Rolling history of raw 0..1 peaks, oldest first — the scrolling waveform. */
    private val _waveform = MutableStateFlow<List<Float>>(emptyList())
    val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Int>>(emptyList())
    val bookmarks: StateFlow<List<Int>> = _bookmarks.asStateFlow()

    private val _startFailed = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val startFailed: SharedFlow<String> = _startFailed.asSharedFlow()

    /** Emits the recording id once a session has been saved to the library. */
    private val _sessionFinished = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val sessionFinished: SharedFlow<String> = _sessionFinished.asSharedFlow()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        AppLog.init(applicationContext)
        // A session interrupted by a process kill left its audio on disk;
        // salvage it into the library instead of leaking an orphan file.
        // Off the main thread: it probes the file with MediaMetadataRetriever.
        serviceScope.launch { recoverInterruptedSession() }
    }

    /**
     * Handles the notification action buttons and survives a system restart.
     * START_STICKY matters here: if Android kills the process under memory
     * pressure it recreates the service with a null intent, which is exactly
     * when [recoverInterruptedSession] (in [onCreate]) rescues the partial file.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_DISCARD -> cancelRecording()
            ACTION_RECORD_CALL -> startCallRecording()
            // Only ends a call session: a dictation the user started by hand
            // during a call is theirs to stop.
            ACTION_CALL_ENDED -> if (isCallSession) stopRecording()
        }
        if (intent == null && !_isRecording.value) {
            // Restarted by the system after a process kill. Everything worth
            // doing already happened in onCreate's salvage pass, so don't
            // linger as a started service with no work.
            scheduleIdleShutdown()
        }
        return START_STICKY
    }

    // ---- Session control -------------------------------------------------------

    /**
     * Starts recording the ongoing phone call. Reached through
     * startForegroundService(), so every path must promote to the foreground
     * before returning.
     */
    private fun startCallRecording() {
        CallRecordingAccessibilityService.cancelRecordPrompt(this)
        if (_isRecording.value) {
            // Already in the foreground (a dictation, or the prompt tapped
            // twice), which satisfies the startForegroundService() contract.
            AppLog.d("Rozmowy", "Nagrywanie już trwa — pomijam start rozmowy")
            return
        }
        if (!CallRecordingAccessibilityService.callInProgress) {
            // The prompt was tapped just after hanging up.
            AppLog.d("Rozmowy", "Rozmowa już się zakończyła — nie nagrywam")
            promote(TYPE_DATA, getString(R.string.app_name), "Rozmowa już się zakończyła")
            endWork()
            return
        }
        startRecording("r" + System.currentTimeMillis(), phoneCall = true)
    }

    /** Returns true if the recorder actually started. */
    fun startRecording(id: String, phoneCall: Boolean = false): Boolean {
        if (_isRecording.value) return true

        pausedTotalMs = 0L
        pauseStartedAtElapsed = 0L
        _isPaused.value = false
        _elapsedSec.value = 0
        _bookmarks.value = emptyList()
        _audioLevel.value = 0f
        _waveform.value = emptyList()

        // Must happen before anything that can fail: the process was launched
        // with startForegroundService() and Android gives us ~5s to promote.
        promote(TYPE_MIC, getString(R.string.app_name), if (phoneCall) "Nagrywanie rozmowy…" else "Nagrywanie…")

        val manager = AudioRecorderManager(this)
        val source = if (phoneCall) MediaRecorder.AudioSource.VOICE_RECOGNITION else MediaRecorder.AudioSource.MIC
        val file = runCatching { manager.start(repo.audioDir, id, source) }.getOrElse { e ->
            AppLog.e("Recording", "Start nagrywania $id nie powiódł się", e)
            _startFailed.tryEmit(e.message ?: "Nie udało się uruchomić mikrofonu")
            endWork()
            return false
        }

        recorder = manager
        sessionId = id
        sessionStartedAtElapsed = SystemClock.elapsedRealtime()
        sessionStartedAtWallClock = System.currentTimeMillis()
        isCallSession = phoneCall
        callSessionActive = phoneCall
        lastSoundAtElapsed = sessionStartedAtElapsed
        _isRecording.value = true
        persistSession(id, file, phoneCall)
        acquireWakeLock()
        startTicker()
        startAmplitudeMonitoring()
        if (phoneCall) startCallMonitor()
        updateNotification()
        AppLog.d("Recording", "Start nagrywania ${if (phoneCall) "rozmowy " else ""}$id -> ${file.name}")
        return true
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        pauseStartedAtElapsed = SystemClock.elapsedRealtime()
        _isPaused.value = true
        _audioLevel.value = 0f
        recorder?.pause()
        updateNotification()
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        if (pauseStartedAtElapsed != 0L) {
            pausedTotalMs += SystemClock.elapsedRealtime() - pauseStartedAtElapsed
            pauseStartedAtElapsed = 0L
        }
        _isPaused.value = false
        lastSoundAtElapsed = SystemClock.elapsedRealtime()
        recorder?.resume()
        updateNotification()
    }

    fun addBookmark() {
        if (!_isRecording.value) return
        _bookmarks.value = _bookmarks.value + _elapsedSec.value
    }

    /**
     * Stops the mic session and writes the finished recording into the shared
     * library itself — so a stop triggered from the notification, with no UI
     * alive, saves exactly the same entry the in-app button would.
     *
     * Deliberately does NOT tear the foreground service down: the
     * transcription upload that follows needs the same protection and ends it
     * via [endWork].
     *
     * @return the saved recording id, or null if there was nothing to save.
     */
    fun stopRecording(): String? {
        if (recorder == null && !_isRecording.value) return null

        val id = sessionId
        val durationSec = (elapsedMs() / 1000L).toInt()
        val bookmarkOffsets = _bookmarks.value
        val startedAt = sessionStartedAtWallClock.takeIf { it > 0L } ?: System.currentTimeMillis()
        val phoneCall = isCallSession

        stopCallMonitor()
        stopAmplitudeMonitoring()
        stopTicker()
        val file = recorder?.stop()
        recorder = null
        releaseWakeLock()
        clearSession()

        _isRecording.value = false
        _isPaused.value = false
        _elapsedSec.value = 0
        _bookmarks.value = emptyList()
        _waveform.value = emptyList()

        if (id == null || file == null) {
            AppLog.e("Recording", "Zatrzymano nagrywanie bez zapisanego pliku (id=$id)")
            _startFailed.tryEmit("Nagranie nie zapisało się — plik jest pusty")
            endWork()
            return null
        }

        repo.upsert(
            Recording(
                id = id,
                title = if (phoneCall) CALL_TITLE else null,
                filePath = file.absolutePath,
                durationSec = durationSec.coerceAtLeast(1),
                createdAtMillis = startedAt,
                status = RecordingStatus.PROCESSING,
                bookmarks = bookmarkOffsets,
                isPhoneCall = phoneCall
            )
        )
        AppLog.d("Recording", "Zapisano $id — ${durationSec}s, plik=${file.name}, rozmiar=${file.length()}B")

        // The mic is released now, so re-declare the promotion as a plain data
        // sync for the upload phase — holding the microphone type without a mic
        // is what Android 14+ rejects.
        promote(TYPE_DATA, getString(R.string.app_name), "Przetwarzanie nagrania…")
        _sessionFinished.tryEmit(id)
        scheduleIdleShutdown()
        return id
    }

    /**
     * Safety net for a stop that nobody follows up on — e.g. the user tapped
     * "Zatrzymaj" on the notification with the UI long gone, so no transcription
     * ever starts and nothing calls [endWork]. Without this the service would
     * sit in the foreground with a stale "Przetwarzanie nagrania…" notification
     * forever. The recording itself is already saved and stays PROCESSING, so
     * the app picks the transcription up next time it opens.
     */
    private fun scheduleIdleShutdown() {
        idleShutdownJob?.cancel()
        idleShutdownJob = serviceScope.launch {
            delay(IDLE_SHUTDOWN_MILLIS)
            if (!_isRecording.value) endWork()
        }
    }

    /** Discards the session, deletes the audio, and drops the foreground promotion. */
    fun cancelRecording() {
        val id = sessionId
        stopCallMonitor()
        stopAmplitudeMonitoring()
        stopTicker()
        recorder?.stop()
        recorder = null
        releaseWakeLock()
        clearSession()

        _isRecording.value = false
        _isPaused.value = false
        _elapsedSec.value = 0
        _bookmarks.value = emptyList()
        _audioLevel.value = 0f
        _waveform.value = emptyList()

        id?.let { repo.deleteAudioFor(it) }
        endWork()
    }

    // ---- Foreground promotion for the post-recording upload --------------------

    fun beginWork(title: String, text: String) {
        idleShutdownJob?.cancel()
        promote(if (_isRecording.value) TYPE_MIC else TYPE_DATA, title, text)
    }

    fun updateWork(text: String) {
        if (isDestroying) return
        runCatching {
            notificationManager().notify(NOTIFICATION_ID, buildNotification(getString(R.string.app_name), text))
        }.onFailure { e -> AppLog.e("Service", "Błąd aktualizacji powiadomienia", e) }
    }

    /** Ends the foreground session — refuses while a recording still needs it. */
    fun endWork() {
        if (recorder != null || _isRecording.value) return
        idleShutdownJob?.cancel()
        isForeground = false
        foregroundType = TYPE_NONE
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { notificationManager().cancel(NOTIFICATION_ID) }
        runCatching { stopSelf() }
    }

    // ---- Clock -----------------------------------------------------------------

    /**
     * Elapsed time derived from [SystemClock.elapsedRealtime] rather than
     * counted up tick by tick. The previous implementation used
     * `CountDownTimer(Long.MAX_VALUE, 1000)`, whose internal
     * `elapsedRealtime() + Long.MAX_VALUE` overflows to a negative deadline —
     * so it fired onFinish() immediately and never ticked once. That is why
     * the timer sat at 00:00 and every saved recording had durationSec = 0
     * (which in turn hid the audio player on the transcript screen).
     */
    private fun elapsedMs(): Long {
        if (sessionStartedAtElapsed == 0L) return 0L
        val now = SystemClock.elapsedRealtime()
        val pausedNow = if (_isPaused.value && pauseStartedAtElapsed != 0L) now - pauseStartedAtElapsed else 0L
        return (now - sessionStartedAtElapsed - pausedTotalMs - pausedNow).coerceAtLeast(0L)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                val seconds = (elapsedMs() / 1000L).toInt()
                if (seconds != _elapsedSec.value) _elapsedSec.value = seconds
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // ---- Amplitude / waveform --------------------------------------------------

    private fun startAmplitudeMonitoring() {
        amplitudeJob?.cancel()
        amplitudeJob = serviceScope.launch {
            val history = ArrayDeque<Float>()
            var smoothed = 0f
            while (isActive) {
                val raw = if (_isPaused.value) 0 else recorder?.getMaxAmplitude() ?: 0
                if (raw > 0) lastSoundAtElapsed = SystemClock.elapsedRealtime()
                val level = levelFor(raw)

                // Fast attack, slow release: the meter jumps the instant you
                // speak up and settles back gently, the way a dictaphone does.
                smoothed = if (level > smoothed) level else smoothed + (level - smoothed) * RELEASE
                _audioLevel.value = smoothed

                history.addLast(level)
                while (history.size > WAVEFORM_SAMPLES) history.removeFirst()
                _waveform.value = history.toList()

                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun stopAmplitudeMonitoring() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _audioLevel.value = 0f
    }

    /**
     * Maps MediaRecorder's linear 0..32767 peak onto a 0..1 bar height through
     * decibels. A linear mapping looks broken on a phone mic — normal speech
     * peaks at only 3–15% of full scale, so the bars barely move — whereas
     * hearing is logarithmic, so a dB scale is what makes "speak louder →
     * visibly taller lines" actually track the voice.
     */
    private fun levelFor(rawAmplitude: Int): Float {
        if (rawAmplitude <= NOISE_FLOOR) return 0f
        val db = 20.0 * log10(rawAmplitude.toDouble() / MAX_AMPLITUDE)
        return ((db - MIN_DB) / -MIN_DB).toFloat().coerceIn(0f, 1f)
    }

    // ---- Phone call monitor ----------------------------------------------------

    /**
     * Surfaces the two ways a call recording fails silently, while the user
     * can still do something about it:
     * - the call is on the earpiece, so the mic only hears the user (no app
     *   can move the call to the speaker — Telecom owns that route);
     * - the capture delivers exact digital zeros, which is how Android
     *   silences a recorder it refuses during a call (accessibility service
     *   off, or a vendor policy that ignores the exemption).
     */
    private fun startCallMonitor() {
        callMonitorJob?.cancel()
        callMonitorJob = serviceScope.launch {
            while (isActive) {
                val warning = when {
                    _isPaused.value -> null
                    SystemClock.elapsedRealtime() - lastSoundAtElapsed >= SILENCE_WARNING_MS ->
                        "Mikrofon nagrywa ciszę — sprawdź, czy usługa dostępności Nutki jest włączona"
                    !isSpeakerOn() -> "Włącz głośnik — inaczej głos rozmówcy się nie nagra"
                    else -> null
                }
                if (warning != callWarning) {
                    callWarning = warning
                    AppLog.d("Rozmowy", warning ?: "Nagrywanie rozmowy działa poprawnie")
                    updateNotification()
                }
                delay(CALL_MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun stopCallMonitor() {
        callMonitorJob?.cancel()
        callMonitorJob = null
        callWarning = null
    }

    /** Whether the call audio currently plays through the loudspeaker. Unknown counts as yes — no false alarms. */
    private fun isSpeakerOn(): Boolean {
        val audio = getSystemService(AudioManager::class.java) ?: return true
        return runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                audio.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn
            }
        }.getOrDefault(true)
    }

    // ---- Wake lock -------------------------------------------------------------

    /**
     * A foreground service keeps the process alive but does not keep the CPU
     * awake — with the screen off the device can still suspend, which stalls
     * the timer/level coroutines and, on some OEM ROMs, the capture itself.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val manager = getSystemService(PowerManager::class.java)
            wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(MAX_SESSION_MILLIS)
            }
        }.onFailure { AppLog.e("Service", "Nie udało się przejąć wake locka", it) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    // ---- Crash recovery --------------------------------------------------------

    private fun sessionPrefs() = getSharedPreferences("nutka_session", Context.MODE_PRIVATE)

    private fun persistSession(id: String, file: File, phoneCall: Boolean) {
        runCatching {
            sessionPrefs().edit()
                .putString(KEY_SESSION_ID, id)
                .putString(KEY_SESSION_FILE, file.absolutePath)
                .putLong(KEY_SESSION_STARTED, sessionStartedAtWallClock)
                .putBoolean(KEY_SESSION_CALL, phoneCall)
                .apply()
        }
    }

    private fun clearSession() {
        sessionId = null
        isCallSession = false
        callSessionActive = false
        sessionStartedAtElapsed = 0L
        sessionStartedAtWallClock = 0L
        pausedTotalMs = 0L
        pauseStartedAtElapsed = 0L
        runCatching { sessionPrefs().edit().clear().apply() }
    }

    /**
     * Picks up audio left behind by a session that never got to stop cleanly
     * (process killed by an OEM battery manager, low memory, a crash). The
     * ADTS container [AudioRecorderManager] records into needs no trailer, so
     * whatever reached the disk is still playable and transcribable.
     */
    private fun recoverInterruptedSession() {
        val prefs = runCatching { sessionPrefs() }.getOrNull() ?: return
        val id = prefs.getString(KEY_SESSION_ID, null) ?: return
        val path = prefs.getString(KEY_SESSION_FILE, null)
        val startedAt = prefs.getLong(KEY_SESSION_STARTED, System.currentTimeMillis())
        val phoneCall = prefs.getBoolean(KEY_SESSION_CALL, false)
        runCatching { prefs.edit().clear().apply() }

        val file = path?.let(::File)?.takeIf { it.exists() } ?: repo.audioFileFor(id)
        if (file == null || file.length() < MIN_SALVAGEABLE_BYTES) {
            runCatching { file?.delete() }
            AppLog.d("Recording", "Przerwana sesja $id nie zawierała dźwięku — pominięto")
            return
        }
        if (repo.find(id) != null) return

        val durationSec = probeDurationSec(file)
        repo.upsert(
            Recording(
                id = id,
                title = if (phoneCall) CALL_TITLE else null,
                filePath = file.absolutePath,
                durationSec = durationSec,
                createdAtMillis = startedAt,
                status = RecordingStatus.PROCESSING,
                isPhoneCall = phoneCall
            )
        )
        AppLog.d("Recording", "Odzyskano przerwane nagranie $id (${durationSec}s, ${file.length()}B)")
    }

    private fun probeDurationSec(file: File): Int {
        val retriever = android.media.MediaMetadataRetriever()
        val fromMetadata = runCatching {
            retriever.setDataSource(file.absolutePath)
            (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) / 1000L
        }.getOrDefault(0L)
        runCatching { retriever.release() }
        // Fall back to the nominal bitrate (128 kbit/s ≈ 16 kB per second).
        return (if (fromMetadata > 0L) fromMetadata else file.length() / 16_000L).toInt().coerceAtLeast(1)
    }

    // ---- Notification ----------------------------------------------------------

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    private fun updateNotification() {
        if (!isForeground || isDestroying) return
        runCatching {
            notificationManager().notify(NOTIFICATION_ID, buildNotification(getString(R.string.app_name), null))
        }
    }

    /**
     * Promotes (or re-declares) this service in the foreground. It MUST always
     * reach startForeground() when the process was launched via
     * startForegroundService(), or Android throws "did not then call
     * Service.startForeground()" a few seconds later. If the typed promotion is
     * refused (e.g. an import starts transcription without mic permission on
     * API 34+), retry untyped and finally fall back to a plain notification.
     */
    private fun promote(type: Int, title: String, text: String) {
        // A service on its way out must not post an ongoing notification that
        // would then outlive it and sit in the shade with nothing behind it.
        if (isDestroying) return
        val notification = buildNotification(title, text)
        if (isForeground && type == foregroundType) {
            updateWork(text)
            return
        }
        var promoted = runCatching {
            if (Build.VERSION.SDK_INT >= 29 && type != TYPE_NONE) {
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { e ->
            AppLog.e("Service", "Błąd promowania do foreground (typ=$type)", e)
        }.isSuccess

        if (!promoted) promoted = runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess

        if (promoted) {
            isForeground = true
            foregroundType = type
        } else {
            runCatching { notificationManager().notify(NOTIFICATION_ID, notification) }
        }
    }

    private fun serviceAction(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, RecordingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(title: String, text: String?): Notification {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val recording = _isRecording.value
        val paused = _isPaused.value
        val body = text ?: when {
            paused -> "Wstrzymano · ${formatClock(_elapsedSec.value)}"
            recording && isCallSession -> callWarning ?: "Nagrywanie rozmowy"
            recording -> "Nagrywanie w toku"
            else -> "Pracuję w tle"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            // Call warnings are longer than one line; don't truncate the fix.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (recording && !paused) {
            // Live timer straight in the shade — readable from the lock screen
            // without unlocking the phone.
            builder.setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() - elapsedMs())
        } else {
            builder.setUsesChronometer(false).setShowWhen(false)
        }

        if (recording) {
            if (paused) {
                builder.addAction(0, "Wznów", serviceAction(ACTION_RESUME))
            } else {
                builder.addAction(0, "Wstrzymaj", serviceAction(ACTION_PAUSE))
            }
            builder.addAction(0, "Zatrzymaj", serviceAction(ACTION_STOP))
        }
        return builder.build()
    }

    private fun ensureChannel() {
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Nagrywanie i transkrypcja", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Utrzymuje nagrywanie i wysyłkę transkrypcji przy wygaszonym ekranie"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun formatClock(totalSec: Int): String = "%d:%02d".format(totalSec / 60, totalSec % 60)

    // ---- Lifecycle -------------------------------------------------------------

    /**
     * Swiping the app out of recents must not end a recording — that is the
     * whole point of the background mode. Only a service with nothing left to
     * do shuts itself down here.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (_isRecording.value) {
            AppLog.d("Service", "Aplikacja zamknięta z listy zadań — nagrywanie działa dalej")
        } else {
            endWork()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isDestroying = true
        // Last chance to keep whatever was recorded: save it rather than
        // dropping the session on the floor.
        if (_isRecording.value) runCatching { stopRecording() }
        stopCallMonitor()
        stopAmplitudeMonitoring()
        stopTicker()
        idleShutdownJob?.cancel()
        releaseWakeLock()
        recorder?.stop()
        recorder = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { notificationManager().cancel(NOTIFICATION_ID) }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 421
        private const val CHANNEL_ID = "recording"
        private const val WAKE_LOCK_TAG = "nutka:recording"

        const val ACTION_STOP = "com.nutka.app.action.STOP"
        const val ACTION_PAUSE = "com.nutka.app.action.PAUSE"
        const val ACTION_RESUME = "com.nutka.app.action.RESUME"
        const val ACTION_DISCARD = "com.nutka.app.action.DISCARD"
        const val ACTION_RECORD_CALL = "com.nutka.app.action.RECORD_CALL"
        const val ACTION_CALL_ENDED = "com.nutka.app.action.CALL_ENDED"

        private const val CALL_TITLE = "Rozmowa telefoniczna"

        /**
         * True while a phone-call session is recording — lets the call
         * detector send [ACTION_CALL_ENDED] only when there is something to
         * stop, instead of spinning this service up on every hang-up.
         */
        @Volatile
        var callSessionActive: Boolean = false
            private set

        fun recordCallIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_RECORD_CALL)

        private const val TYPE_NONE = 0
        // FOREGROUND_SERVICE_TYPE_MICROPHONE only exists from API 30 — API 29
        // knows the typed startForeground() overload but not this type, and
        // passing it there is rejected. Below 30 we promote untyped instead.
        private val TYPE_MIC =
            if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else TYPE_NONE
        private val TYPE_DATA =
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else TYPE_NONE

        /** How many peaks the scrolling waveform keeps (≈ 2.4 s of history). */
        const val WAVEFORM_SAMPLES = 72
        // 33 ms ≈ 30 samples a second, so the trace slides smoothly instead of
        // stepping. getMaxAmplitude() reports the peak since the previous call,
        // so a shorter interval simply means finer peaks — nothing is lost.
        private const val SAMPLE_INTERVAL_MS = 33L
        private const val RELEASE = 0.28f
        private const val MAX_AMPLITUDE = 32767.0
        private const val MIN_DB = -50.0
        private const val NOISE_FLOOR = 110
        private const val MIN_SALVAGEABLE_BYTES = 4096L
        private const val IDLE_SHUTDOWN_MILLIS = 20_000L
        private const val MAX_SESSION_MILLIS = 12L * 60L * 60L * 1000L
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SESSION_FILE = "session_file"
        private const val KEY_SESSION_STARTED = "session_started"
        private const val KEY_SESSION_CALL = "session_call"
        private const val CALL_MONITOR_INTERVAL_MS = 1_000L
        // Real microphones never deliver exact zeros for this long; a
        // silenced capture delivers nothing else.
        private const val SILENCE_WARNING_MS = 8_000L
    }
}
