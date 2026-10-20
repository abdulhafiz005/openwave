package com.freemusic.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Saves tracks the source allows downloading. On Android 10+ they go to Music/Free Music via
 * MediaStore, so they show up in the Songs list and survive an uninstall; older versions keep
 * them in the app's private storage.
 */
class Downloader(private val context: Context) {

    /** Returns where the file now lives: a content:// uri, or an absolute path on Android 9 and below. */
    suspend fun download(track: Track): String = withContext(Dispatchers.IO) {
        val url = when (track.source) {
            SOURCE_AUDIUS -> audiusDownloadUrl(track.id)
            SOURCE_JAMENDO -> track.uri
            else -> error("This track can't be downloaded")
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) error("Server answered ${conn.responseCode}")
            val (ext, mime) = format(conn.contentType, conn.getHeaderField("Content-Disposition"))
            val name = fileName(track, ext)
            if (Build.VERSION.SDK_INT >= 29) saveToMediaStore(conn, track, name, mime) else saveToPrivate(conn, name)
        } finally {
            conn.disconnect()
        }
    }

    fun delete(path: String) {
        runCatching {
            if (path.startsWith("content:")) context.contentResolver.delete(Uri.parse(path), null, null)
            else File(path).delete()
        }
    }

    /** True if the saved file can still be opened (the user may have deleted it). */
    fun exists(path: String) = runCatching {
        if (path.startsWith("content:")) {
            context.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
        } else File(path).exists()
    }.getOrDefault(false)

    private fun saveToMediaStore(conn: HttpURLConnection, track: Track, name: String, mime: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Free Music")
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            if (track.album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, track.album)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Couldn't create the file")
        try {
            resolver.openOutputStream(uri)!!.use { out -> conn.inputStream.use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri.toString()
    }

    private fun saveToPrivate(conn: HttpURLConnection, name: String): String {
        val dir = File(context.filesDir, "music").apply { mkdirs() }
        val file = File(dir, name)
        conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file.absolutePath
    }

    private fun fileName(track: Track, ext: String): String {
        val base = "${track.artist} - ${track.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        return "$base.$ext"
    }

    private fun format(contentType: String?, disposition: String?): Pair<String, String> {
        val fromName = disposition?.substringAfter("filename=", "")?.trim('"', ' ', ';')
            ?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return when {
            fromName == "wav" || mime in setOf("audio/wav", "audio/wave", "audio/x-wav") -> "wav" to "audio/x-wav"
            fromName == "flac" || mime == "audio/flac" -> "flac" to "audio/flac"
            fromName == "m4a" || mime in setOf("audio/mp4", "audio/x-m4a", "audio/aac") -> "m4a" to "audio/mp4"
            fromName == "ogg" || mime == "audio/ogg" -> "ogg" to "audio/ogg"
            else -> "mp3" to "audio/mpeg"
        }
    }
}
