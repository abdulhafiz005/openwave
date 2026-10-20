package com.freemusic.app

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.freemusic.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private val SONG_FOLDER_NAME = Regex("music|song|mp3", RegexOption.IGNORE_CASE)

enum class MusicSource(val label: String) { JAMENDO("Jamendo"), AUDIUS("Audius") }

class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.create(app).dao()
    val jamendo = JamendoRepository()
    val audius = AudiusRepository()
    val youtube = YouTubeRepository()
    private val localRepo = LocalMusicRepository(app)
    private val downloader = Downloader(app)

    // Catalog
    val trending = MutableStateFlow<List<Track>>(emptyList())
    val newReleases = MutableStateFlow<List<Track>>(emptyList())
    val audiusTrending = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = MutableStateFlow<List<Track>>(emptyList())
    val videoResults = MutableStateFlow<List<Video>>(emptyList())
    val youtubeWebUrl = MutableStateFlow<String?>(null)
    val localTracks = MutableStateFlow<List<Track>>(emptyList())
    /** Folder (relative path) of each local file, keyed by track id. */
    val localFolders = MutableStateFlow<Map<String, String>>(emptyMap())
    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    /** Folders the user marked as holding songs, on top of the built-in Music/Songs name match. */
    val songFolders = MutableStateFlow(prefs.getStringSet("song_folders", emptySet()).orEmpty())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    /** One-off status message shown as a snackbar (download finished, etc.). */
    val notice = MutableStateFlow<String?>(null)

    // Library
    val liked = dao.likedTracks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = dao.downloadedTracks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun playlistTracks(id: Long) = dao.playlistTracks(id)

    // Player state
    val currentTrack = MutableStateFlow<Track?>(null)
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    val shuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)

    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()

    init {
        connectPlayer()
        loadHome()
    }

    private fun connectPlayer() = viewModelScope.launch {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val c = MediaController.Builder(app, token).buildAsync().await()
        controller = c
        c.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncCurrent()
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying.value = playing }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle.value = enabled }
            override fun onRepeatModeChanged(mode: Int) { repeatMode.value = mode }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs.value = c.duration.coerceAtLeast(0)
            }
        })
        syncCurrent()
        while (true) {
            if (c.isPlaying) positionMs.value = c.currentPosition
            delay(500)
        }
    }

    private fun syncCurrent() {
        val c = controller ?: return
        currentTrack.value = queue.firstOrNull { it.id == c.currentMediaItem?.mediaId }
        positionMs.value = c.currentPosition
        durationMs.value = c.duration.coerceAtLeast(0)
    }

    // --- Catalog loading ---

    fun loadHome() = viewModelScope.launch {
        loading.value = true
        error.value = null
        // Audius needs no key, so it also fills Home when Jamendo isn't configured.
        runCatching { audiusTrending.value = audius.trending() }
        if (!jamendo.configured) {
            if (audiusTrending.value.isEmpty()) {
                error.value = "Add JAMENDO_CLIENT_ID to local.properties to stream online music."
            }
            loading.value = false
            return@launch
        }
        var failure: Throwable? = null
        for (attempt in 0 until 3) {
            failure = runCatching {
                trending.value = jamendo.trending()
                newReleases.value = jamendo.newReleases()
            }.exceptionOrNull()
            if (failure == null) break
            delay(1500L * (attempt + 1))
        }
        if (failure != null && audiusTrending.value.isEmpty()) {
            error.value = "Couldn't load music: ${failure.message}"
        }
        loading.value = false
    }

    fun search(query: String, source: MusicSource) = viewModelScope.launch {
        if (query.isBlank() || (source == MusicSource.JAMENDO && !jamendo.configured)) {
            searchResults.value = emptyList()
            return@launch
        }
        loading.value = true
        runCatching {
            searchResults.value = when (source) {
                MusicSource.JAMENDO -> jamendo.search(query.trim())
                MusicSource.AUDIUS -> audius.search(query.trim())
            }
        }.onFailure { error.value = "Search failed: ${it.message}" }
        loading.value = false
    }

    fun searchVideos(query: String) = viewModelScope.launch {
        val id = YouTubeRepository.parseVideoId(query)
        if (id != null) {
            openVideo(Video(id, "YouTube video", "", "https://i.ytimg.com/vi/$id/mqdefault.jpg"))
            return@launch
        }
        if (query.isBlank()) return@launch
        if (!youtube.searchConfigured) {
            // No API key: let the user search on YouTube's own site inside the app.
            controller?.pause()
            youtubeWebUrl.value = "https://m.youtube.com/results?search_query=" +
                java.net.URLEncoder.encode(query.trim(), "UTF-8")
            return@launch
        }
        loading.value = true
        runCatching { videoResults.value = youtube.search(query.trim()) }
            .onFailure { error.value = "YouTube search failed: ${it.message}" }
        loading.value = false
    }

    fun openVideo(video: Video) {
        controller?.pause() // don't play music over a video
        youtubeWebUrl.value = "https://m.youtube.com/watch?v=${video.id}"
    }
    fun closeYoutubeWeb() { youtubeWebUrl.value = null }

    fun scanLocal() = viewModelScope.launch {
        runCatching { localRepo.scan() }.onSuccess {
            localTracks.value = it.tracks
            localFolders.value = it.folders
        }
    }

    /** The Music folder, any folder with music/song in its name, or one the user marked. App data under Android/ never counts. */
    fun isSongFolder(folder: String) =
        folder in songFolders.value ||
            (!folder.startsWith("Android/", ignoreCase = true) && SONG_FOLDER_NAME.containsMatchIn(folder))

    fun setSongFolder(folder: String, enabled: Boolean) {
        songFolders.value = if (enabled) songFolders.value + folder else songFolders.value - folder
        prefs.edit().putStringSet("song_folders", songFolders.value).apply()
    }

    // --- Playback ---

    fun play(tracks: List<Track>, index: Int) {
        val c = controller ?: return
        if (index !in tracks.indices) return
        val downloaded = downloads.value.associate { it.id to it.downloadedPath?.takeIf(downloader::exists) }
        queue = tracks
        c.setMediaItems(tracks.map { toMediaItem(it, downloaded[it.id]) }, index, 0L)
        c.prepare()
        c.play()
    }

    private fun toMediaItem(t: Track, localPath: String?): MediaItem {
        val uri = localPath?.let { if (it.startsWith("content:")) it else Uri.fromFile(File(it)).toString() } ?: t.uri
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.artUri?.let(Uri::parse))
                    .build(),
            )
            .build()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }
    /** Stops playback and clears the queue, which also hides the mini player and the notification. */
    fun stop() {
        controller?.run { stop(); clearMediaItems() }
        queue = emptyList()
        currentTrack.value = null
        isPlaying.value = false
    }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms); positionMs.value = ms }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // --- Library ---

    fun toggleLike(track: Track) = viewModelScope.launch {
        dao.insertTrackIfAbsent(track)
        dao.setLiked(track.id, !liked.value.any { it.id == track.id })
    }

    fun createPlaylist(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.insertPlaylist(Playlist(name = name.trim()))
    }

    fun deletePlaylist(id: Long) = viewModelScope.launch {
        dao.clearPlaylist(id)
        dao.deletePlaylist(id)
    }

    fun addToPlaylist(playlistId: Long, track: Track) = viewModelScope.launch {
        dao.insertTrackIfAbsent(track)
        dao.addToPlaylist(PlaylistTrack(playlistId, track.id))
    }

    fun removeFromPlaylist(playlistId: Long, track: Track) = viewModelScope.launch {
        dao.removeFromPlaylist(playlistId, track.id)
    }

    fun download(track: Track) = viewModelScope.launch {
        if (!track.downloadable) return@launch
        notice.value = "Downloading \"${track.title}\"…"
        runCatching {
            val path = downloader.download(track)
            dao.insertTrackIfAbsent(track)
            dao.setDownloaded(track.id, path)
        }.onSuccess {
            notice.value = "Saved to Music/Free Music"
        }.onFailure {
            notice.value = null
            error.value = "Download failed: ${it.message}"
        }
    }

    fun removeDownload(track: Track) = viewModelScope.launch {
        withContext(Dispatchers.IO) { track.downloadedPath?.let(downloader::delete) }
        dao.setDownloaded(track.id, null)
    }

    fun dismissError() { error.value = null }
    fun dismissNotice() { notice.value = null }

    override fun onCleared() {
        controller?.release()
    }
}
