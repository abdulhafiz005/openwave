package com.freemusic.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [folders] maps a track id to its folder as a relative path like "Music/" (blank if unknown). */
data class LocalScan(val tracks: List<Track>, val folders: Map<String, String>)

/** Tags that tools write when they don't know the real title (e.g. audio saved from a video). */
private val GENERIC_TITLE = Regex(
    """video ?playback|audio|untitled|unknown|title|track ?\d*|audio[ _-]?\d+|\d+""",
    RegexOption.IGNORE_CASE,
)

class LocalMusicRepository(private val context: Context) {

    suspend fun scan(): LocalScan = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, String>()
        val tracks = scanAudio(folders) + scanVideo(folders)
        LocalScan(tracks.sortedBy { it.title.lowercase() }, folders)
    }

    private fun scanAudio(folders: MutableMap<String, String>): List<Track> {
        val out = mutableListOf<Track>()
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            pathColumn,
        )
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000",
            null,
            null,
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val path = c.getColumnIndexOrThrow(pathColumn)
            while (c.moveToNext()) {
                val rowId = c.getLong(id)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, rowId)
                val art = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), c.getLong(albumId),
                )
                val trackId = "local:$rowId"
                folders[trackId] = folderOf(c.getString(path))
                out += Track(
                    id = trackId,
                    title = displayTitle(c.getString(title), c.getString(name)),
                    artist = c.getString(artist)?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                    album = c.getString(album) ?: "",
                    artUri = art.toString(),
                    uri = contentUri.toString(),
                    source = SOURCE_LOCAL,
                    durationMs = c.getLong(duration),
                )
            }
        }
        return out
    }

    /** Video files (music videos, clips) that can be played as audio. */
    private fun scanVideo(folders: MutableMap<String, String>): List<Track> {
        val out = mutableListOf<Track>()
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.RELATIVE_PATH else MediaStore.Video.Media.DATA
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.DURATION,
            pathColumn,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Video.Media.DURATION} > 10000",
                null,
                null,
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val artist = c.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
                val duration = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val path = c.getColumnIndexOrThrow(pathColumn)
                while (c.moveToNext()) {
                    val rowId = c.getLong(id)
                    val folder = folderOf(c.getString(path))
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId).toString()
                    val trackId = "$LOCAL_VIDEO_PREFIX$rowId"
                    folders[trackId] = folder
                    out += Track(
                        id = trackId,
                        title = displayTitle(c.getString(title), c.getString(name)),
                        artist = c.getString(artist)?.takeUnless { it == "<unknown>" }
                            ?: folder.trimEnd('/').substringAfterLast('/').ifBlank { "Video" },
                        album = "",
                        artUri = uri,
                        uri = uri,
                        source = SOURCE_LOCAL,
                        durationMs = c.getLong(duration),
                    )
                }
            }
        }
        return out
    }

    /** The embedded tag title, unless it's missing or generic; then the file name without its extension. */
    private fun displayTitle(tag: String?, fileName: String?): String {
        val fromFile = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        val t = tag?.trim()
        return when {
            t.isNullOrBlank() || GENERIC_TITLE.matches(t) -> fromFile ?: t ?: "Untitled"
            else -> t
        }
    }

    /** Folder as a relative path like "Music/" (on API < 29 the DATA path is trimmed to match). */
    private fun folderOf(raw: String?): String {
        val p = raw ?: return ""
        if (Build.VERSION.SDK_INT >= 29) return p
        return p.removePrefix("/storage/emulated/0/").substringBeforeLast('/', "") + "/"
    }
}
