package com.nutka.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** One audio file sitting in the phone's Downloads folder. */
data class DownloadedAudio(
    val id: String,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationSec: Int,
    val addedAtMillis: Long
)

/**
 * Lists audio files from the shared Downloads folder, so recordings sent over
 * from other apps can be transcribed without walking the system file picker
 * every time.
 *
 * Reads MediaStore rather than the raw directory: since Android 10 an app
 * cannot list `/sdcard/Download` directly, and MediaStore is the only view of
 * shared storage that works across every supported version. A file only shows
 * up here if the media scanner classified it as audio — which covers anything
 * downloaded with a normal audio extension, but not e.g. an .opus voice note
 * some apps save with no extension at all. The manual picker stays available
 * for those.
 */
object DownloadsAudioScanner {

    /** Runtime permission needed to see other apps' audio in shared storage. */
    val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(requiredPermission) == PackageManager.PERMISSION_GRANTED

    fun scan(context: Context, limit: Int = 60): List<DownloadedAudio> {
        if (!hasPermission(context)) return emptyList()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        )
        // RELATIVE_PATH only exists from API 29; older versions still expose the
        // real filesystem path, so match on that instead.
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            selection = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?"
            args = arrayOf("Download/%")
        } else {
            @Suppress("DEPRECATION")
            selection = MediaStore.Audio.Media.DATA + " LIKE ?"
            args = arrayOf("%/Download/%")
        }

        return runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                MediaStore.Audio.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                val result = mutableListOf<DownloadedAudio>()
                while (cursor.moveToNext() && result.size < limit) {
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    result += DownloadedAudio(
                        id = id.toString(),
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        name = cursor.getString(nameCol) ?: "nagranie",
                        sizeBytes = size,
                        // DURATION is in milliseconds; DATE_ADDED in whole seconds.
                        durationSec = (cursor.getLong(durationCol) / 1000L).toInt(),
                        addedAtMillis = cursor.getLong(addedCol) * 1000L
                    )
                }
                result
            }.orEmpty()
        }.getOrElse { e ->
            AppLog.e("Pobrane", "Nie udało się odczytać folderu Pobrane", e)
            emptyList()
        }
    }
}
