package com.freemusic.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalScan(val tracks: List<Track>, val videoFolders: Map<String, String>)

class LocalMusicRepository(private val context: Context) {

    suspend fun scan(): LocalScan = withContext(Dispatchers.IO) {
        val (videos, folders) = scanVideo()
        LocalScan(scanAudio() + videos, folders)
    }

    private fun scanAudio(): List<Track> {
        val out = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val rowId = c.getLong(id)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, rowId)
                val art = ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"), c.getLong(albumId),
                )
                val artistName = c.getString(artist)?.takeUnless { it == "<unknown>" } ?: "Unknown artist"
                out += Track(
                    id = "local:$rowId",
                    title = c.getString(title) ?: "Untitled",
                    artist = artistName,
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
    private fun scanVideo(): Pair<List<Track>, Map<String, String>> {
        val out = mutableListOf<Track>()
        val folders = mutableMapOf<String, String>()
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.RELATIVE_PATH else MediaStore.Video.Media.DATA
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
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
                "${MediaStore.Video.Media.TITLE} COLLATE NOCASE",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
                val duration = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val path = c.getColumnIndexOrThrow(pathColumn)
                while (c.moveToNext()) {
                    val rowId = c.getLong(id)
                    val folder = folderOf(c.getString(path))
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId).toString()
                    out += Track(
                        id = "$LOCAL_VIDEO_PREFIX$rowId",
                        title = c.getString(title) ?: "Untitled",
                        artist = c.getString(artist)?.takeUnless { it == "<unknown>" }
                            ?: folder.trimEnd('/').substringAfterLast('/').ifBlank { "Video" },
                        album = "",
                        artUri = uri,
                        uri = uri,
                        source = SOURCE_LOCAL,
                        durationMs = c.getLong(duration),
                    )
                    folders["$LOCAL_VIDEO_PREFIX$rowId"] = folder
                }
            }
        }
        return out to folders
    }

    /** Folder of a video as a relative path like "Movies/Songs/" (on API < 29 the DATA path is trimmed). */
    private fun folderOf(raw: String?): String {
        val p = raw ?: return ""
        if (Build.VERSION.SDK_INT >= 29) return p
        return p.removePrefix("/storage/emulated/0/").substringBeforeLast('/', "") + "/"
    }
}
