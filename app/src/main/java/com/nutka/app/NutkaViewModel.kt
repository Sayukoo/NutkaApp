package com.nutka.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nutka.app.data.AppLog
import com.nutka.app.data.DownloadedAudio
import com.nutka.app.data.DownloadsAudioScanner
import com.nutka.app.data.ElevenLabsTranscriptionService
import com.nutka.app.data.NotionExportResult
import com.nutka.app.data.NotionExportService
import com.nutka.app.data.Recording
import com.nutka.app.data.RecordingStatus
import com.nutka.app.data.RecordingsRepository
import com.nutka.app.data.Segment
import com.nutka.app.data.SettingsRepository
import com.nutka.app.data.SettingsState
import com.nutka.app.data.TranscriptionResult
import com.nutka.app.service.RecordingService
import com.nutka.app.ui.components.computeSpeakerStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { RECORD, LIST, TRANSCRIPT, IMPORT, SETTINGS, LOG }

data class UiState(
    val screen: Screen = Screen.RECORD,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSec: Int = 0,
    val bookmarkCount: Int = 0,
    val recordings: List<Recording> = emptyList(),
    val activeRecordingId: String? = null,
    val editingSpeaker: String? = null, // "a" / "b" / null
    val nameDraft: String = "",
    val toast: String? = null,
    val importing: Boolean = false,
    val settings: SettingsState = SettingsState(),
    val logLines: List<String> = emptyList(),
    val uploadProgress: Pair<String, Int>? = null, // (recordingId, percent) while uploading to ElevenLabs
    val initialTranscriptSearch: String? = null,
    val initialTranscriptSegmentIndex: Int? = null,
    val downloads: List<DownloadedAudio> = emptyList(),
    val downloadsPermissionGranted: Boolean = false
) {
    val activeRecording: Recording? get() = recordings.firstOrNull { it.id == activeRecordingId }
}

class NutkaViewModel(app: Application) : AndroidViewModel(app) {

    private val recordingsRepo = RecordingsRepository.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val cloudTranscription = ElevenLabsTranscriptionService()
    private val notionExport = NotionExportService()

    private val _screen = MutableStateFlow(Screen.RECORD)
    private val _isRecording = MutableStateFlow(false)
    private val _bookmarkCount = MutableStateFlow(0)
    private val _activeRecordingId = MutableStateFlow<String?>(null)
    private val _editingSpeaker = MutableStateFlow<String?>(null)
    private val _nameDraft = MutableStateFlow("")
    private val _toast = MutableStateFlow<String?>(null)
    private val _importing = MutableStateFlow(false)
    private val _elapsedSec = MutableStateFlow(0)
    private val _isPaused = MutableStateFlow(false)
    private val _uploadProgress = MutableStateFlow<Pair<String, Int>?>(null)
    private val _initialTranscriptSearch = MutableStateFlow<String?>(null)
    private val _initialTranscriptSegmentIndex = MutableStateFlow<Int?>(null)
    private val _downloads = MutableStateFlow<List<DownloadedAudio>>(emptyList())
    private val _downloadsPermission = MutableStateFlow(false)
    private val transcriptionJobs = mutableMapOf<String, Job>()
    private val transcriptionAttempted = mutableSetOf<String>()

    /**
     * Live microphone level and its rolling history are kept OUT of [uiState]
     * on purpose. They update ~22×/s, and folding them into the single
     * combined state object would re-emit the whole UiState — and therefore
     * recompose every screen — at that rate. As separate flows, only the
     * waveform/pulse composables that read them do any work.
     */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _waveform = MutableStateFlow<List<Float>>(emptyList())
    val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

