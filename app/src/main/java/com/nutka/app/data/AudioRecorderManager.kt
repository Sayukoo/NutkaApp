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
 */
class AudioRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    /** @throws Exception if the recorder can't be prepared/started (mic busy, disk full, ...). */
    fun start(outputFile: File) {
        currentFile = outputFile
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        mediaRecorder = recorder
    }

    fun pause() {
        runCatching { if (Build.VERSION.SDK_INT >= 24) mediaRecorder?.pause() }
    }

    fun resume() {
        runCatching { if (Build.VERSION.SDK_INT >= 24) mediaRecorder?.resume() }
    }

    /** Stops the recorder and returns the finished audio file (null if nothing was recording). */
    fun stop(): File? {
        val hadRecorder = mediaRecorder != null
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        return if (hadRecorder) currentFile else null
    }
}
