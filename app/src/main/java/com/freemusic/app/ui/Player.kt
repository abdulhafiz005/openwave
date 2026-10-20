package com.freemusic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.freemusic.app.MusicViewModel

@Composable
fun MiniPlayer(vm: MusicViewModel, onOpen: () -> Unit) {
    val track by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val pos by vm.positionMs.collectAsState()
    val dur by vm.durationMs.collectAsState()
    val t = track ?: return

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(t.artUri, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ vm.togglePlay() }) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pause")
                }
                IconButton({ vm.stop() }) { Icon(Icons.Default.Close, "Close player") }
            }
            LinearProgressIndicator(
                progress = { if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun FullPlayer(vm: MusicViewModel, onClose: () -> Unit) {
    val track by vm.currentTrack.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val pos by vm.positionMs.collectAsState()
    val dur by vm.durationMs.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeatMode.collectAsState()
    val liked by vm.liked.collectAsState()
    val t = track ?: run { onClose(); return }
    val isLiked = liked.any { it.id == t.id }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close") }
                Text("Now playing", Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.weight(1f))
            Artwork(t.artUri, 300.dp)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ vm.toggleLike(t) }) {
                    Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (isLiked) primary else dim)
                }
            }
            Slider(
                value = dragging ?: if (dur > 0) pos.toFloat() / dur else 0f,
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { vm.seekTo((it * dur).toLong()) }; dragging = null },
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(formatTime(pos), style = MaterialTheme.typography.bodySmall, color = dim)
                Text(formatTime(dur), style = MaterialTheme.typography.bodySmall, color = dim)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                IconButton({ vm.toggleShuffle() }) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) primary else dim) }
                IconButton({ vm.previous() }) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                FilledIconButton({ vm.togglePlay() }, Modifier.size(64.dp)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pause", Modifier.size(36.dp))
                }
                IconButton({ vm.next() }) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
                IconButton({ vm.cycleRepeat() }) {
                    Icon(
                        if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat", tint = if (repeat == Player.REPEAT_MODE_OFF) dim else primary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
