package com.freemusic.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.freemusic.app.MusicViewModel

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Search("Search", Icons.Default.Search),
    YouTube("YouTube", Icons.Default.PlayCircle),
    Library("Your Library", Icons.Default.LibraryMusic),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeMusicApp(vm: MusicViewModel) {
    var tab by remember { mutableStateOf(HomeTab.Home) }
    var showPlayer by remember { mutableStateOf(false) }
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Android 13+ needs notification permission for the playback notification.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val notice by vm.notice.collectAsState()
    LaunchedEffect(notice) {
        notice?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short); vm.dismissNotice() }
    }
    LaunchedEffect(error) {
        // Home shows the missing-key message inline; use the snackbar for transient failures.
        error?.takeIf { vm.trending.value.isNotEmpty() || vm.audiusTrending.value.isNotEmpty() || tab != HomeTab.Home }?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                MiniPlayer(vm) { showPlayer = true }
                NavigationBar {
                    HomeTab.entries.forEach {
                        NavigationBarItem(tab == it, { tab = it }, { Icon(it.icon, it.label) }, label = { Text(it.label) })
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                HomeTab.Home -> HomeScreen(vm)
                HomeTab.Search -> SearchScreen(vm)
                HomeTab.YouTube -> YouTubeScreen(vm)
                HomeTab.Library -> LibraryScreen(vm)
            }
        }
    }

    val webUrl by vm.youtubeWebUrl.collectAsState()
    webUrl?.let { YouTubeWebScreen(it) { vm.closeYoutubeWeb() } }

    if (showPlayer) {
        BackHandler { showPlayer = false }
        FullPlayer(vm) { showPlayer = false }
    }
}
