package com.freemusic.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.freemusic.app.R

/** Shows a download's progress, then its result, as a notification. */
class DownloadNotifier(private val context: Context, private val id: Int) {
    private val manager = NotificationManagerCompat.from(context)
    private var lastPercent = -1

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun builder(title: String) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_openwave)
        .setContentTitle(title)
        .setOnlyAlertOnce(true)

    private fun show(b: NotificationCompat.Builder, id: Int = this.id) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) runCatching { manager.notify(id, b.build()) }
    }

    /** The first notification of a download, which the foreground service shows while it starts. */
    fun starting() = builder("Downloading audio").setContentText("Starting").setOngoing(true)
        .setProgress(0, 0, true).build()

    /** [total] is unknown (<= 0) until the first chunk arrives. */
    fun progress(title: String, done: Long, total: Long) {
        val percent = if (total > 0) (done * 100 / total).toInt() else -1
        if (percent == lastPercent && percent >= 0) return
        lastPercent = percent
        show(
            builder(title).setContentText("Downloading").setOngoing(true)
                .setProgress(100, percent.coerceAtLeast(0), percent < 0),
        )
    }

    /**
     * The result goes under its own id: reusing the progress notification's id lets the system keep
     * it flagged as ongoing while the service is still winding down.
     */
    fun finished(title: String, text: String) {
        manager.cancel(id)
        show(builder(title).setContentText(text).setAutoCancel(true), id + 1)
    }

    fun cancel() = manager.cancel(id)

    private companion object {
        const val CHANNEL = "downloads"
    }
}
