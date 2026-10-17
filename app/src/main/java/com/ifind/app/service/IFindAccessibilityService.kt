package com.ifind.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.content.Context
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AccessibilityService"

class IFindAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isAccessibilityEnabled.value = true
        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply FLAG_RETRIEVE_INTERACTIVE_WINDOWS: ${e.message}")
        }
        Log.i(TAG, "IFindAccessibilityService connected and ready for synthetic gestures.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used; gestures only
    }

    override fun onInterrupt() {
        Log.w(TAG, "IFindAccessibilityService interrupted.")
        _isAccessibilityEnabled.value = false
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        _isAccessibilityEnabled.value = false
        super.onDestroy()
    }

    private fun getScreenDimensions(): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val bounds = wm?.currentWindowMetrics?.bounds
            if (bounds != null) {
                Pair(bounds.width().toFloat(), bounds.height().toFloat())
            } else {
                val dm = resources.displayMetrics
                Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
            }
        } else {
            val dm = resources.displayMetrics
            Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
        }
    }

    fun click(x: Float, y: Float, durationMs: Long = 50L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            onComplete?.invoke()
            return false
        }

        return try {
            val (screenW, screenH) = getScreenDimensions()

            // Clamp coordinates safely within screen boundaries
            val safeX = x.coerceIn(5f, screenW - 5f)
            val safeY = y.coerceIn(5f, screenH - 5f)

            // 1-pixel micro line ensures Android InputDispatcher validates non-empty stroke without drift
            val clickPath = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY + 1f)
            }

            val stroke = GestureDescription.StrokeDescription(clickPath, 0L, durationMs.coerceIn(30L, 120L))
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Tap completed at ($safeX, $safeY)")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Tap cancelled at ($safeX, $safeY)")
                    onComplete?.invoke()
                }
            }, null)

            Log.i(TAG, "dispatchGesture tap invoked at ($safeX, $safeY): dispatched=$dispatched")
            if (!dispatched) {
                onComplete?.invoke()
            }
            dispatched
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch click gesture safely: ${t.message}", t)
            onComplete?.invoke()
            false
        }
    }

    fun doubleClick(x: Float, y: Float, tapDurationMs: Long = 50L, gapMs: Long = 90L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete?.invoke()
            return false
        }

        return try {
            val (screenW, screenH) = getScreenDimensions()

            val safeX = x.coerceIn(5f, screenW - 5f)
            val safeY = y.coerceIn(5f, screenH - 5f)

            val path1 = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY + 1f)
            }
            val path2 = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY + 1f)
            }

            // Atomic double-tap with two strokes in a single gesture
            val stroke1 = GestureDescription.StrokeDescription(path1, 0L, tapDurationMs.coerceIn(30L, 80L))
            val stroke2 = GestureDescription.StrokeDescription(path2, tapDurationMs + gapMs, tapDurationMs.coerceIn(30L, 80L))

            val gesture = GestureDescription.Builder()
                .addStroke(stroke1)
                .addStroke(stroke2)
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Double tap completed at ($safeX, $safeY)")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Double tap cancelled at ($safeX, $safeY)")
                    onComplete?.invoke()
                }
            }, null)

            if (!dispatched) {
                onComplete?.invoke()
            }
            dispatched
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch double click gesture safely: ${t.message}", t)
            onComplete?.invoke()
            false
        }
    }

    fun scrollVertical(
        distancePx: Float,
        customStartX: Float? = null,
        durationMs: Long = 260L,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            onComplete?.invoke()
            return false
        }

        return try {
            val (screenW, screenH) = getScreenDimensions()

            // Horizontal position: Center of the screen (safe from edge back-gesture zones)
            val safeX = customStartX?.coerceIn(screenW * 0.30f, screenW * 0.70f) ?: (screenW * 0.50f)

            // Vertical scroll: Starts in the natural lower flick zone (~72% screen height)
            val startY = screenH * 0.72f

            // Distance to swipe: Clamped to healthy flick distance (between 25% and 55% screen height)
            val targetDistance = distancePx.coerceIn(screenH * 0.25f, screenH * 0.55f)
            val endY = (startY - targetDistance).coerceIn(screenH * 0.18f, startY - 150f)

            val scrollPath = Path().apply {
                moveTo(safeX, startY)
                lineTo(safeX, endY)
            }

            val stroke = GestureDescription.StrokeDescription(scrollPath, 0L, durationMs.coerceIn(180L, 350L))
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Scroll gesture completed: from ($safeX, $startY) to ($safeX, $endY)")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Scroll gesture cancelled")
                    onComplete?.invoke()
                }
            }, null)

            Log.i(TAG, "dispatchGesture scroll from $startY to $endY: dispatched=$dispatched")
            if (!dispatched) {
                onComplete?.invoke()
            }
            dispatched
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch scroll gesture safely: ${t.message}", t)
            onComplete?.invoke()
            false
        }
    }

    companion object {
        var instance: IFindAccessibilityService? = null
            private set

        private val _isAccessibilityEnabled = MutableStateFlow(false)
        val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

        fun isRunning(): Boolean = instance != null

        fun clickAt(x: Float, y: Float, durationMs: Long = 50L, onComplete: (() -> Unit)? = null): Boolean {
            return instance?.click(x, y, durationMs, onComplete) ?: run {
                onComplete?.invoke()
                false
            }
        }

        fun doubleClickAt(x: Float, y: Float, tapDurationMs: Long = 50L, gapMs: Long = 90L, onComplete: (() -> Unit)? = null): Boolean {
            return instance?.doubleClick(x, y, tapDurationMs, gapMs, onComplete) ?: run {
                onComplete?.invoke()
                false
            }
        }

        fun scrollVerticalAt(
            distancePx: Float,
            startX: Float? = null,
            durationMs: Long = 260L,
            onComplete: (() -> Unit)? = null
        ): Boolean {
            return instance?.scrollVertical(distancePx, startX, durationMs, onComplete) ?: run {
                onComplete?.invoke()
                false
            }
        }

        fun scrollVerticalAt(
            startX: Float,
            startY: Float,
            distancePx: Float,
            durationMs: Long = 260L,
            onComplete: (() -> Unit)? = null
        ): Boolean {
            return instance?.scrollVertical(distancePx, startX, durationMs, onComplete) ?: run {
                onComplete?.invoke()
                false
            }
        }
    }
}
