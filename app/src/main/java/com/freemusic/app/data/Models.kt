package com.freemusic.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

const val SOURCE_JAMENDO = "jamendo"
const val SOURCE_LOCAL = "local"
const val SOURCE_AUDIUS = "audius"
const val SOURCE_YOUTUBE = "youtube"

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artUri: String?,
    val uri: String,
    val source: String,
    val durationMs: Long,
    val liked: Boolean = false,
    val downloadedPath: String? = null,
    /** The source allows saving this track for offline use. */
    val downloadable: Boolean = false,
) {
    /** A video file on the device whose audio track is played as a song. */
    val isVideo get() = id.startsWith(LOCAL_VIDEO_PREFIX)
}

const val LOCAL_VIDEO_PREFIX = "localv:"

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis(),
)
