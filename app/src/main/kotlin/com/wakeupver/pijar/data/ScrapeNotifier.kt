package com.wakeupver.pijar.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wakeupver.pijar.MainActivity
import com.wakeupver.pijar.R

class ScrapeNotifier(context: Context) {
    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    init {
        createChannel()
    }

    @Synchronized
    fun start(total: Int) {
        val text = if (total > 0) {
            app.getString(R.string.notification_scrape_progress, 0, total)
        } else {
            app.getString(R.string.notification_scrape_title)
        }
        post(total = total, done = 0, text = text, ongoing = true, autoCancel = false, indeterminate = total <= 0)
    }

    @Synchronized
    fun progress(done: Int, total: Int) {
        val text = if (total > 0) {
            app.getString(R.string.notification_scrape_progress, done, total)
        } else {
            app.getString(R.string.notification_scrape_title)
        }
        post(total = total, done = done, text = text, ongoing = true, autoCancel = false, indeterminate = total <= 0)
    }

    @Synchronized
    fun finish(updated: Int, total: Int) {
        val text = app.getString(R.string.notification_scrape_done, updated, total)
        post(total = 0, done = 0, text = text, ongoing = false, autoCancel = true, indeterminate = false)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            app.getString(R.string.notification_channel_scrape),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setSound(null, null)
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
    }

    private fun post(
        total: Int,
        done: Int,
        text: String,
        ongoing: Boolean,
        autoCancel: Boolean,
        indeterminate: Boolean,
    ) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(app.getString(R.string.notification_scrape_title))
            .setContentText(text)
            .setContentIntent(openIntent())
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (indeterminate) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(total, done.coerceAtMost(total), false)
                }
            }
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openIntent(): PendingIntent {
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            app,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "scrape"
        const val NOTIFICATION_ID = 2401
    }
}
