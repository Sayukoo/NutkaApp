package com.nutka.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nutka.app.data.AppLog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class Screen { RECORD, LIST, TRANSCRIPT, IMPORT, SETTINGS, LOG }

data class UiState(
    val screen: Screen = Screen.RECORD,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSec: Int = 0,
    val audioLevel: Float = 0f,
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
    val initialTranscriptSegmentIndex: Int? = null
) {
    val activeRecording: Recording? get() = recordings.firstOrNull { it.id == activeRecordingId }
}

class NutkaViewModel(app: Application) : AndroidViewModel(app) {

    private val recordingsRepo = RecordingsRepository(app)
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
    private val _audioLevel = MutableStateFlow(0f)
    private val _uploadProgress = MutableStateFlow<Pair<String, Int>?>(null)
    private val _initialTranscriptSearch = MutableStateFlow<String?>(null)
    private val _initialTranscriptSegmentIndex = MutableStateFlow<Int?>(null)
    private val transcriptionJobs = mutableMapOf<String, Job>()

    // A single flat combine() over all source flows — not 14 chained pairwise
    // combines. Each pairwise .merge() used to be its own combine() stage, so
    // e.g. a keystroke in a text field had to propagate through up to 14
    // sequential coroutine dispatch hops before uiState (and therefore the
    // TextField's controlled `value`) caught up; that lag is what made typed
    // text visibly jump/lag behind the cursor. combine()'s vararg overload
    // has no arity limit, so all flows can be combined in one hop.
    val uiState: StateFlow<UiState> = combine(
        _screen, _isRecording, _isPaused, _elapsedSec, _audioLevel, _bookmarkCount,
        recordingsRepo.recordings, _activeRecordingId, _editingSpeaker, _nameDraft,
        _toast, _importing, settingsRepo.state, AppLog.lines, _uploadProgress,
        _initialTranscriptSearch, _initialTranscriptSegmentIndex
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        UiState(
            screen = values[0] as Screen,
            isRecording = values[1] as Boolean,
            isPaused = values[2] as Boolean,
            elapsedSec = values[3] as Int,
            audioLevel = values[4] as Float,
            bookmarkCount = values[5] as Int,
            recordings = values[6] as List<Recording>,
            activeRecordingId = values[7] as String?,
            editingSpeaker = values[8] as String?,
            nameDraft = values[9] as String,
            toast = values[10] as String?,
            importing = values[11] as Boolean,
            settings = values[12] as SettingsState,
            logLines = values[13] as List<String>,
            uploadProgress = values[14] as Pair<String, Int>?,
            initialTranscriptSearch = values[15] as String?,
            initialTranscriptSegmentIndex = values[16] as Int?
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    // ---- Recording service binding -------------------------------------------------

    private var boundService: RecordingService? = null
    private var serviceBindingJobs: List<Job> = emptyList()
    private var pendingStartJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as RecordingService.LocalBinder).service()
            // A reconnect (e.g. after the process survived a service kill) must not
            // stack collectors on top of the old ones — cancel them first or every
            // rebind leaks another set of coroutines feeding stale values.
            serviceBindingJobs.forEach { it.cancel() }
            boundService = service
            serviceBindingJobs = listOf(
                viewModelScope.launch { service.elapsedSec.collect { _elapsedSec.value = it } },
                viewModelScope.launch { service.isPaused.collect { _isPaused.value = it } },
                viewModelScope.launch { service.audioLevel.collect { _audioLevel.value = it } },
                viewModelScope.launch { service.bookmarks.collect { _bookmarkCount.value = it.size } },
                viewModelScope.launch {
                    service.startFailed.collect { message ->
                        AppLog.d("Recording", "Start nagrywania nie powiódł się: $message")
                        _isRecording.value = false
                        _audioLevel.value = 0f
                        pendingStartId = null
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
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unbindService(connection) }
        super.onCleared()
    }

    // ---- Navigation -------------------------------------------------------------

    fun goRecord() { _screen.value = Screen.RECORD; _editingSpeaker.value = null }
    fun goList() { _screen.value = Screen.LIST; _editingSpeaker.value = null }
    fun goSettings() { _screen.value = Screen.SETTINGS; _editingSpeaker.value = null }
    fun goImport() { _screen.value = Screen.IMPORT; _importing.value = false }
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
            val file = recordingsRepo.newAudioFile(id)
            AppLog.d("Recording", "Start nagrywania $id -> ${file.name}")
            ContextCompat.startForegroundService(app0, Intent(app0, RecordingService::class.java))
            pendingStartId = id
            // The bound service reference may momentarily be null right after a cold
            // start; retry once bound (see connection callback) via this small poll.
            // If the recorder itself fails to start, service.startFailed (collected
            // above) resets isRecording and surfaces the error — nothing here needs
            // to check the return value. The job is cancelled by cancelRecording(),
            // otherwise a quick record→cancel tap would still spin up the mic.
            pendingStartJob = viewModelScope.launch {
                var attempts = 0
                while (boundService == null && attempts < 50 && pendingStartId != null) { delay(20); attempts++ }
                if (pendingStartId == id) boundService?.startRecording(file)
                else if (pendingStartId == null) runCatching { file.delete() }
            }
            _isRecording.value = true
        } else {
            pendingStartJob?.cancel()
            val service = boundService
            if (service == null) {
                // Binder lost mid-session — don't fabricate a broken entry from a
                // null file/stale timer; reset and tell the user what happened.
                AppLog.e("Recording", "Stop nagrywania bez podłączonego serwisu — sesja porzucona")
                pendingStartId = null
                _isRecording.value = false
                _elapsedSec.value = 0
                _bookmarkCount.value = 0
                showToast("Nie udało się zapisać nagrania")
                return
            }
            val bookmarkOffsets = service.bookmarks.value
            val file = service.stopRecording()
            val id = pendingStartId ?: return
            val durationSec = _elapsedSec.value
            val newRecording = Recording(
                id = id,
                title = null,
                filePath = file?.absolutePath,
                durationSec = durationSec,
                createdAtMillis = System.currentTimeMillis(),
                status = RecordingStatus.PROCESSING,
                bookmarks = bookmarkOffsets
            )
            recordingsRepo.upsert(newRecording)
            AppLog.d("Recording", "Stop nagrywania $id, czas ${durationSec}s, plik=${file?.absolutePath ?: "brak"}, rozmiar=${file?.length() ?: 0}B")
            _isRecording.value = false
            _activeRecordingId.value = id
            _screen.value = Screen.TRANSCRIPT
            finalizeTranscription(id, file)
        }
    }

