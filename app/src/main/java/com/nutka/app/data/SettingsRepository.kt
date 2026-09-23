package com.nutka.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SettingsState(
    // Notion export (mirrors the "Ustawienia" screen in the mockup)
    val notionToken: String = "",
    val notionDatabaseId: String = "",
    val autoNotion: Boolean = true,
    val backgroundRecording: Boolean = true,

    // Watch the phone's Downloads folder and transcribe anything new that lands
    // there. Off by default on purpose: every auto-import spends ElevenLabs
    // credit, so opting in is the user's call, not a default.
    val autoImportDownloads: Boolean = false,

    // Phone-call recording. Off by default, and even when on the default is to
    // ASK per call (a notification with a "Nagraj" button) rather than record
    // every call: the other side has to know they are being recorded, and that
    // is true for some calls, not all of them.
    val callRecording: Boolean = false,
    val autoRecordCalls: Boolean = false,

    // Transcription options — carried over from the "Transcribe files" upload
    // dialog screenshot, applied to both live recordings and imports.
    val primaryLanguage: String = "pl", // "pl" == Polski (domyślny)
    val tagAudioEvents: Boolean = true,
    val includeSubtitles: Boolean = false,
    val noVerbatim: Boolean = false,
    val assignSpeakersFromLibrary: Boolean = false,
    val expectedSpeakers: Int = 0, // 0 == let ElevenLabs auto-detect; otherwise a hint (num_speakers)
    val keyterms: List<String> = emptyList(),

    // ElevenLabs Speech-to-Text (optional). Empty == on-device recognizer only,
    // nothing leaves the phone. Set this to also transcribe imported files and
    // get real diarization/subtitles.
    val elevenLabsApiKey: String = ""
) {
    val notionConnected: Boolean get() = notionToken.isNotBlank()
}

