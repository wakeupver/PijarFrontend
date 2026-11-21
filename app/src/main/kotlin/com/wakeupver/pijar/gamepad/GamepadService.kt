package com.wakeupver.pijar.gamepad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wakeupver.pijar.MainActivity
import com.wakeupver.pijar.R

class GamepadService : Service() {
    private val windowManager: WindowManager by lazy { getSystemService(WindowManager::class.java) }
    private val backend: GamepadBackend = UinputGamepadBackend()
    private val views = ArrayList<View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!backend.start()) {
            Toast.makeText(this, R.string.msg_gamepad_root_failed, Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay()
        if (views.isEmpty()) {
            Toast.makeText(this, R.string.msg_gamepad_overlay_failed, Toast.LENGTH_SHORT).show()
            backend.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        backend.releaseAll()
        backend.stop()
        super.onDestroy()
    }

    private fun showOverlay() {
        hideOverlay()
        val metrics = resources.displayMetrics
        val placement = GamepadLayout.build(this, metrics.widthPixels, metrics.heightPixels)
        for (spec in placement.buttons) {
            val view = GamepadButtonView(this, spec.control, backend)
            if (addWindow(view, spec.x, spec.y, spec.size)) views += view
        }
        for (spec in placement.sticks) {
            val view = GamepadStickView(this, spec.side, backend)
            if (addWindow(view, spec.x, spec.y, spec.size)) views += view
        }
    }

    private fun addWindow(view: View, x: Int, y: Int, size: Int): Boolean = try {
        windowManager.addView(
            view,
            WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            },
        )
        true
    } catch (_: Exception) {
        false
    }

    private fun hideOverlay() {
        for (view in views) runCatching { windowManager.removeView(view) }
        views.clear()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.gamepad_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.gamepad_notification_title))
            .setContentText(getString(R.string.gamepad_notification_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pijar_gamepad"
        private const val NOTIFICATION_ID = 2401

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GamepadService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GamepadService::class.java))
        }
    }
}
