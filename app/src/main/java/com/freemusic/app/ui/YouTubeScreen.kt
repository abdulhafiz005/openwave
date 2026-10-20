package com.freemusic.app.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.freemusic.app.MusicViewModel
import com.freemusic.app.data.Video

@Composable
fun YouTubeScreen(vm: MusicViewModel) {
    val results by vm.videoResults.collectAsState()
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            placeholder = { Text("Search YouTube or paste a link") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.searchVideos(query) }),
        )
        if (results.isEmpty()) {
            EmptyState(
                "Search YouTube or paste a link. Videos play on YouTube's own player, with its ads, " +
                    "and stop when the screen is off.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(results, key = { it.id }) { v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openVideo(v) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        coil.compose.AsyncImage(
                            v.thumbnail, null,
                            Modifier.width(120.dp).aspectRatio(16f / 9f),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(v.channel, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * YouTube's own mobile site in an in-app browser. The embedded player ignores quality requests,
 * the site honors them, so everything opens here and is asked for the lowest quality.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebScreen(url: String, onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    androidx.activity.compose.BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close") }
                Text("YouTube", Modifier.weight(1f))
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                // Keep asking for the lowest quality; the player object appears once a video opens.
                                view.evaluateJavascript(LOW_QUALITY_JS, null)
                            }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl(url)
                        web = this
                    }
                },
                onRelease = { it.destroy(); web = null },
            )
        }
    }
}

private const val LOW_QUALITY_JS = """
(function () {
  if (window.__lowQuality) return;
  window.__lowQuality = setInterval(function () {
    var p = document.getElementById('movie_player');
    if (p && p.setPlaybackQualityRange) {
      p.setPlaybackQualityRange('tiny', 'tiny');
      console.log('quality=' + p.getPlaybackQuality());
    }
  }, 1500);
})();
"""
