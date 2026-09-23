package com.nutka.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the recordings library as a single JSON file under the app's
 * private storage. No Room/SQLite — the dataset is small (voice memos, not
 * a large corpus) so a flat file keeps the dependency surface minimal.
 */
class RecordingsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "recordings.json")
    val audioDir = File(appContext.filesDir, "audio").apply { mkdirs() }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _recordings = MutableStateFlow(loadFromDisk())
    val recordings: StateFlow<List<Recording>> = _recordings

    fun newAudioFile(id: String, extension: String = "m4a"): File =
        File(audioDir, "$id.${extension.trim('.').ifBlank { "m4a" }}")

    /** Any audio file already on disk for [id], whatever container it ended up in. */
    fun audioFileFor(id: String): File? =
        audioDir.listFiles()?.firstOrNull { it.nameWithoutExtension == id && it.extension != "srt" }

    /** Removes every on-disk artefact for [id] (audio in any container + its .srt). */
    fun deleteAudioFor(id: String) {
        audioDir.listFiles()?.filter { it.nameWithoutExtension == id }?.forEach { runCatching { it.delete() } }
    }

    // All three mutators go through StateFlow.update {}, which retries on a
    // compare-and-set miss. A plain `value = value.map { … }` is a
    // read-modify-write, and the recording service now writes from its own
    // background thread while the ViewModel updates statuses on the main one —
    // two of those interleaving would silently drop whichever landed first,
    // i.e. lose a just-finished recording.
    fun upsert(recording: Recording) {
        _recordings.update { current ->
            (listOf(recording) + current.filterNot { it.id == recording.id })
                .sortedByDescending { it.createdAtMillis }
        }
        persist()
    }

    fun update(id: String, transform: (Recording) -> Recording) {
        _recordings.update { current ->
            current.map { if (it.id == id) transform(it) else it }
        }
        persist()
    }

    fun find(id: String?): Recording? = _recordings.value.firstOrNull { it.id == id }

    fun delete(id: String) {
        _recordings.value.firstOrNull { it.id == id }?.filePath?.let { path ->
            val audio = File(path)
            audio.delete()
            File(audio.parentFile, audio.nameWithoutExtension + ".srt").delete()
        }
        _recordings.update { current -> current.filterNot { it.id == id } }
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
        put("isPhoneCall", r.isPhoneCall)
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
            errorMessage = o.optString("errorMessage", null.toString()).takeIf { o.has("errorMessage") && !o.isNull("errorMessage") },
            isPhoneCall = o.optBoolean("isPhoneCall", false)
        )
    }

    companion object {
        @Volatile private var instance: RecordingsRepository? = null

        /**
         * One shared instance per process. The recording service and the
         * ViewModel both read and write the library — with two instances each
         * would hold its own in-memory [StateFlow] over the same JSON file, so
         * a recording saved by the service (e.g. stopped from the notification
         * while the UI was gone) would be invisible to the UI until a restart,
         * and whichever persisted last would clobber the other's entries.
         */
        fun get(context: Context): RecordingsRepository =
            instance ?: synchronized(this) {
                instance ?: RecordingsRepository(context).also { instance = it }
            }
    }
}
