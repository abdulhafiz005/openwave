package com.freemusic.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.freemusic.app.MusicSource
import com.freemusic.app.MusicViewModel
import com.freemusic.app.data.Playlist

@Composable
fun HomeScreen(vm: MusicViewModel) {
    val trending by vm.trending.collectAsState()
    val fresh by vm.newReleases.collectAsState()
    val audius by vm.audiusTrending.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    if (trending.isEmpty() && fresh.isEmpty() && audius.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            if (loading) CircularProgressIndicator()
            else {
                Text(error ?: "Nothing here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button({ vm.loadHome() }) { Text("Retry") }
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        if (trending.isNotEmpty()) item { SectionTitle("Trending on Jamendo") }
        items(trending.size) { i -> TrackRow(vm, trending[i], onClick = { vm.play(trending, i) }) }
        if (audius.isNotEmpty()) item { SectionTitle("Trending on Audius") }
        items(audius.size) { i -> TrackRow(vm, audius[i], onClick = { vm.play(audius, i) }) }
        if (fresh.isNotEmpty()) item { SectionTitle("New on Jamendo") }
        items(fresh.size) { i -> TrackRow(vm, fresh[i], onClick = { vm.play(fresh, i) }) }
    }
}

@Composable
fun SearchScreen(vm: MusicViewModel) {
    val results by vm.searchResults.collectAsState()
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(MusicSource.AUDIUS) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
            singleLine = true,
            placeholder = { Text("Songs, artists, albums") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.search(query, source) }),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicSource.entries.forEach { s ->
                FilterChip(
                    selected = source == s,
                    onClick = { source = s; vm.search(query, s) },
                    label = { Text(s.label) },
                )
            }
        }
        if (results.isEmpty()) EmptyState("Search free music on ${source.label}") else TrackList(vm, results)
    }
}

@Composable
fun LibraryScreen(vm: MusicViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val titles = listOf("Liked", "Playlists", "On device", "Downloads")

    openPlaylist?.let {
        BackHandler { openPlaylist = null }
        PlaylistDetail(vm, it) { openPlaylist = null }
        return
    }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(tab, edgePadding = 8.dp) {
            titles.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> { val l by vm.liked.collectAsState(); if (l.isEmpty()) EmptyState("Tap the heart on a song to save it here") else TrackList(vm, l) }
            1 -> PlaylistsTab(vm) { openPlaylist = it }
            2 -> LocalTab(vm)
            else -> { val d by vm.downloads.collectAsState(); if (d.isEmpty()) EmptyState("Downloaded songs show up here and play offline") else TrackList(vm, d) }
        }
    }
}

@Composable
private fun PlaylistsTab(vm: MusicViewModel, onOpen: (Playlist) -> Unit) {
    val playlists by vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    if (creating) NameDialog("New playlist", { creating = false }) { vm.createPlaylist(it) }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable { creating = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp)); Text("Create playlist")
            }
        }
        items(playlists, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(p) }.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.QueueMusic, null)
                Spacer(Modifier.width(16.dp))
                Text(p.name, Modifier.weight(1f))
                IconButton({ vm.deletePlaylist(p.id) }) { Icon(Icons.Default.Delete, "Delete playlist") }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(vm: MusicViewModel, playlist: Playlist, onBack: () -> Unit) {
    val tracks by vm.playlistTracks(playlist.id).collectAsState(emptyList())
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(playlist.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (tracks.isNotEmpty()) FilledIconButton({ vm.play(tracks, 0) }) { Icon(Icons.Default.PlayArrow, "Play all") }
            Spacer(Modifier.width(12.dp))
        }
        if (tracks.isEmpty()) EmptyState("This playlist is empty. Use ⋮ on any song to add it.") else
            TrackList(vm, tracks, extraMenu = { t, close ->
                DropdownMenuItem(
                    text = { Text("Remove from playlist") },
                    leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) },
                    onClick = { close(); vm.removeFromPlaylist(playlist.id, t) },
                )
            })
    }
}

@Composable
private fun LocalTab(vm: MusicViewModel) {
    val context = LocalContext.current
    val permissions = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun allGranted() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(allGranted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = allGranted()
    }
    val tracks by vm.localTracks.collectAsState()
    var filter by remember { mutableStateOf(LocalFilter.All) }

    LaunchedEffect(granted) { if (granted) vm.scanLocal() }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Allow access to your audio and video files to play music stored on this phone. Video files play as audio only.")
            Spacer(Modifier.height(12.dp))
            Button({ launcher.launch(permissions) }) { Text("Grant access") }
        }
        return
    }
    val folders by vm.localVideoFolders.collectAsState()
    val songFolders by vm.songFolders.collectAsState()
    val audio = tracks.filterNot { it.isVideo }
    val (videoSongs, otherVideos) = tracks.filter { it.isVideo }
        .partition { vm.isSongFolder(folders[it.id].orEmpty()) }
    val shown = when (filter) {
        LocalFilter.All -> audio + videoSongs
        LocalFilter.Audio -> audio
        LocalFilter.Video -> videoSongs
        LocalFilter.Other -> otherVideos
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(16.dp, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LocalFilter.entries.forEach {
                FilterChip(filter == it, { filter = it }, label = { Text(it.label) })
            }
        }
        if (shown.isEmpty()) EmptyState(
            when (filter) {
                LocalFilter.Video -> "No video songs yet. Put videos in a folder named Music or Songs, " +
                    "or open Other videos and use \u22EE \u2192 Use this folder for songs."
                else -> "No ${filter.label.lowercase()} files found on this device"
            },
        ) else TrackList(vm, shown, extraMenu = { t, close ->
            val folder = folders[t.id]
            if (t.isVideo && folder != null && folder.isNotBlank()) {
                if (folder in songFolders) DropdownMenuItem(
                    text = { Text("Stop using this folder for songs") },
                    onClick = { close(); vm.setSongFolder(folder, false) },
                ) else if (!vm.isSongFolder(folder)) DropdownMenuItem(
                    text = { Text("Use this folder for songs") },
                    onClick = { close(); vm.setSongFolder(folder, true) },
                )
            }
        })
    }
}

private enum class LocalFilter(val label: String) {
    All("All"), Audio("Songs"), Video("Video songs"), Other("Other videos")
}