    // A single flat combine() over all source flows — not 14 chained pairwise
    // combines. Each pairwise .merge() used to be its own combine() stage, so
    // e.g. a keystroke in a text field had to propagate through up to 14
    // sequential coroutine dispatch hops before uiState (and therefore the
    // TextField's controlled `value`) caught up; that lag is what made typed
    // text visibly jump/lag behind the cursor. combine()'s vararg overload
    // has no arity limit, so all flows can be combined in one hop.
    val uiState: StateFlow<UiState> = combine(
        _screen, _isRecording, _isPaused, _elapsedSec, _bookmarkCount,
        recordingsRepo.recordings, _activeRecordingId, _editingSpeaker, _nameDraft,
        _toast, _importing, settingsRepo.state, AppLog.lines, _uploadProgress,
        _initialTranscriptSearch, _initialTranscriptSegmentIndex,
        _downloads, _downloadsPermission
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        UiState(
            screen = values[0] as Screen,
            isRecording = values[1] as Boolean,
            isPaused = values[2] as Boolean,
            elapsedSec = values[3] as Int,
            bookmarkCount = values[4] as Int,
            recordings = values[5] as List<Recording>,
            activeRecordingId = values[6] as String?,
            editingSpeaker = values[7] as String?,
            nameDraft = values[8] as String,
            toast = values[9] as String?,
            importing = values[10] as Boolean,
            settings = values[11] as SettingsState,
            logLines = values[12] as List<String>,
            uploadProgress = values[13] as Pair<String, Int>?,
            initialTranscriptSearch = values[14] as String?,
            initialTranscriptSegmentIndex = values[15] as Int?,
            downloads = values[16] as List<DownloadedAudio>,
            downloadsPermissionGranted = values[17] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    // ---- Recording service binding -------------------------------------------------

    private var boundService: RecordingService? = null
    private var serviceBindingJobs: List<Job> = emptyList()
    private var pendingStartJob: Job? = null

    /** True between tapping record and the service confirming the mic is live. */
    private var startPending = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? RecordingService.LocalBinder)?.service() ?: return
            // A reconnect (e.g. after the process survived a service kill) must not
            // stack collectors on top of the old ones — cancel them first or every
            // rebind leaks another set of coroutines feeding stale values.
            serviceBindingJobs.forEach { it.cancel() }
            boundService = service
            serviceBindingJobs = listOf(
                // The service — not this ViewModel — is the source of truth for
                // whether a session is live. That is what lets the UI be closed
                // and reopened mid-recording and still come back showing the
                // running session instead of an idle "ready" screen (which
                // previously let you start a second, overlapping recording).
                viewModelScope.launch {
                    service.isRecording.collect { running ->
                        if (running) {
                            startPending = false
                            _isRecording.value = true
                        } else if (!startPending) {
                            _isRecording.value = false
                        }
                    }
                },
                viewModelScope.launch { service.elapsedSec.collect { _elapsedSec.value = it } },
                viewModelScope.launch { service.isPaused.collect { _isPaused.value = it } },
                viewModelScope.launch { service.audioLevel.collect { _audioLevel.value = it } },
                viewModelScope.launch { service.waveform.collect { _waveform.value = it } },
                viewModelScope.launch { service.bookmarks.collect { _bookmarkCount.value = it.size } },
                viewModelScope.launch {
                    // Fired for every finished session, including a stop tapped
                    // on the notification while the app was in the background.
                    service.sessionFinished.collect { id ->
                        _activeRecordingId.value = id
                        _screen.value = Screen.TRANSCRIPT
                    }
                },
                viewModelScope.launch {
                    service.startFailed.collect { message ->
                        AppLog.d("Recording", "Start nagrywania nie powiódł się: $message")
                        startPending = false
                        _isRecording.value = false
                        _audioLevel.value = 0f
                        _waveform.value = emptyList()
                        showToast("Nagrywanie nie powiodło się: $message")
                    }
                }
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }

    init {
        val app0 = getApplication<Application>()
        AppLog.init(app0)
        app0.bindService(Intent(app0, RecordingService::class.java), connection, Context.BIND_AUTO_CREATE)

        // Anything sitting in PROCESSING needs transcribing: a recording just
        // stopped (in-app or from the notification), a fresh import, or a
        // session salvaged after the process was killed mid-recording. Driving
        // it off the repository instead of the stop/import call sites means a
        // recording is never left stranded just because the UI wasn't alive
        // when it was saved.
        viewModelScope.launch {
            recordingsRepo.recordings.collect { list ->
                list.forEach { recording ->
                    if (recording.status != RecordingStatus.PROCESSING) return@forEach
                    if (transcriptionJobs.containsKey(recording.id)) return@forEach
                    if (!transcriptionAttempted.add(recording.id)) return@forEach
                    finalizeTranscription(recording.id, recording.filePath?.let(::File))
                }
            }
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unbindService(connection) }
        serviceBindingJobs.forEach { it.cancel() }
        serviceBindingJobs = emptyList()
        // Drop the Service reference with the binding — holding it past unbind
        // keeps a dead Context alive for as long as the ViewModel object lingers.
        boundService = null
        super.onCleared()
    }

    // ---- Navigation -------------------------------------------------------------

    fun goRecord() { _screen.value = Screen.RECORD; _editingSpeaker.value = null }
    fun goList() { _screen.value = Screen.LIST; _editingSpeaker.value = null }
    fun goSettings() { _screen.value = Screen.SETTINGS; _editingSpeaker.value = null }
    fun goImport() { _screen.value = Screen.IMPORT; _importing.value = false; refreshDownloads() }
    fun goLog() { _screen.value = Screen.LOG }
    fun openRecording(id: String, initialSearch: String? = null, initialSegmentIndex: Int? = null) {
        _activeRecordingId.value = id
        _editingSpeaker.value = null
        _initialTranscriptSearch.value = initialSearch
        _initialTranscriptSegmentIndex.value = initialSegmentIndex
        _screen.value = Screen.TRANSCRIPT
    }

    fun consumeInitialSearch() {
        _initialTranscriptSearch.value = null
        _initialTranscriptSegmentIndex.value = null
    }

    // ---- Recording ----------------------------------------------------------------

    fun toggleRecord() {
        val app0 = getApplication<Application>()
        if (!_isRecording.value) {
            val id = "r" + System.currentTimeMillis()
            startPending = true
            _isRecording.value = true
            _elapsedSec.value = 0
            _bookmarkCount.value = 0
            _waveform.value = emptyList()

            runCatching {
                ContextCompat.startForegroundService(app0, Intent(app0, RecordingService::class.java))
            }.onFailure { e ->
                AppLog.e("Recording", "Nie udało się wystartować usługi nagrywania", e)
            }

            // The bound service reference may momentarily be null right after a
            // cold start; retry once bound (see the connection callback above).
            // If the recorder itself fails to start, service.startFailed resets
            // the flags and surfaces the error.
            pendingStartJob?.cancel()
            pendingStartJob = viewModelScope.launch {
                var attempts = 0
                while (boundService == null && attempts < 150 && startPending) {
                    delay(20)
                    attempts++
                }
                if (!startPending) return@launch
                val service = boundService
                if (service == null) {
                    startPending = false
                    _isRecording.value = false
                    AppLog.e("Recording", "Usługa nagrywania nie podłączyła się w czasie")
                    showToast("Nie udało się uruchomić nagrywania")
                    return@launch
                }
                service.startRecording(id)
            }
        } else {
            pendingStartJob?.cancel()
            startPending = false
            val service = boundService
            if (service == null) {
                // Binder lost mid-session — don't fabricate a broken entry from a
                // null file/stale timer; reset and tell the user what happened.
                AppLog.e("Recording", "Stop nagrywania bez podłączonego serwisu — sesja porzucona")
                _isRecording.value = false
                _elapsedSec.value = 0
                _bookmarkCount.value = 0
                showToast("Nie udało się zapisać nagrania")
                return
            }
            // The service saves the entry itself and emits sessionFinished, which
            // navigates to the transcript; transcription starts from the
            // PROCESSING watcher in init.
            if (service.stopRecording() == null) {
                // Stop landed before the mic session ever came up (a start/stop
                // double-tap). The service's isRecording was never true, so it
                // won't emit a change to bring the UI back — reset it here.
                _isRecording.value = false
                _elapsedSec.value = 0
                _bookmarkCount.value = 0
                _waveform.value = emptyList()
            }
        }
    }

    fun togglePause() {
        val service = boundService ?: return
        if (_isPaused.value) service.resumeRecording() else service.pauseRecording()
    }

    fun cancelRecording() {
        val app0 = getApplication<Application>()
        pendingStartJob?.cancel()
        startPending = false
        val service = boundService
        if (service != null) {
            service.cancelRecording()
        } else {
            // Cancelled in the split second before the binder arrived. The
            // service was already asked to start via startForegroundService(),
            // and Android crashes the app if such a service never reaches
            // startForeground() — so release the start obligation explicitly.
            runCatching { app0.stopService(Intent(app0, RecordingService::class.java)) }
        }
        _isRecording.value = false
        _isPaused.value = false
        _audioLevel.value = 0f
        _waveform.value = emptyList()
        _elapsedSec.value = 0
        _bookmarkCount.value = 0
        showToast("Anulowano nagranie")
    }

    fun addBookmark() {
        boundService?.addBookmark()
        showToast("Dodano zakładkę")
    }

    fun toggleBackgroundRecording() = settingsRepo.setBackgroundRecording(!settingsRepo.state.value.backgroundRecording)
    fun setCallRecording(enabled: Boolean) = settingsRepo.setCallRecording(enabled)
    fun toggleAutoRecordCalls() = settingsRepo.setAutoRecordCalls(!settingsRepo.state.value.autoRecordCalls)

    // ---- Transcription --------------------------------------------------------

    /**
     * The recording/import is already in [recordingsRepo] — and therefore
     * already visible in the app and on local disk — *before* this runs.
     * Whatever happens to ElevenLabs here, that entry and its audio file are
     * never deleted; this only ever updates its status/text.
     *
     * Wraps the whole thing in [RecordingService.beginWork]/[endWork] so the
     * foreground-service promotion protects the upload even if the user
     * switches to another app mid-transcription — without it, Android
     * suspends background sockets and the request dies with a "Broken pipe".
     */
    private fun finalizeTranscription(id: String, file: File?) {
        transcriptionJobs[id]?.cancel()
        val job = viewModelScope.launch {
            // A phone call is two people by definition. Telling Scribe so
            // matters here more than anywhere: the caller comes through the
            // loudspeaker, quieter than the user, and auto-detection tends to
            // fold a quiet voice into the loud one.
            val settings = settingsRepo.state.value.let { s ->
                if (recordingsRepo.find(id)?.isPhoneCall == true && s.expectedSpeakers == 0) s.copy(expectedSpeakers = 2) else s
            }
            val hasKey = settings.elevenLabsApiKey.isNotBlank()
            val audio = file?.takeIf { it.exists() && it.length() > 0L }
            val elevenLabsConfigured = hasKey && audio != null
            AppLog.d("Transcription", "$id: wysyłanie do ElevenLabs=${elevenLabsConfigured}, język=${settings.primaryLanguage}, tagEvents=${settings.tagAudioEvents}")

            if (elevenLabsConfigured) {
                boundService?.beginWork("Nutka transkrybuje", "Przygotowywanie…")
                _uploadProgress.value = id to 0
            }

            val segments: List<Segment>
            try {
                segments = if (elevenLabsConfigured) {
                    when (val result = withContext(Dispatchers.IO) {
                        cloudTranscription.transcribe(audio, settings) { percent ->
                            _uploadProgress.value = id to percent
                            val label = if (percent < 100) "Wysyłanie… $percent%" else "Przetwarzanie po stronie ElevenLabs…"
                            runCatching { boundService?.updateWork(label) }
                        }
                    }) {
                        is TranscriptionResult.Success -> {
                            AppLog.d("Transcription", "$id: ElevenLabs zwrócił ${result.segments.size} segmentów")
                            result.segments
                        }
                        is TranscriptionResult.Failure -> {
                            AppLog.e("Transcription", "$id: błąd ElevenLabs — ${result.message}")
                            recordingsRepo.update(id) {
                                it.copy(status = RecordingStatus.ERROR, errorMessage = "Nagranie zapisane lokalnie. ${result.message}")
                            }
                            return@launch
                        }
                    }
                } else emptyList()
            } catch (e: Throwable) {
                AppLog.e("Transcription", "$id: nieoczekiwany błąd podczas transkrypcji", e)
                recordingsRepo.update(id) {
                    it.copy(status = RecordingStatus.ERROR, errorMessage = "Błąd transkrypcji: ${e.message}")
                }
                return@launch
            } finally {
                // Only clear progress that still belongs to THIS recording — a
                // concurrent second upload must not lose its indicator.
                if (_uploadProgress.value?.first == id) _uploadProgress.value = null
            }

            if (segments.isNotEmpty()) {
                recordingsRepo.update(id) { it.copy(status = RecordingStatus.TRANSCRIBED, segments = segments, errorMessage = null) }
                if (settingsRepo.state.value.autoNotion && settingsRepo.state.value.notionConnected) {
                    sendToNotion(id)
                }
            } else {
                val msg = when {
                    !hasKey -> "Nagranie zapisane lokalnie. Dodaj klucz API ElevenLabs w Ustawieniach, aby je transkrybować"
                    audio == null -> "Nagranie bez pliku audio — nie można go transkrybować"
                    else -> "Nagranie zapisane lokalnie. ElevenLabs nie wykrył mowy w tym nagraniu"
                }
                AppLog.d("Transcription", "$id: brak segmentów ($msg)")
                recordingsRepo.update(id) { it.copy(status = RecordingStatus.ERROR, errorMessage = msg) }
            }
        }
        transcriptionJobs[id] = job
        job.invokeOnCompletion {
            if (transcriptionJobs[id] == job) transcriptionJobs.remove(id)
            maybeEndForegroundWork()
        }
    }

    /** Drops the foreground promotion once nothing (recording, upload) needs it anymore. */
    private fun maybeEndForegroundWork() {
        if (!_isRecording.value && transcriptionJobs.isEmpty()) {
            runCatching { boundService?.endWork() }
        }
    }

    fun cancelTranscription(id: String) {
        transcriptionJobs[id]?.cancel()
        transcriptionJobs.remove(id)
        if (_uploadProgress.value?.first == id) _uploadProgress.value = null
        maybeEndForegroundWork()
        recordingsRepo.update(id) {
            it.copy(status = RecordingStatus.ERROR, errorMessage = "Transkrypcja została anulowana")
        }
        showToast("Anulowano transkrypcję")
    }

    fun retryTranscription(id: String) {
        AppLog.d("Transcription", "$id: ponowna próba")
        val recording = recordingsRepo.find(id) ?: return
        val file = recording.filePath?.let { File(it) } ?: recordingsRepo.audioFileFor(id)
        // Claim the id first so the PROCESSING watcher doesn't also fire for it.
        transcriptionAttempted.add(id)
        recordingsRepo.update(id) { it.copy(status = RecordingStatus.PROCESSING, errorMessage = null) }
        finalizeTranscription(id, file)
    }

    fun deleteRecording(id: String) {
        AppLog.d("Recording", "Usunięto nagranie $id")
        transcriptionJobs[id]?.cancel()
        transcriptionJobs.remove(id)
        transcriptionAttempted.remove(id)
        maybeEndForegroundWork()
        recordingsRepo.delete(id)
        if (_activeRecordingId.value == id) {
            _activeRecordingId.value = null
            _screen.value = Screen.LIST
        }
        showToast("Usunięto nagranie")
    }

    fun sendToNotion(id: String? = null) {
        val targetId = id ?: _activeRecordingId.value ?: return
        viewModelScope.launch {
            recordingsRepo.update(targetId) { it.copy(status = RecordingStatus.SENDING) }
            val recording = recordingsRepo.find(targetId) ?: return@launch
            val settings = settingsRepo.state.value
            val result = withContext(Dispatchers.IO) { notionExport.export(recording, settings) }
            when (result) {
                is NotionExportResult.Success -> {
                    AppLog.d("Notion", "$targetId: wysłano")
                    recordingsRepo.update(targetId) { it.copy(status = RecordingStatus.SENT) }
                    if (id == null) showToast("Wysłano do Notion")
                }
                is NotionExportResult.Failure -> {
                    AppLog.d("Notion", "$targetId: błąd — ${result.message}")
                    recordingsRepo.update(targetId) { it.copy(status = RecordingStatus.TRANSCRIBED, errorMessage = result.message) }
                    if (id == null) showToast(result.message)
                }
            }
        }
    }

    // ---- Import -----------------------------------------------------------------

    /**
     * @param navigateToTranscript false for background auto-imports, which must
     *   not yank the user off whatever screen they are on — possibly several
     *   times in a row when more than one new file turned up.
     */
    fun importAudio(uri: Uri, navigateToTranscript: Boolean = true) {
        val app0 = getApplication<Application>()
        _importing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val id = "r" + System.currentTimeMillis()
            // Keep the source container's extension: ElevenLabs is told the
            // content type from it, so copying an .mp3 into a file called
            // ".m4a" made the upload announce audio/mp4 for MPEG data.
            val dest = recordingsRepo.newAudioFile(id, resolveExtension(uri))
            AppLog.d("Import", "Import $id <- $uri -> ${dest.name}")
            val copied = runCatching {
                app0.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: error("Nie udało się otworzyć pliku")
            }
            if (copied.isFailure || !dest.exists() || dest.length() == 0L) {
                AppLog.e("Import", "$id: kopiowanie pliku nie powiodło się", copied.exceptionOrNull())
                runCatching { dest.delete() }
                withContext(Dispatchers.Main) {
                    _importing.value = false
                    showToast("Nie udało się zaimportować pliku")
                }
                return@launch
            }
            // Live recordings get a real durationSec from the elapsed-time counter
            // (see the service) — imports need to read it back from the file
            // instead. Without this, durationSec stayed 0 and the player/waveform
            // row in TranscriptScreen (gated on durationSec > 0) never showed up.
            val durationSec = readDurationSec(dest)
            recordingsRepo.upsert(
                Recording(
                    id = id, title = null, filePath = dest.absolutePath, durationSec = durationSec,
                    createdAtMillis = System.currentTimeMillis(), status = RecordingStatus.PROCESSING
                )
            )
            withContext(Dispatchers.Main) {
                _importing.value = false
                if (navigateToTranscript) {
                    _activeRecordingId.value = id
                    _screen.value = Screen.TRANSCRIPT
                }
            }
            // Transcription is kicked off by the PROCESSING watcher in init.
        }
    }

