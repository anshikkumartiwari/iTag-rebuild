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

    fun click(x: Float, y: Float, durationMs: Long = 50L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            return false
        }

        return try {
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            val screenH = dm.heightPixels.toFloat()

            // Clamp coordinates safely within screen boundaries
            val safeX = x.coerceIn(10f, (screenW - 10f).coerceAtLeast(10f))
            val safeY = y.coerceIn(10f, (screenH - 10f).coerceAtLeast(10f))

            val clickPath = Path().apply {
                moveTo(safeX, safeY)
            }

            val stroke = GestureDescription.StrokeDescription(clickPath, 0L, durationMs.coerceIn(20L, 200L))
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
                }
            }, null)

            Log.i(TAG, "dispatchGesture tap invoked at ($safeX, $safeY): dispatched=$dispatched")
            dispatched
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch click gesture safely: ${t.message}", t)
            false
        }
    }

    fun doubleClick(x: Float, y: Float, tapDurationMs: Long = 50L, gapMs: Long = 100L, onComplete: (() -> Unit)? = null): Boolean {
        return click(x, y, tapDurationMs) {
            mainHandler.postDelayed({
                click(x, y, tapDurationMs, onComplete)
            }, gapMs)
        }
    }

    fun scrollVertical(startX: Float, startY: Float, distancePx: Float, durationMs: Long = 260L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            return false
        }

        return try {
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            val screenH = dm.heightPixels.toFloat()

            // Safe horizontal swipe coordinate: keep within central 50% of screen to avoid system back gesture zones!
            val safeStartX = startX.coerceIn(screenW * 0.25f, screenW * 0.75f)
            val safeStartY = startY.coerceIn(screenH * 0.50f, screenH * 0.85f)

            // Clamp endY to be strictly positive and strictly higher on the screen (swipe up to advance reel)
            val targetDistance = distancePx.coerceIn(250f, screenH * 0.75f)
            val safeEndY = (safeStartY - targetDistance).coerceIn(screenH * 0.10f, safeStartY - 100f)

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
                }
            }, null)

            Log.i(TAG, "dispatchGesture scroll from $safeStartY to $safeEndY: dispatched=$dispatched")
            dispatched
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch scroll gesture safely: ${t.message}", t)
            false
        }
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
