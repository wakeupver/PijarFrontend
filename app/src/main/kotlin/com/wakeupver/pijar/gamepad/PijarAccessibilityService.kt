package com.wakeupver.pijar.gamepad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import com.wakeupver.pijar.R

class PijarAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager by lazy { getSystemService(WindowManager::class.java)!! }
    private var overlay: FrameLayout? = null
    private var mapping: Map<GamepadControl, PointF> = emptyMap()
    private var platformId = ""
    private var targetPackage = ""
    private var pendingShow: Runnable? = null
    private var calibration: CalibrationView? = null
    private var calibrationValues = LinkedHashMap<GamepadControl, PointF>()
    private var calibrationIndex = 0
    private var targetForeground = false
    private var shownAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        VirtualGamepad.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (overlay == null && calibration == null && pendingShow == null) return
        val current = event.packageName?.toString().orEmpty()
        if (current.isBlank()) return
        if (current == targetPackage) {
            targetForeground = true
            if (pendingShow != null) showCurrent()
            return
        }
        if (pendingShow != null) {
            if (current != packageName && current != "com.android.systemui") hideGamepad()
            return
        }
        if (SystemClock.elapsedRealtime() - shownAt < 2_500L) return
        if (current == packageName) {
            if (!targetForeground) hideGamepad()
            return
        }
        if (targetPackage.isNotBlank() && current != "com.android.systemui") {
            hideGamepad()
        }
    }

    override fun onInterrupt() {
        hideGamepad()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideGamepad()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideGamepad()
        VirtualGamepad.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideGamepad()
        VirtualGamepad.detach(this)
        super.onDestroy()
    }

    fun showGamepad(platformId: String, targetPackage: String?): Boolean {
        hideGamepad()
        this.platformId = platformId
        this.targetPackage = targetPackage.orEmpty()
        if (this.targetPackage.isBlank()) return showCurrent()
        val pending = Runnable { showCurrent() }
        pendingShow = pending
        handler.postDelayed(pending, 3_000L)
        return true
    }

    fun hideGamepad() {
        cancelPendingShow()
        removeOverlays()
        calibrationValues.clear()
        mapping = emptyMap()
        targetForeground = false
    }

    private fun showCurrent(): Boolean {
        cancelPendingShow()
        val stored = GamepadStore.load(this, platformId, targetPackage)
        if (stored == null) {
            startCalibration()
        } else {
            mapping = stored
            showButtons()
        }
        shownAt = SystemClock.elapsedRealtime()
        return overlay != null || calibration != null
    }

    private fun cancelPendingShow() {
        pendingShow?.let { handler.removeCallbacks(it) }
        pendingShow = null
    }

    private fun startCalibration() {
        calibrationIndex = 0
        calibrationValues = LinkedHashMap()
        val view = CalibrationView(
            this,
            onTap = { x, y -> recordCalibration(x, y) },
            onSkip = { hideGamepad() },
        )
        calibration = view
        if (!addOverlay(view, fullScreenParams())) {
            calibration = null
            return
        }
        updateCalibration()
    }

    private fun recordCalibration(x: Float, y: Float) {
        val controls = GamepadControl.all()
        if (calibrationIndex !in controls.indices) return
        val size = screenSize()
        if (size.x <= 0 || size.y <= 0) return
        val barHeight = GamepadLayout.build(this, size.x, size.y).height
        if (y <= barHeight) {
            calibration?.setMessage(getString(R.string.gamepad_calibrate_blocked))
            return
        }
        val control = controls[calibrationIndex]
        calibrationValues[control] = PointF(
            (x / size.x).coerceIn(0f, 1f),
            (y / size.y).coerceIn(0f, 1f),
        )
        calibrationIndex++
        if (calibrationIndex >= controls.size) {
            GamepadStore.save(this, platformId, targetPackage, calibrationValues)
            mapping = calibrationValues.toMap()
            removeOverlays()
            showButtons()
            shownAt = SystemClock.elapsedRealtime()
        } else {
            updateCalibration()
        }
    }

    private fun updateCalibration() {
        val controls = GamepadControl.all()
        if (calibrationIndex !in controls.indices) return
        val control = controls[calibrationIndex]
        calibration?.setMessage(
            getString(
                R.string.gamepad_calibrate_step,
                getString(control.labelRes),
                calibrationIndex + 1,
                controls.size,
            ),
        )
    }

    private fun showButtons() {
        if (mapping.isEmpty()) return
        val size = screenSize()
        val placement = GamepadLayout.build(this, size.x, size.y)
        val bar = FrameLayout(this)
        bar.setBackgroundColor(Color.TRANSPARENT)
        for (spec in placement.specs) {
            if (spec.control !in mapping) continue
            val view = GamepadButtonView(
                this,
                spec.control,
                onTap = { tap(it) },
            )
            bar.addView(
                view,
                FrameLayout.LayoutParams(spec.size, spec.size).apply {
                    leftMargin = spec.x
                    topMargin = spec.y
                },
            )
        }
        if (bar.childCount == 0) return
        if (addOverlay(bar, barParams(placement.height))) overlay = bar
    }

    private fun tap(control: GamepadControl) {
        val size = screenSize()
        if (size.x <= 0 || size.y <= 0) return
        val point = mapping[control] ?: return
        val path = Path().apply {
            moveTo(point.x * size.x, point.y * size.y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 140L))
            .build()
        runCatching { dispatchGesture(gesture, null, null) }
    }

    private fun addOverlay(view: View, params: WindowManager.LayoutParams): Boolean = try {
        windowManager.addView(view, params)
        true
    } catch (_: Exception) {
        false
    }

    private fun removeOverlays() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        calibration?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        calibration = null
    }

    private fun fullScreenParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun barParams(height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun screenSize(): Point {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        val point = Point()
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        display.getRealSize(point)
        return point
    }
}
