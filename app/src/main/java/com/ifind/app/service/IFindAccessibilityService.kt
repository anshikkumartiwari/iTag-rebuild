package com.ifind.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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

    fun click(x: Float, y: Float, durationMs: Long = 60L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            onComplete?.invoke()
            return false
        }

        return try {
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            val screenH = dm.heightPixels.toFloat()

            // Clamp coordinates safely within screen boundaries
            val safeX = x.coerceIn(10f, (screenW - 10f).coerceAtLeast(10f))
            val safeY = y.coerceIn(10f, (screenH - 10f).coerceAtLeast(10f))

            // 1-pixel line segment ensures Android InputDispatcher validates non-empty stroke without drift
            val clickPath = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY + 1f)
            }

            val stroke = GestureDescription.StrokeDescription(clickPath, 0L, durationMs.coerceIn(30L, 150L))
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
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            val screenH = dm.heightPixels.toFloat()

            val safeX = x.coerceIn(10f, (screenW - 10f).coerceAtLeast(10f))
            val safeY = y.coerceIn(10f, (screenH - 10f).coerceAtLeast(10f))

            val path1 = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY + 1f)
            }
            val path2 = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY + 1f)
            }

            // Atomic double-tap with two strokes in a single gesture
            val stroke1 = GestureDescription.StrokeDescription(path1, 0L, tapDurationMs.coerceIn(30L, 100L))
            val stroke2 = GestureDescription.StrokeDescription(path2, tapDurationMs + gapMs, tapDurationMs.coerceIn(30L, 100L))

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

    fun scrollVertical(startX: Float, startY: Float, distancePx: Float, durationMs: Long = 280L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            onComplete?.invoke()
            return false
        }

        return try {
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            val screenH = dm.heightPixels.toFloat()

            // Keep horizontal swipe safely within central 70% to avoid system edge gestures
            val safeStartX = startX.coerceIn(screenW * 0.15f, screenW * 0.85f)
            // Start vertical swipe from user position, or sensible reel flick zone
            val safeStartY = startY.coerceIn(screenH * 0.40f, screenH * 0.85f)

            // Clamp endY to be strictly positive and strictly above startY (swipe up to advance reel)
            val targetDistance = distancePx.coerceIn(200f, screenH * 0.70f)
            val safeEndY = (safeStartY - targetDistance).coerceIn(60f, safeStartY - 100f)

            val scrollPath = Path().apply {
                moveTo(safeStartX, safeStartY)
                lineTo(safeStartX, safeEndY)
            }

            val stroke = GestureDescription.StrokeDescription(scrollPath, 0L, durationMs.coerceIn(180L, 400L))
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Scroll gesture completed: from ($safeStartX, $safeStartY) to ($safeStartX, $safeEndY)")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Scroll gesture cancelled")
                    onComplete?.invoke()
                }
            }, null)

            Log.i(TAG, "dispatchGesture scroll from $safeStartY to $safeEndY: dispatched=$dispatched")
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

        fun clickAt(x: Float, y: Float, durationMs: Long = 60L, onComplete: (() -> Unit)? = null): Boolean {
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

        fun scrollVerticalAt(startX: Float, startY: Float, distancePx: Float, durationMs: Long = 280L, onComplete: (() -> Unit)? = null): Boolean {
            return instance?.scrollVertical(startX, startY, distancePx, durationMs, onComplete) ?: run {
                onComplete?.invoke()
                false
            }
        }
    }
}
