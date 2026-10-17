package com.ifind.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AccessibilityService"

class IFindAccessibilityService : AccessibilityService() {

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

        val clickPath = Path().apply {
            moveTo(x, y)
        }

        val stroke = GestureDescription.StrokeDescription(clickPath, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture completed successfully at ($x, $y)")
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture cancelled at ($x, $y)")
            }
        }, null)

        Log.d(TAG, "dispatchGesture invoked at ($x, $y): dispatched=$dispatched")
        return dispatched
    }

    companion object {
        var instance: IFindAccessibilityService? = null
            private set

        private val _isAccessibilityEnabled = MutableStateFlow(false)
        val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

        fun isRunning(): Boolean = instance != null

        fun clickAt(x: Float, y: Float, durationMs: Long = 50L, onComplete: (() -> Unit)? = null): Boolean {
            return instance?.click(x, y, durationMs, onComplete) ?: false
        }
    }
}
