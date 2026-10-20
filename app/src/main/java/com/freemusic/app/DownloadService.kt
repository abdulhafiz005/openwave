package com.freemusic.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.freemusic.app.data.AppDb
import com.freemusic.app.data.DownloadNotifier
import com.freemusic.app.data.Downloader
import com.freemusic.app.data.YouTubeDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads YouTube audio in the foreground, so the download keeps going when the app is in the
 * background and its progress stays in the notification shade.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val active = AtomicInteger(0)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID)
        if (videoId == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val notifier = DownloadNotifier(this, videoId.hashCode())
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, videoId.hashCode(), notifier.starting(), type)
        active.incrementAndGet()
        scope.launch {
            var title = "YouTube audio"
            runCatching {
                withContext(Dispatchers.IO) {
                    val audio = YouTubeDownloader.resolve(videoId)
                    title = audio.track.title
                    notifier.progress(title, 0, 0)
                    val downloader = Downloader(applicationContext)
                    val path = audio.open { done, total -> notifier.progress(title, done, total) }
                        .use { downloader.saveAudio(audio.track, audio.ext, audio.mime, it) }
                    val dao = AppDb.create(applicationContext).dao()
                    dao.insertTrackIfAbsent(audio.track)
                    dao.setDownloaded(audio.track.id, path)
                }
            }.onSuccess {
                finish(notifier, title, "Saved to Music")
            }.onFailure {
                android.util.Log.e("DownloadService", "download of $videoId failed", it)
                finish(notifier, title, "Download failed: ${it.message}")
            }
            if (active.decrementAndGet() == 0) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun finish(notifier: DownloadNotifier, title: String, text: String) {
        // The last download takes the foreground notification with it; the result is posted separately.
        val flag = if (active.get() <= 1) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH
        ServiceCompat.stopForeground(this, flag)
        notifier.finished(title, text)
        Toast.makeText(this, "$title: $text", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "video_id"

        fun start(context: Context, videoId: String) =
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).putExtra(EXTRA_VIDEO_ID, videoId),
            )
    }
}