    // ---- Folder "Pobrane" ---------------------------------------------------------

    /** Re-reads the Downloads folder (and the permission state) for the import screen. */
    fun refreshDownloads() {
        val app0 = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val granted = DownloadsAudioScanner.hasPermission(app0)
            _downloadsPermission.value = granted
            _downloads.value = if (granted) DownloadsAudioScanner.scan(app0) else emptyList()
        }
    }

    /** One-tap import of a file the user picked from the Downloads list. */
    fun importFromDownloads(item: DownloadedAudio) {
        settingsRepo.rememberImportedDownloads(listOf(item.id))
        AppLog.d("Pobrane", "Import z Pobranych: ${item.name}")
        importAudio(item.uri)
    }

    fun toggleAutoImportDownloads() {
        val app0 = getApplication<Application>()
        val enabling = !settingsRepo.state.value.autoImportDownloads
        settingsRepo.setAutoImportDownloads(enabling)
        if (!enabling) return
        viewModelScope.launch(Dispatchers.IO) {
            // Everything already sitting in Downloads counts as "seen", so
            // switching this on doesn't upload the user's whole history (and
            // their whole ElevenLabs balance) in one go. Only files that appear
            // from now on get pulled in.
            settingsRepo.rememberImportedDownloads(DownloadsAudioScanner.scan(app0).map { it.id })
            withContext(Dispatchers.Main) {
                showToast("Nowe pliki z Pobranych będą transkrybowane automatycznie")
            }
        }
    }

    /** Imports anything new in Downloads — only when the user opted in. */
    fun syncDownloads() {
        val app0 = getApplication<Application>()
        if (!settingsRepo.state.value.autoImportDownloads) return
        if (!DownloadsAudioScanner.hasPermission(app0)) return
        viewModelScope.launch(Dispatchers.IO) {
            val seen = settingsRepo.importedDownloadIds()
            val fresh = DownloadsAudioScanner.scan(app0).filterNot { it.id in seen }
            if (fresh.isEmpty()) return@launch
            settingsRepo.rememberImportedDownloads(fresh.map { it.id })
            AppLog.d("Pobrane", "Auto-import: ${fresh.size} nowych plików")
            withContext(Dispatchers.Main) {
                showToast(
                    if (fresh.size == 1) "Nowy plik z Pobranych — transkrybuję"
                    else "${fresh.size} nowe pliki z Pobranych — transkrybuję"
                )
            }
            fresh.forEach { importAudio(it.uri, navigateToTranscript = false) }
        }
    }

    /** Called from the Activity's onResume: the folder may have changed while away. */
    fun onAppResumed() {
        refreshDownloads()
        syncDownloads()
    }

    /** Best-effort source extension for [uri]: display name first, then MIME type. */
    private fun resolveExtension(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val fromName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.substringAfterLast('.', "")?.lowercase()
        if (!fromName.isNullOrBlank() && fromName.length in 2..4) return fromName

        val fromMime = runCatching {
            resolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        }.getOrNull()
        return fromMime?.lowercase()?.takeIf { it.isNotBlank() } ?: "m4a"
    }

    private fun readDurationSec(file: File): Int {
        val retriever = android.media.MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (ms / 1000).toInt()
        }.getOrDefault(0).also { runCatching { retriever.release() } }
    }

    // ---- Transcript editing -------------------------------------------------------

    fun setTitle(value: String) {
        val id = _activeRecordingId.value ?: return
        recordingsRepo.update(id) { it.copy(title = value) }
    }

    fun startEditSpeaker(role: String) {
        val current = uiState.value.activeRecording?.speakerNames?.get(role) ?: defaultSpeakerName(role)
        _editingSpeaker.value = role
        _nameDraft.value = current
    }

    fun setNameDraft(value: String) { _nameDraft.value = value }

    fun commitName() {
        val role = _editingSpeaker.value ?: return
        val id = _activeRecordingId.value ?: return
        val value = _nameDraft.value.trim().ifEmpty { defaultSpeakerName(role) }
        recordingsRepo.update(id) { it.copy(speakerNames = it.speakerNames + (role to value)) }
        _editingSpeaker.value = null
    }

    fun cancelEditSpeaker() { _editingSpeaker.value = null }

    private fun defaultSpeakerName(role: String) = if (role == "a") "Osoba 1" else "Osoba 2"

    /**
     * The whole note as plain text — what "Kopiuj" and "Udostępnij" put on the
     * clipboard. Opens with a summary line and, for a conversation, the share
     * of speaking time per person in percent, so the split is readable straight
     * from the pasted text instead of only in the app's bar chart.
     */
    fun transcriptText(): String {
        val rec = uiState.value.activeRecording ?: return ""

        val header = mutableListOf<String>()
        val title = rec.title?.takeIf { it.isNotBlank() } ?: "Notatka"
        header += "$title · ${clockLabel(rec.durationSec)} · ${headerDateFormat.format(Date(rec.createdAtMillis))}"

        val stats = computeSpeakerStats(rec.segments, rec.speakerNames, rec.durationSec)
        if (stats.size >= 2) {
            header += "Podział czasu: " + stats.joinToString("  ·  ") {
                "${it.name} ${it.percent}% (${clockLabel(it.seconds)})"
            }
        }

        val body = rec.segments.joinToString("\n") { seg ->
            "[${clockLabel(seg.timeSec)}] ${rec.speakerNames[seg.speaker] ?: seg.speaker}: ${seg.text}"
        }
        return header.joinToString("\n") + "\n\n" + body
    }

    private fun clockLabel(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

    private val headerDateFormat = SimpleDateFormat("d.MM.yyyy, HH:mm", Locale.forLanguageTag("pl"))

    fun showToast(message: String) {
        // Token per show: without it, two identical messages in a row would let the
        // first timer clear the second toast before its own delay elapsed.
        val token = ++toastToken
        _toast.value = message
        viewModelScope.launch {
            delay(1800)
            if (_toast.value == message && token == toastToken) _toast.value = null
        }
    }

    private var toastToken = 0

    // ---- Settings ------------------------------------------------------------

    fun setNotionToken(v: String) = settingsRepo.setNotionToken(v)
    fun setNotionDatabaseId(v: String) = settingsRepo.setNotionDatabaseId(v)
    fun disconnectNotion() { settingsRepo.setNotionToken(""); settingsRepo.setNotionDatabaseId("") }
    fun toggleAutoNotion() = settingsRepo.setAutoNotion(!settingsRepo.state.value.autoNotion)
    fun setPrimaryLanguage(v: String) = settingsRepo.setPrimaryLanguage(v)
    fun toggleTagAudioEvents() = settingsRepo.setTagAudioEvents(!settingsRepo.state.value.tagAudioEvents)
    fun toggleIncludeSubtitles() = settingsRepo.setIncludeSubtitles(!settingsRepo.state.value.includeSubtitles)
    fun toggleNoVerbatim() = settingsRepo.setNoVerbatim(!settingsRepo.state.value.noVerbatim)
    fun toggleAssignSpeakersFromLibrary() = settingsRepo.setAssignSpeakersFromLibrary(!settingsRepo.state.value.assignSpeakersFromLibrary)
    fun setExpectedSpeakers(count: Int) = settingsRepo.setExpectedSpeakers(count)
    fun setKeyterms(v: List<String>) = settingsRepo.setKeyterms(v)
    fun setElevenLabsApiKey(v: String) = settingsRepo.setElevenLabsApiKey(v)

    // ---- Log -------------------------------------------------------------------

    fun clearLog() = AppLog.clear()
}
