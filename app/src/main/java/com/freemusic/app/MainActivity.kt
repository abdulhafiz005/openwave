package com.freemusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.freemusic.app.ui.FreeMusicApp
import com.freemusic.app.ui.FreeMusicTheme

class MainActivity : ComponentActivity() {
    private val vm: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreeMusicTheme { FreeMusicApp(vm) }
        }
    }
}
