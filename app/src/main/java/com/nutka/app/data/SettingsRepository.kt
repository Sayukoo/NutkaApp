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
    fun setPrimaryLanguage(v: String) = putString(KEY_LANGUAGE, v) { copy(primaryLanguage = v) }
    fun setTagAudioEvents(v: Boolean) = putBoolean(KEY_TAG_EVENTS, v) { copy(tagAudioEvents = v) }
    fun setIncludeSubtitles(v: Boolean) = putBoolean(KEY_SUBTITLES, v) { copy(includeSubtitles = v) }
    fun setNoVerbatim(v: Boolean) = putBoolean(KEY_NO_VERBATIM, v) { copy(noVerbatim = v) }
    fun setAssignSpeakersFromLibrary(v: Boolean) = putBoolean(KEY_SPEAKER_LIB, v) { copy(assignSpeakersFromLibrary = v) }
    fun setExpectedSpeakers(v: Int) {
        prefs.edit().putInt(KEY_EXPECTED_SPEAKERS, v).apply()
        _state.update { it.copy(expectedSpeakers = v) }
    }
    fun setKeyterms(v: List<String>) = putString(KEY_KEYTERMS, v.joinToString(",")) { copy(keyterms = v) }
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
