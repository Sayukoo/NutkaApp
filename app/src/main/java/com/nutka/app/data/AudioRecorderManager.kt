package com.nutka.app.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Owns the microphone for a recording session via [MediaRecorder] only.
 *
 * This used to also run the on-device [android.speech.SpeechRecognizer] in
 * parallel for live captions, but on real hardware (confirmed on Pixel) the
 * two fight over the mic: the system speech-recognition service grabs the
 * audio session out from under MediaRecorder, and the recorded file comes
 * out silent even though nothing crashes — which is exactly the failure
 * this app hit ("recording doesn't save" / ElevenLabs "no voice detected").
 * MediaRecorder is now the sole owner of the mic for the whole session.
 *
 * The container is AAC/ADTS (`.aac`) rather than MPEG-4 (`.m4a`) whenever the
 * device supports it. An MPEG-4 file only becomes playable once
 * [MediaRecorder.stop] writes its trailing index — so if the process is ever
 * killed mid-session (an OEM battery manager, a low-memory kill, a crash) an
 * .m4a is lost *entirely*, however many minutes were already on disk. ADTS is
 * a plain frame stream with no trailer, so a killed session still leaves
 * everything recorded up to that moment playable and transcribable. Devices
 * that refuse the ADTS combination fall back to MPEG-4 automatically.
 */
class AudioRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    val isActive: Boolean get() = mediaRecorder != null

    /**
     * Starts a session inside [directory], naming the file after [baseName]
     * plus whichever container actually worked.
     *
     * @param audioSource [MediaRecorder.AudioSource.MIC] for dictation. Phone
     *   calls must use [MediaRecorder.AudioSource.VOICE_RECOGNITION]: while a
     *   call is active Android hands an ordinary app's MIC stream nothing but
     *   silence, and the only exemption open to a non-system app is an enabled
     *   accessibility service capturing from VOICE_RECOGNITION (see
     *   AudioPolicyService::updateUidStates_l). VOICE_COMMUNICATION would be
     *   doubly wrong — silenced too, and its echo canceller exists precisely
     *   to erase the loudspeaker, i.e. the other person's voice.
     * @return the file being written to.
     * @throws Exception if no container could be prepared/started (mic busy,
     *   permission revoked, disk full, ...).
     */
    fun start(directory: File, baseName: String, audioSource: Int = MediaRecorder.AudioSource.MIC): File {
        directory.mkdirs()
        val containers = listOf(
            MediaRecorder.OutputFormat.AAC_ADTS to "aac",
            MediaRecorder.OutputFormat.MPEG_4 to "m4a"
        )
        var lastError: Throwable? = null
        for ((format, extension) in containers) {
            val file = File(directory, "$baseName.$extension")
            val recorder = newRecorder()
            try {
                recorder.setAudioSource(audioSource)
                recorder.setOutputFormat(format)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44_100)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder
                currentFile = file
                AppLog.d("Recorder", "Mikrofon uruchomiony -> ${file.name}")
                return file
            } catch (t: Throwable) {
                lastError = t
                AppLog.e("Recorder", "Format $extension nieobsługiwany, próbuję dalej", t)
                runCatching { recorder.reset() }
                runCatching { recorder.release() }
                runCatching { file.delete() }
            }
        }
        throw lastError ?: IllegalStateException("Nie udało się uruchomić mikrofonu")
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()

    fun pause() {
        runCatching { mediaRecorder?.pause() }
            .onFailure { AppLog.e("Recorder", "Nie udało się wstrzymać nagrywania", it) }
    }

    fun resume() {
        runCatching { mediaRecorder?.resume() }
            .onFailure { AppLog.e("Recorder", "Nie udało się wznowić nagrywania", it) }
    }

    /** Peak amplitude (0..32767) since the previous call, or 0 when idle. */
    fun getMaxAmplitude(): Int = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    /**
     * Stops the recorder and returns the finished audio file, or null if
     * nothing was recording or the file came out empty. A failing
     * [MediaRecorder.stop] is logged but not fatal: with the ADTS container
     * everything already flushed to disk stays valid.
     */
    fun stop(): File? {
        val recorder = mediaRecorder ?: return null
        mediaRecorder = null
        val file = currentFile
        currentFile = null
        runCatching { recorder.stop() }
            .onFailure { AppLog.e("Recorder", "MediaRecorder.stop() zgłosił błąd (plik może być mimo to poprawny)", it) }
        runCatching { recorder.release() }
        return file?.takeIf { it.exists() && it.length() > 0L }
    }
}
