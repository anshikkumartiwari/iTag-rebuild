package com.ifind.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ifind.app.IFindApp
import com.ifind.app.R
import com.ifind.app.ble.model.ButtonEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "FloatingClickerService"

class FloatingClickerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingRootView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var buttonListenerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
        listenToTrackerButtonEvents()
        _isFloatingClickerActive.value = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300
            y = 500
        }

        // Programmatically build the Auto Clicker target view
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Target Reticle (Outer Ring + Inner Target Pin)
        val targetFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }

        // Outer Reticle Circle with Cyan border
        val outerRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x3300D2FF.toInt())
                setStroke(dp(3), 0xFF00D2FF.toInt())
            }
            background = drawable
        }

        // Crosshairs horizontal
        val hLine = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(64), dp(2)).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundColor(0x8800D2FF.toInt())
        }

        // Crosshairs vertical
        val vLine = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(2), dp(64)).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundColor(0x8800D2FF.toInt())
        }

        // Center Pin (Exact Click Point)
        val centerDot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(12), dp(12)).apply {
                gravity = Gravity.CENTER
            }
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFFF0055.toInt())
                setStroke(dp(2), 0xFFFFFFFF.toInt())
            }
            background = drawable
        }

        targetFrame.addView(outerRing)
        targetFrame.addView(hLine)
        targetFrame.addView(vLine)
        targetFrame.addView(centerDot)

        // Control Pill (Coordinates + Test Click + Close)
        val controlPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val pillBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(0xCC090D16.toInt())
                setStroke(dp(1), 0x6600D2FF.toInt())
            }
            background = pillBg
        }

        val coordLabel = TextView(this).apply {
            text = "iTAG Target"
            setTextColor(0xFF00D2FF.toInt())
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, dp(8), 0)
        }

        // Test Click Button
        val testClickBtn = TextView(this).apply {
            text = "TAP"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            val btnBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0xFF2563EB.toInt())
            }
            background = btnBg
            setOnClickListener {
                performClickAtTarget(targetFrame)
            }
        }

        // Close Button
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(0xFFEF4444.toInt())
            textSize = 12f
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener {
                stopSelf()
            }
        }

        controlPill.addView(coordLabel)
        controlPill.addView(testClickBtn)
        controlPill.addView(closeBtn)

        rootLayout.addView(targetFrame)
        rootLayout.addView(controlPill)

        // Touch listener for smooth dragging anywhere on the screen
        rootLayout.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = initialX + (event.rawX - initialTouchX).toInt()
                    p.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(rootLayout, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }

        floatingRootView = rootLayout
        try {
            windowManager?.addView(rootLayout, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view: ${e.message}")
            stopSelf()
        }
    }

    private fun listenToTrackerButtonEvents() {
        val bleManager = IFindApp.instance.bleManager

        buttonListenerJob = scope.launch {
            bleManager.buttonEvents.collect { event ->
                when (event) {
                    is ButtonEvent.SinglePress -> {
                        Log.i(TAG, "Tracker button pressed! Dispatching floating clicker tap...")
                        floatingRootView?.let { root ->
                            performClickAtTarget(root)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun performClickAtTarget(view: View) {
        val p = params ?: return
        val density = resources.displayMetrics.density
        val targetRadius = (32 * density) // 64dp diameter / 2

        val targetScreenX = p.x + targetRadius
        val targetScreenY = p.y + targetRadius

        // Visual click feedback: scale animation
        view.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(80)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(80)
                    .start()
            }
            .start()

        // Haptic feedback
        vibratePhone()

        // Dispatch synthetic click via Accessibility Service
        val success = IFindAccessibilityService.clickAt(targetScreenX, targetScreenY)
        if (!success) {
            if (!IFindAccessibilityService.isRunning()) {
                Toast.makeText(
                    this,
                    "Please enable iFind Accessibility Service in Android Settings to allow remote clicks",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun vibratePhone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(40)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        buttonListenerJob?.cancel()
        floatingRootView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        floatingRootView = null
        _isFloatingClickerActive.value = false
        super.onDestroy()
    }

    companion object {
        private val _isFloatingClickerActive = MutableStateFlow(false)
        val isFloatingClickerActive: StateFlow<Boolean> = _isFloatingClickerActive.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, FloatingClickerService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingClickerService::class.java)
            context.stopService(intent)
        }
    }
}
