package com.freemusic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.freemusic.app.MusicViewModel
import com.freemusic.app.data.Track

@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { android.util.Log.w("Artwork", "load failed: $url", it.result.throwable) },
        )
    }
}

/** A scrollable list of tracks; tapping a row plays the whole list from that row. */
@Composable
fun TrackList(
    vm: MusicViewModel,
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    extraMenu: (@Composable (Track, () -> Unit) -> Unit)? = null,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        if (header != null) item { header() }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(vm, track, onClick = { vm.play(tracks, index) }, extraMenu = extraMenu)
        }
    }
}

/**
 * Two groups of tracks in one list, with a heading above each. With only one non-empty group
 * there are no headings. Tapping a row plays both groups as one queue, in the order shown.
 */
@Composable
fun SectionedTrackList(
    vm: MusicViewModel,
    first: List<Track>,
    second: List<Track>,
    firstTitle: String,
    secondTitle: String,
    modifier: Modifier = Modifier,
    extraMenu: (@Composable (Track, () -> Unit) -> Unit)? = null,
) {
    val queue = first + second
    val showHeadings = first.isNotEmpty() && second.isNotEmpty()
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        if (showHeadings) item(key = "h1") { SectionTitle(firstTitle) }
        itemsIndexed(first, key = { _, t -> t.id }) { i, t ->
            TrackRow(vm, t, onClick = { vm.play(queue, i) }, extraMenu = extraMenu)
        }
        if (showHeadings) item(key = "h2") {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle(secondTitle)
        }
        itemsIndexed(second, key = { _, t -> t.id }) { i, t ->
            TrackRow(vm, t, onClick = { vm.play(queue, first.size + i) }, extraMenu = extraMenu)
        }
    }
}

@Composable
fun TrackRow(
    vm: MusicViewModel,
    track: Track,
    onClick: () -> Unit,
    extraMenu: (@Composable (Track, () -> Unit) -> Unit)? = null,
) {
    val liked by vm.liked.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val current by vm.currentTrack.collectAsState()
    val isLiked = liked.any { it.id == track.id }
    val isDownloaded = downloads.any { it.id == track.id }
    var menu by remember { mutableStateOf(false) }
    var pickPlaylist by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artUri, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (current?.id == track.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (track.isVideo) Icon(Icons.Default.Videocam, "Video file, plays as audio", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        if (isDownloaded) Icon(Icons.Default.DownloadDone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        IconButton({ vm.toggleLike(track) }) {
            Icon(
                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like",
                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) },
                    onClick = { menu = false; pickPlaylist = true },
                )
                if (track.downloadable || isDownloaded) {
                    if (isDownloaded) DropdownMenuItem(
                        text = { Text("Remove download") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = {
                            menu = false
                            vm.removeDownload(downloads.first { it.id == track.id })
                        },
                    ) else DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                        onClick = { menu = false; vm.download(track) },
                    )
                }
                extraMenu?.invoke(track) { menu = false }
            }
        }
    }
    if (pickPlaylist) PlaylistPicker(vm, track) { pickPlaylist = false }
}

@Composable
fun PlaylistPicker(vm: MusicViewModel, track: Track, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New playlist", onDismiss = { creating = false }) { vm.createPlaylist(it) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                TextButton({ creating = true }) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New playlist")
                }
                playlists.forEach { p ->
                    Text(
                        p.name,
                        Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p.id, track); onDismiss() }.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton({ onConfirm(name); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))

@Composable
fun EmptyState(text: String) =
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
