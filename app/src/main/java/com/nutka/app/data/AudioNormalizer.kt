package com.nutka.app.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Levels out an audio file's volume before it's sent to ElevenLabs, so quiet
 * speakers aren't lost against louder ones and the API's diarization has an
 * easier time telling voices apart.
 *
 * Decodes to PCM, applies a windowed RMS ("dynamic") normalization — every
 * ~400ms chunk is boosted or attenuated toward a common target loudness,
 * smoothed against its neighbor so gain doesn't pump audibly — then
 * re-encodes to AAC/M4A. A single global gain (plain peak normalization)
 * wouldn't help here: if one speaker is already loud, it sets the ceiling
 * for the whole file and the quiet speaker never gets boosted relative to
 * them. Per-window leveling fixes that because it reacts to whoever is
 * talking at that moment.
 */
object AudioNormalizer {

    // Above this compressed source size, skip normalization rather than risk
    // decoding a huge file to raw PCM in memory (OOM on long recordings).
    private const val MAX_SOURCE_BYTES = 60L * 1024 * 1024
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val MAX_LOOP_MILLIS = 3 * 60 * 1000L

    /** Returns a new normalized temp file next to [source], or null if normalization wasn't possible/safe. */
    fun normalize(source: File): File? {
        if (!source.exists() || source.length() == 0L || source.length() > MAX_SOURCE_BYTES) return null
        return runCatching {
            val pcm = decodeToPcm(source) ?: return null
            if (pcm.samples.isEmpty()) return null
            val leveled = levelWindows(pcm.samples, pcm.sampleRate, pcm.channelCount)
            val out = File.createTempFile("nutka_norm_", ".m4a", source.parentFile)
            encodePcmToAac(leveled, pcm.sampleRate, pcm.channelCount, out)
            out
        }.getOrElse { AppLog.d("Normalize", "${source.name}: pominięto (${it.message})"); null }
    }

    private class PcmAudio(val samples: ShortArray, val sampleRate: Int, val channelCount: Int)

    private fun decodeToPcm(source: File): PcmAudio? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i; format = f; break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val output = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            val deadline = System.currentTimeMillis() + MAX_LOOP_MILLIS

            try {
                while (!sawOutputEOS) {
                    check(System.currentTimeMillis() < deadline) { "timeout dekodowania" }
                    if (!sawInputEOS) {
                        val inIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                    if (outIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)!!
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.get(chunk)
                            output.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                    }
                }
            } finally {
                codec.stop()
                codec.release()
            }

            val bytes = output.toByteArray()
            val shorts = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return PcmAudio(shorts, sampleRate, channelCount)
        } finally {
            extractor.release()
        }
    }

    private fun levelWindows(samples: ShortArray, sampleRate: Int, channels: Int): ShortArray {
        val windowMs = 400
        val windowSize = (sampleRate * channels * windowMs / 1000).coerceAtLeast(channels)
        val targetRms = 0.2 * Short.MAX_VALUE // ~ -14 dBFS window target
        val maxGain = 12.0 // ~ +21.6 dB — enough headroom for a speaker who's genuinely much quieter, not just soft-spoken
        val minGain = 0.3
        val out = ShortArray(samples.size)

        var i = 0
        var prevGain = 1.0
        while (i < samples.size) {
            val end = min(i + windowSize, samples.size)
            var sumSq = 0.0
            for (j in i until end) sumSq += samples[j].toDouble() * samples[j].toDouble()
            val rms = sqrt(sumSq / (end - i))
            var gain = if (rms > 1.0) targetRms / rms else maxGain
            gain = gain.coerceIn(minGain, maxGain)
            gain = prevGain + (gain - prevGain) * 0.5 // smooth against previous window to avoid audible pumping
            prevGain = gain

            for (j in i until end) {
                val v = (samples[j] * gain).roundToInt()
                out[j] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            i = end
        }
        return out
    }

    private fun encodePcmToAac(samples: ShortArray, sampleRate: Int, channelCount: Int, out: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrack = -1
        var muxerStarted = false

        val pcmBytes = ByteArray(samples.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)

        val bufferInfo = MediaCodec.BufferInfo()
        val chunkBytes = 4096
        var offset = 0
        var presentationTimeUs = 0L
        var inputDone = false
        var outputDone = false
        val bytesPerFrame = 2 * channelCount
        val deadline = System.currentTimeMillis() + MAX_LOOP_MILLIS

        try {
            while (!outputDone) {
                check(System.currentTimeMillis() < deadline) { "timeout kodowania" }
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        val remaining = pcmBytes.size - offset
                        if (remaining <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val size = min(chunkBytes, remaining)
                            inBuf.put(pcmBytes, offset, size)
                            codec.queueInputBuffer(inIndex, 0, size, presentationTimeUs, 0)
                            offset += size
                            presentationTimeUs += (size / bytesPerFrame) * 1_000_000L / sampleRate
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrack = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerTrack, outBuf, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }
}
