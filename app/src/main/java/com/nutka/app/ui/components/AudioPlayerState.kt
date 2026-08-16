package com.nutka.app.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Local, ephemeral playback state for the audio file shown on the transcript screen. */
class AudioPlayerState {
    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableStateOf(0); private set
    var durationMs by mutableStateOf(0); private set

    private var mediaPlayer: MediaPlayer? = null
    private var loadedPath: String? = null

    fun load(path: String) {
        if (loadedPath == path && mediaPlayer != null) return
        release()
        loadedPath = path
        runCatching {
            // Deliberately not using MediaPlayer().apply { } here: inside that
            // block `this` is the MediaPlayer, and it has its own read-only
            // `isPlaying` getter that would shadow this class's `var isPlaying`
            // ("'val' cannot be reassigned") — so this stays as plain calls on
            // a local val instead.
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.setOnCompletionListener { player ->
                isPlaying = false
                player.seekTo(0)
                positionMs = 0
            }
            mp.prepare()
            mediaPlayer = mp
            durationMs = mp.duration
        }
    }

    fun toggle() {
        val mp = mediaPlayer ?: return
        if (isPlaying) {
            runCatching { mp.pause() }
            isPlaying = false
        } else {
            runCatching { mp.start() }
            isPlaying = true
        }
    }

    fun seekToFraction(fraction: Float) {
        val mp = mediaPlayer ?: return
        val target = (fraction.coerceIn(0f, 1f) * durationMs).toInt()
        runCatching { mp.seekTo(target) }
        positionMs = target
    }

    internal fun tick() {
        val mp = mediaPlayer ?: return
        if (isPlaying) positionMs = runCatching { mp.currentPosition }.getOrDefault(positionMs)
    }

    fun release() {
        mediaPlayer?.let { runCatching { it.release() } }
        mediaPlayer = null
        loadedPath = null
        isPlaying = false
        positionMs = 0
        durationMs = 0
    }
}

@Composable
fun rememberAudioPlayerState(filePath: String?): AudioPlayerState {
    val context: Context = LocalContext.current
    val state = remember { AudioPlayerState() }

    LaunchedEffect(filePath) {
        if (!filePath.isNullOrBlank()) state.load(filePath)
    }
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            state.tick()
            delay(200)
        }
    }
    DisposableEffect(context) {
        onDispose { state.release() }
    }
    return state
}
