package com.nutka.app.data

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed class TranscriptionResult {
    data class Success(val segments: List<Segment>) : TranscriptionResult()
    data class Failure(val message: String) : TranscriptionResult()
}

/** Wraps a request body to report upload progress (0-100) as it's written to the socket. */
private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (percent: Int) -> Unit
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().coerceAtLeast(1)
        var written = 0L
        var lastPercentReported = -1
        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                if (percent != lastPercentReported) {
                    lastPercentReported = percent
                    onProgress(percent)
                }
            }
        }
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}

/**
 * ElevenLabs Speech-to-Text ("Scribe") — https://api.elevenlabs.io/v1/speech-to-text.
 * You just paste an API key in Settings; the audio is sent to ElevenLabs and
 * they do the actual transcription/diarization. This is the only network
 * call this class makes — nothing else about the app talks to ElevenLabs.
 *
 * Request fields sent (model_id, language_code, tag_audio_events, diarize,
 * num_speakers, timestamps_granularity, additional_formats) match
 * ElevenLabs' documented Scribe parameters. num_speakers is only sent when
 * the user set an explicit expected speaker count in Settings — otherwise
 * ElevenLabs auto-detects, which is usually fine but can under-split a
 * conversation where one speaker is much quieter than the other; a known
 * count is a strong hint that fixes that. "keyterms" is sent too as a
 * best-effort biasing hint —
 * if a given ElevenLabs API version doesn't recognize it, multipart backends
 * generally just ignore unknown fields rather than reject the request. "No
 * verbatim" has no API equivalent, so it's applied client-side after the
 * transcript comes back (filler-word stripping in [stripFillers]).
 *
 * Diarization is always requested (`diarize=true`) — it's a baseline
 * feature, not tied to [SettingsState.assignSpeakersFromLibrary], which is
 * a separate "recognize known voices" toggle. Before this file also
 * requires an explicit num_speakers hint to attempt diarization; that
 * caused imported/recorded audio to silently come back with no speaker
 * split whenever that unrelated toggle was off (the default).
 *
 * The file is uploaded exactly as recorded. [AudioNormalizer] exists and
 * could even out loud/quiet stretches before sending, but it is deliberately
 * not wired in: it decodes the whole recording to raw PCM in memory, which is
 * a real OOM risk on the hour-long sessions this app is built for. This note
 * used to claim normalization was applied — it never was.
 */
class ElevenLabsTranscriptionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // transcription can take up to a few minutes for long audio
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    fun transcribe(audioFile: File, settings: SettingsState, onProgress: (percent: Int) -> Unit = {}): TranscriptionResult {
        if (settings.elevenLabsApiKey.isBlank()) {
            AppLog.d("ElevenLabs", "Brak klucza API ElevenLabs w Ustawieniach")
            return TranscriptionResult.Failure("Brak klucza API ElevenLabs w Ustawieniach")
        }

        val startTime = System.currentTimeMillis()
        val mediaType = when (audioFile.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            else -> "audio/mp4"
        }.toMediaTypeOrNull()

        AppLog.d("ElevenLabs", "Rozpoczęcie wysyłania: plik ${audioFile.name} (${audioFile.length()} B, typ: $mediaType), język: ${settings.primaryLanguage}")

        return runCatching {
            val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", audioFile.name,
                    audioFile.asRequestBody(mediaType)
                )
                .addFormDataPart("model_id", "scribe_v1")
                .addFormDataPart("tag_audio_events", settings.tagAudioEvents.toString())
                .addFormDataPart("diarize", "true")
                .addFormDataPart("timestamps_granularity", "word")

            if (settings.primaryLanguage != "auto") {
                bodyBuilder.addFormDataPart("language_code", settings.primaryLanguage)
            }
            if (settings.expectedSpeakers > 0) {
                bodyBuilder.addFormDataPart("num_speakers", settings.expectedSpeakers.toString())
            }
            if (settings.keyterms.isNotEmpty()) {
                bodyBuilder.addFormDataPart("keyterms", settings.keyterms.joinToString(","))
            }
            if (settings.includeSubtitles) {
                bodyBuilder.addFormDataPart("additional_formats", JSONArray().put(JSONObject().put("format", "srt")).toString())
            }

            val progressBody = ProgressRequestBody(bodyBuilder.build()) { pct ->
                onProgress(pct)
                if (pct == 100) {
                    AppLog.d("ElevenLabs", "Plik wysłany (100%), oczekiwanie na transkrypcję z ElevenLabs...")
                }
            }

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/speech-to-text")
                .addHeader("xi-api-key", settings.elevenLabsApiKey.trim())
                .post(progressBody)
                .build()

            AppLog.d("ElevenLabs", "Wysyłanie żądania POST do ElevenLabs Speech-to-Text API...")

            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                val bodyStr = response.body?.string().orEmpty()
                AppLog.d("ElevenLabs", "Odpowiedź HTTP ${response.code} w ${elapsed}ms")

                if (!response.isSuccessful) {
                    AppLog.e("ElevenLabs", "Błąd API ElevenLabs (HTTP ${response.code}): $bodyStr")
                    return TranscriptionResult.Failure("ElevenLabs zwrócił błąd ${response.code}: ${bodyStr.take(200)}")
                }

                maybeSaveSubtitles(audioFile, bodyStr)
                val segments = parseSegments(bodyStr, settings)
                AppLog.d("ElevenLabs", "Sukces: wyodrębniono ${segments.size} segmentów transkrypcji")
                TranscriptionResult.Success(segments)
            }
        }.getOrElse { e ->
            AppLog.e("ElevenLabs", "Wyjątek podczas transkrypcji: ${e.message}", e)
            TranscriptionResult.Failure(e.message ?: "Nie udało się połączyć z ElevenLabs")
        }
    }

    /** Groups ElevenLabs' word-level output into per-speaker paragraphs. */
    private fun parseSegments(body: String, settings: SettingsState): List<Segment> {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()

        val words = json.optJSONArray("words")
        if (words != null && words.length() > 0) {
            val segments = mutableListOf<Segment>()
            val speakerRole = LinkedHashMap<String, String>()
            var curSpeaker: String? = null
            var curRole = "a"
            var curStart = 0.0
            val curText = StringBuilder()

            fun flush() {
                val text = curText.toString().trim()
                if (text.isNotEmpty()) {
                    val finalText = if (settings.noVerbatim) stripFillers(text) else text
                    if (finalText.isNotEmpty()) segments.add(Segment(curRole, curStart.toInt(), finalText, curSpeaker))
                }
                curText.setLength(0)
            }

            for (i in 0 until words.length()) {
                val w = words.optJSONObject(i) ?: continue
                val speaker = if (w.has("speaker_id")) w.optString("speaker_id") else null
                if (speaker != curSpeaker) {
                    flush()
                    curSpeaker = speaker
                    curRole = speaker?.let { s -> speakerRole.getOrPut(s) { if (speakerRole.size % 2 == 0) "a" else "b" } } ?: "a"
                    curStart = w.optDouble("start", 0.0)
                }
                curText.append(w.optString("text", ""))
            }
            flush()
            return segments
        }

        val plainText = json.optString("text").trim()
        if (plainText.isNotEmpty()) {
            val finalText = if (settings.noVerbatim) stripFillers(plainText) else plainText
            return listOf(Segment(speaker = "a", timeSec = 0, text = finalText))
        }
        return emptyList()
    }

    private val fillerRegex = Regex(
        "\\b(yyy+|eee+|hmm+|umm?|uhh?|erm+)\\b[,.]?\\s*",
        RegexOption.IGNORE_CASE
    )

    private fun stripFillers(text: String): String =
        text.replace(fillerRegex, "").replace(Regex("\\s{2,}"), " ").trim()

    /** Best-effort: if ElevenLabs returned an SRT alongside the transcript, keep it next to the audio file. */
    private fun maybeSaveSubtitles(audioFile: File, body: String) {
        runCatching {
            val json = JSONObject(body)
            val formats = json.optJSONArray("additional_formats") ?: return
            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val kind = fmt.optString("requested_format", fmt.optString("format", ""))
                if (!kind.contains("srt", ignoreCase = true)) continue
                val content = fmt.optString("content")
                if (content.isBlank()) continue
                val decoded = if (fmt.optBoolean("is_base64_encoded", false)) {
                    String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
                } else content
                File(audioFile.parentFile, audioFile.nameWithoutExtension + ".srt").writeText(decoded)
            }
        }
    }
}
