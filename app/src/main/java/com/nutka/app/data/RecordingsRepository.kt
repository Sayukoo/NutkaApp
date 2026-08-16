package com.nutka.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the recordings library as a single JSON file under the app's
 * private storage. No Room/SQLite — the dataset is small (voice memos, not
 * a large corpus) so a flat file keeps the dependency surface minimal.
 */
class RecordingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "recordings.json")
    val audioDir = File(appContext.filesDir, "audio").apply { mkdirs() }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _recordings = MutableStateFlow(loadFromDisk())
    val recordings: StateFlow<List<Recording>> = _recordings

    fun newAudioFile(id: String): File = File(audioDir, "$id.m4a")

    fun upsert(recording: Recording) {
        _recordings.value = _recordings.value
            .filterNot { it.id == recording.id }
            .let { listOf(recording) + it }
            .sortedByDescending { it.createdAtMillis }
        persist()
    }

    fun update(id: String, transform: (Recording) -> Recording) {
        _recordings.value = _recordings.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    fun find(id: String?): Recording? = _recordings.value.firstOrNull { it.id == id }

    fun delete(id: String) {
        _recordings.value.firstOrNull { it.id == id }?.filePath?.let { path ->
            val audio = File(path)
            audio.delete()
            File(audio.parentFile, audio.nameWithoutExtension + ".srt").delete()
        }
        _recordings.value = _recordings.value.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        val snapshot = _recordings.value
        scope.launch {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { arr.put(toJson(it)) }
                storeFile.writeText(arr.toString())
            }
        }
    }

    private fun loadFromDisk(): List<Recording> {
        if (!storeFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(storeFile.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun toJson(r: Recording): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("title", r.title)
        put("filePath", r.filePath)
        put("durationSec", r.durationSec)
        put("createdAtMillis", r.createdAtMillis)
        put("status", r.status.name)
        put("bookmarks", JSONArray(r.bookmarks))
        put("segments", JSONArray().apply {
            r.segments.forEach { s ->
                put(JSONObject().apply {
                    put("speaker", s.speaker)
                    put("timeSec", s.timeSec)
                    put("text", s.text)
                    put("speakerLabel", s.speakerLabel)
                })
            }
        })
        put("speakerNames", JSONObject(r.speakerNames))
        put("errorMessage", r.errorMessage)
    }

    private fun fromJson(o: JSONObject): Recording {
        val bookmarks = o.optJSONArray("bookmarks")?.let { arr ->
            (0 until arr.length()).map { arr.getInt(it) }
        } ?: emptyList()
        val segments = o.optJSONArray("segments")?.let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                Segment(
                    speaker = s.getString("speaker"),
                    timeSec = s.getInt("timeSec"),
                    text = s.getString("text"),
                    speakerLabel = s.optString("speakerLabel", null.toString()).takeIf { s.has("speakerLabel") && !s.isNull("speakerLabel") }
                )
            }
        } ?: emptyList()
        val names = o.optJSONObject("speakerNames")?.let { obj ->
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } ?: mapOf("a" to "Osoba 1", "b" to "Osoba 2")
        return Recording(
            id = o.getString("id"),
            title = o.optString("title", null.toString()).takeIf { o.has("title") && !o.isNull("title") },
            filePath = o.optString("filePath", null.toString()).takeIf { o.has("filePath") && !o.isNull("filePath") },
            durationSec = o.getInt("durationSec"),
            createdAtMillis = o.getLong("createdAtMillis"),
            status = runCatching { RecordingStatus.valueOf(o.getString("status")) }.getOrDefault(RecordingStatus.TRANSCRIBED),
            bookmarks = bookmarks,
            segments = segments,
            speakerNames = names,
            errorMessage = o.optString("errorMessage", null.toString()).takeIf { o.has("errorMessage") && !o.isNull("errorMessage") }
        )
    }
}