/**
 * Thin SharedPreferences wrapper exposing settings as a single StateFlow.
 * Deliberately avoids DataStore/Proto to keep the dependency graph small —
 * this screen has a dozen scalar fields, not a growing schema.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("nutka_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readAll())
    val state: StateFlow<SettingsState> = _state

    private fun readAll() = SettingsState(
        notionToken = prefs.getString(KEY_NOTION_TOKEN, "") ?: "",
        notionDatabaseId = prefs.getString(KEY_NOTION_DB, "") ?: "",
        autoNotion = prefs.getBoolean(KEY_AUTO_NOTION, true),
        backgroundRecording = prefs.getBoolean(KEY_BG_RECORDING, true),
        autoImportDownloads = prefs.getBoolean(KEY_AUTO_IMPORT_DOWNLOADS, false),
        callRecording = prefs.getBoolean(KEY_CALL_RECORDING, false),
        autoRecordCalls = prefs.getBoolean(KEY_AUTO_RECORD_CALLS, false),
        primaryLanguage = prefs.getString(KEY_LANGUAGE, "pl") ?: "pl",
        tagAudioEvents = prefs.getBoolean(KEY_TAG_EVENTS, true),
        includeSubtitles = prefs.getBoolean(KEY_SUBTITLES, false),
        noVerbatim = prefs.getBoolean(KEY_NO_VERBATIM, false),
        assignSpeakersFromLibrary = prefs.getBoolean(KEY_SPEAKER_LIB, false),
        expectedSpeakers = prefs.getInt(KEY_EXPECTED_SPEAKERS, 0),
        keyterms = (prefs.getString(KEY_KEYTERMS, "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() },
        elevenLabsApiKey = prefs.getString(KEY_ELEVENLABS_KEY, "") ?: ""
    )

    fun setNotionToken(v: String) = putString(KEY_NOTION_TOKEN, v) { copy(notionToken = v) }
    fun setNotionDatabaseId(v: String) = putString(KEY_NOTION_DB, v) { copy(notionDatabaseId = v) }
    fun setAutoNotion(v: Boolean) = putBoolean(KEY_AUTO_NOTION, v) { copy(autoNotion = v) }
    fun setBackgroundRecording(v: Boolean) = putBoolean(KEY_BG_RECORDING, v) { copy(backgroundRecording = v) }
    fun setAutoImportDownloads(v: Boolean) = putBoolean(KEY_AUTO_IMPORT_DOWNLOADS, v) { copy(autoImportDownloads = v) }
    fun setCallRecording(v: Boolean) = putBoolean(KEY_CALL_RECORDING, v) { copy(callRecording = v) }
    fun setAutoRecordCalls(v: Boolean) = putBoolean(KEY_AUTO_RECORD_CALLS, v) { copy(autoRecordCalls = v) }

    /**
     * MediaStore ids of Downloads entries already pulled into the library.
     *
     * This is what stops the same file being transcribed twice — and, when
     * auto-import is switched on, it is seeded with everything already in the
     * folder so enabling the toggle doesn't upload the user's whole Downloads
     * history in one go. Capped so it can't grow without bound.
     */
    fun importedDownloadIds(): Set<String> =
        prefs.getStringSet(KEY_IMPORTED_DOWNLOADS, emptySet()) ?: emptySet()

    fun rememberImportedDownloads(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val merged = (importedDownloadIds() + ids).toList().takeLast(MAX_REMEMBERED_DOWNLOADS).toSet()
        prefs.edit().putStringSet(KEY_IMPORTED_DOWNLOADS, merged).apply()
    }
    fun setPrimaryLanguage(v: String) = putString(KEY_LANGUAGE, v) { copy(primaryLanguage = v) }
    fun setTagAudioEvents(v: Boolean) = putBoolean(KEY_TAG_EVENTS, v) { copy(tagAudioEvents = v) }
    fun setIncludeSubtitles(v: Boolean) = putBoolean(KEY_SUBTITLES, v) { copy(includeSubtitles = v) }
    fun setNoVerbatim(v: Boolean) = putBoolean(KEY_NO_VERBATIM, v) { copy(noVerbatim = v) }
    fun setAssignSpeakersFromLibrary(v: Boolean) = putBoolean(KEY_SPEAKER_LIB, v) { copy(assignSpeakersFromLibrary = v) }
    fun setExpectedSpeakers(v: Int) {
        prefs.edit().putInt(KEY_EXPECTED_SPEAKERS, v).apply()
        _state.update { it.copy(expectedSpeakers = v) }
    }
    /**
     * Keyterms are stored as one comma-joined string, so a term containing a
     * comma would come back split in two on the next read. Strip them on the
     * way in — a keyterm is a word or phrase, never a list.
     */
    fun setKeyterms(v: List<String>) {
        val cleaned = v.map { it.replace(",", " ").trim() }.filter { it.isNotEmpty() }.distinct()
        putString(KEY_KEYTERMS, cleaned.joinToString(",")) { copy(keyterms = cleaned) }
    }
    fun setElevenLabsApiKey(v: String) = putString(KEY_ELEVENLABS_KEY, v) { copy(elevenLabsApiKey = v) }

    private inline fun putString(key: String, value: String, crossinline reducer: SettingsState.() -> SettingsState) {
        prefs.edit().putString(key, value).apply()
        _state.update { it.reducer() }
    }

    private inline fun putBoolean(key: String, value: Boolean, crossinline reducer: SettingsState.() -> SettingsState) {
        prefs.edit().putBoolean(key, value).apply()
        _state.update { it.reducer() }
    }

    companion object {
        private const val KEY_NOTION_TOKEN = "notion_token"
        private const val KEY_NOTION_DB = "notion_database_id"
        private const val KEY_AUTO_NOTION = "auto_notion"
        private const val KEY_BG_RECORDING = "background_recording"
        private const val KEY_AUTO_IMPORT_DOWNLOADS = "auto_import_downloads"
        private const val KEY_IMPORTED_DOWNLOADS = "imported_download_ids"
        private const val KEY_CALL_RECORDING = "call_recording"
        private const val KEY_AUTO_RECORD_CALLS = "auto_record_calls"
        private const val MAX_REMEMBERED_DOWNLOADS = 500
        private const val KEY_LANGUAGE = "primary_language"
        private const val KEY_TAG_EVENTS = "tag_audio_events"
        private const val KEY_SUBTITLES = "include_subtitles"
        private const val KEY_NO_VERBATIM = "no_verbatim"
        private const val KEY_SPEAKER_LIB = "assign_speakers_from_library"
        private const val KEY_EXPECTED_SPEAKERS = "expected_speakers"
        private const val KEY_KEYTERMS = "keyterms"
        private const val KEY_ELEVENLABS_KEY = "elevenlabs_api_key"
    }
}