    private var pendingStartId: String? = null

    fun togglePause() {
        val service = boundService ?: return
        if (_isPaused.value) service.resumeRecording() else service.pauseRecording()
    }

    fun cancelRecording() {
        pendingStartJob?.cancel()
        val service = boundService
        service?.cancelRecording()
        pendingStartId?.let { id ->
            recordingsRepo.delete(id)
            // The poll may not have reached startRecording() yet; make sure a fresh
            // empty file created for this session doesn't linger on disk.
            runCatching { recordingsRepo.newAudioFile(id).delete() }
        }
        pendingStartId = null
        _isRecording.value = false
        _isPaused.value = false
        _audioLevel.value = 0f
        _elapsedSec.value = 0
        _bookmarkCount.value = 0
        showToast("Anulowano nagranie")
    }

    fun addBookmark() {
        boundService?.addBookmark()
        showToast("Dodano zakładkę")
    }

    fun toggleBackgroundRecording() = settingsRepo.setBackgroundRecording(!settingsRepo.state.value.backgroundRecording)

    // ---- Transcription --------------------------------------------------------

    /**
     * The recording/import is already upserted into [recordingsRepo] — and
     * therefore already visible in the app and on local disk — *before*
     * this runs. Whatever happens to ElevenLabs here, that entry and its
     * audio file are never deleted; this only ever updates its status/text.
     *
     * Wraps the whole thing in [RecordingService.beginWork]/[endWork] so the
     * foreground-service promotion protects the upload even if the user
     * switches to another app mid-transcription — without it, Android
     * suspends the process's sockets in the background and the ElevenLabs
     * request dies with a "Broken pipe".
     */
    private fun finalizeTranscription(id: String, file: File?) {
        transcriptionJobs[id]?.cancel()
        val job = viewModelScope.launch {
            val settings = settingsRepo.state.value
            val hasKey = settings.elevenLabsApiKey.isNotBlank()
            val elevenLabsConfigured = hasKey && file != null
            AppLog.d("Transcription", "$id: wysyłanie do ElevenLabs=${elevenLabsConfigured}, język=${settings.primaryLanguage}, tagEvents=${settings.tagAudioEvents}")

            if (elevenLabsConfigured) {
                boundService?.beginWork("Nutka transkrybuje", "Przygotowywanie…")
                _uploadProgress.value = id to 0
            }

            val segments: List<Segment>
            try {
                segments = if (elevenLabsConfigured) {
                    when (val result = withContext(Dispatchers.IO) {
                        cloudTranscription.transcribe(file!!, settings) { percent ->
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
                    file == null -> "Nagranie bez pliku audio — nie można go transkrybować"
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
        val file = recording.filePath?.let { File(it) }
        recordingsRepo.update(id) { it.copy(status = RecordingStatus.PROCESSING, errorMessage = null) }
        finalizeTranscription(id, file)
    }

    fun deleteRecording(id: String) {
        AppLog.d("Recording", "Usunięto nagranie $id")
        transcriptionJobs[id]?.cancel()
        transcriptionJobs.remove(id)
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

    fun importAudio(uri: Uri) {
        val app0 = getApplication<Application>()
        _importing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val id = "r" + System.currentTimeMillis()
            val dest = recordingsRepo.newAudioFile(id)
            AppLog.d("Import", "Import $id <- $uri")
            runCatching {
                app0.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            }
            // Live recordings get a real durationSec from the elapsed-time counter
            // (see toggleRecord() above) — imports need to read it back from the
            // file instead. Without this, durationSec stayed 0 and the player/
            // waveform row in TranscriptScreen (gated on durationSec > 0) never
            // showed up for imported audio.
            val durationSec = readDurationSec(dest)
            val newRecording = Recording(
                id = id, title = null, filePath = dest.absolutePath, durationSec = durationSec,
                createdAtMillis = System.currentTimeMillis(), status = RecordingStatus.PROCESSING
            )
            recordingsRepo.upsert(newRecording)
            withContext(Dispatchers.Main) {
                _importing.value = false
                _activeRecordingId.value = id
                _screen.value = Screen.TRANSCRIPT
            }
            finalizeTranscription(id, dest)
        }
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

    fun transcriptText(): String {
        val rec = uiState.value.activeRecording ?: return ""
        return rec.segments.joinToString("\n") { seg ->
            val m = seg.timeSec / 60
            val s = seg.timeSec % 60
            val ts = "%d:%02d".format(m, s)
            "[$ts] ${rec.speakerNames[seg.speaker] ?: seg.speaker}: ${seg.text}"
        }
    }

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
