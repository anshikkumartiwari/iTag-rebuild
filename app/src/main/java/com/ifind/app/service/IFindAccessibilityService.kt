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

    fun click(x: Float, y: Float, durationMs: Long = 80L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            return false
        }

        return try {
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            val screenH = dm.heightPixels.toFloat()

            // Clamp coordinates safely within screen boundaries (prevent negative or out of bounds coordinates)
            val safeX = x.coerceIn(10f, (screenW - 10f).coerceAtLeast(10f))
            val safeY = y.coerceIn(10f, (screenH - 10f).coerceAtLeast(10f))

            val clickPath = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX + 1f, safeY + 1f)
            }

            val stroke = GestureDescription.StrokeDescription(clickPath, 0, durationMs.coerceIn(20L, 500L))
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Tap completed at ($safeX, $safeY)")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Tap cancelled at ($safeX, $safeY)")
                }
            }, null)

            Log.d(TAG, "dispatchGesture tap invoked at ($safeX, $safeY): dispatched=$dispatched")
            dispatched
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch click gesture safely: ${t.message}", t)
            false
        }
    }

    fun doubleClick(x: Float, y: Float, tapDurationMs: Long = 60L, gapMs: Long = 100L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
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
                lineTo(safeX + 1f, safeY + 1f)
            }
            val path2 = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX + 1f, safeY + 1f)
            }

            val stroke1 = GestureDescription.StrokeDescription(path1, 0, tapDurationMs)
            val stroke2 = GestureDescription.StrokeDescription(path2, tapDurationMs + gapMs, tapDurationMs)

            val gesture = GestureDescription.Builder()
                .addStroke(stroke1)
                .addStroke(stroke2)
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Double tap completed at ($safeX, $safeY)")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Double tap cancelled at ($safeX, $safeY)")
                }
            }, null)

            dispatched
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch double click gesture safely: ${t.message}", t)
            false
        }
    }

    fun scrollVertical(startX: Float, startY: Float, distancePx: Float, durationMs: Long = 280L, onComplete: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires Android N (API 24)+")
            return false
        }

        return try {
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            val screenH = dm.heightPixels.toFloat()

            // Safe start coordinate in middle-lower third
            val safeStartX = startX.coerceIn(10f, (screenW - 10f).coerceAtLeast(10f))
            val safeStartY = startY.coerceIn(screenH * 0.40f, screenH * 0.85f)

            // Clamp endY to be strictly positive and strictly higher on the screen
            val targetDistance = distancePx.coerceIn(200f, screenH * 0.70f)
            val safeEndY = (safeStartY - targetDistance).coerceIn(screenH * 0.10f, safeStartY - 80f)

            val scrollPath = Path().apply {
                moveTo(safeStartX, safeStartY)
                lineTo(safeStartX, safeEndY)
            }

            val stroke = GestureDescription.StrokeDescription(scrollPath, 0, durationMs.coerceIn(150L, 600L))
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Scroll gesture completed: from $safeStartY to $safeEndY")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Scroll gesture cancelled")
                }
            }, null)

            Log.d(TAG, "dispatchGesture scroll from $safeStartY to $safeEndY: dispatched=$dispatched")
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
