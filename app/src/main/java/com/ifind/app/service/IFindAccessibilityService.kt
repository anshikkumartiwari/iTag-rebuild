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

    fun click(x: Float, y: Float, durationMs: Long = 100L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            return false
        }

        // Must have a non-zero line length to avoid being dropped by Android input pipelines
        val clickPath = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }

        val stroke = GestureDescription.StrokeDescription(clickPath, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap completed at ($x, $y)")
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled at ($x, $y)")
            }
        }, null)

        Log.d(TAG, "dispatchGesture tap invoked at ($x, $y): dispatched=$dispatched")
        return dispatched
    }

    fun doubleClick(x: Float, y: Float, tapDurationMs: Long = 75L, gapMs: Long = 110L, onComplete: (() -> Unit)? = null): Boolean {
        return click(x, y, tapDurationMs) {
            mainHandler.postDelayed({
                click(x, y, tapDurationMs, onComplete)
            }, gapMs)
        }
    }

    fun scrollVertical(startX: Float, startY: Float, distancePx: Float, durationMs: Long = 280L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            return false
        }

        val scrollPath = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, startY - distancePx)
        }

        val stroke = GestureDescription.StrokeDescription(scrollPath, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Scroll gesture completed: swiped up $distancePx px")
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Scroll gesture cancelled")
            }
        }, null)

        Log.d(TAG, "dispatchGesture scroll invoked (distance: $distancePx px): dispatched=$dispatched")
        return dispatched
    }

    companion object {
        var instance: IFindAccessibilityService? = null
            private set

        private val _isAccessibilityEnabled = MutableStateFlow(false)
        val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

        fun isRunning(): Boolean = instance != null

        fun clickAt(x: Float, y: Float, durationMs: Long = 100L, onComplete: (() -> Unit)? = null): Boolean {
            return instance?.click(x, y, durationMs, onComplete) ?: false
        }

        fun doubleClickAt(x: Float, y: Float, tapDurationMs: Long = 75L, gapMs: Long = 110L, onComplete: (() -> Unit)? = null): Boolean {
            return instance?.doubleClick(x, y, tapDurationMs, gapMs, onComplete) ?: false
        }

        fun scrollVerticalAt(startX: Float, startY: Float, distancePx: Float, durationMs: Long = 280L, onComplete: (() -> Unit)? = null): Boolean {
            return instance?.scrollVertical(startX, startY, distancePx, durationMs, onComplete) ?: false
        }
    }
}
