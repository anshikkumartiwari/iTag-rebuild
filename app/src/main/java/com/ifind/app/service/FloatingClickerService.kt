package com.ifind.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ifind.app.IFindApp
import com.ifind.app.MainActivity
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
private const val CHANNEL_ID_FLOATER = "ifind_floater_channel"
private const val NOTIFICATION_ID_FLOATER = 2002
private const val ACTION_STOP = "com.ifind.app.action.STOP_FLOATER"

class FloatingClickerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingRootView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var centerDotView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isGestureInProgress = false
    private var isSimulatingGesture = false
    private var isViewAttached = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun safeRemoveView() {
        if (!isViewAttached) return
        val root = floatingRootView ?: return
        try {
            windowManager?.removeView(root)
            isViewAttached = false
        } catch (e: Exception) {
            Log.w(TAG, "Error removing floating view: ${e.message}")
        }
    }

    private fun safeAddView() {
        if (isViewAttached) return
        val root = floatingRootView ?: return
        val p = params ?: return
        try {
            windowManager?.addView(root, p)
            isViewAttached = true
        } catch (e: Exception) {
            Log.w(TAG, "Error adding floating view: ${e.message}")
        }
    }

    private var buttonListenerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startFloaterForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
        listenToTrackerButtonEvents()
        _isFloatingClickerActive.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID_FLOATER,
                "Floating Auto-Clicker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating clicker active over other apps"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun startFloaterForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingClickerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_FLOATER)
            .setContentTitle("iFind Floater Active")
            .setContentText("Ready for iTAG remote clicks and scrolls")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Floater", stopPendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID_FLOATER,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID_FLOATER, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed startForeground for floater: ${e.message}", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun createFloatingView() {
        val prefs = IFindApp.instance.preferencesManager
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

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
            x = 260
            y = 520
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(16))
            clipToPadding = false
        }

        // ================= TARGET RETICLE =================
        val targetFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }

        val outerRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x332563EB.toInt())
                setStroke(dp(3), 0xFF2563EB.toInt())
            }
            background = drawable
        }

        val hLine = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(64), dp(2)).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundColor(0x882563EB.toInt())
        }

        val vLine = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(2), dp(64)).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundColor(0x882563EB.toInt())
        }

        val centerDot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(14), dp(14)).apply {
                gravity = Gravity.CENTER
            }
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFF3B30.toInt())
                setStroke(dp(2), Color.WHITE)
            }
            background = drawable
        }

        centerDotView = centerDot

        targetFrame.addView(outerRing)
        targetFrame.addView(hLine)
        targetFrame.addView(vLine)
        targetFrame.addView(centerDot)

        // ================= SETTINGS POPUP PANEL =================
        val settingsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val panelBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(0xF01C1C1E.toInt())
                setStroke(dp(1), 0x55FFFFFF.toInt())
            }
            background = panelBg
            visibility = View.GONE
        }

        // Panel Title Row
        val panelTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val panelTitle = TextView(this).apply {
            text = "Clicker Settings"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val panelCloseBtn = TextView(this).apply {
            text = "✕"
            setTextColor(0xFF8E8E93.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                settingsPanel.visibility = View.GONE
                try {
                    windowManager?.updateViewLayout(rootLayout, params)
                } catch (_: Exception) {}
            }
        }

        panelTitleRow.addView(panelTitle)
        panelTitleRow.addView(panelCloseBtn)
        settingsPanel.addView(panelTitleRow)

        // Mode Switcher: Click vs Scroll
        val modeLabel = TextView(this).apply {
            text = "Behavior on Press:"
            setTextColor(0xFF8E8E93.toInt())
            textSize = 11f
            setPadding(0, dp(8), 0, dp(4))
        }
        settingsPanel.addView(modeLabel)

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32))
        }

        val tapModeBtn = TextView(this).apply {
            text = "Tap / Click"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = dp(4)
            }
        }

        val scrollModeBtn = TextView(this).apply {
            text = "Scroll"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(4)
            }
        }

        fun updateModeButtons(mode: String) {
            val activeBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0xFF2563EB.toInt())
            }
            val inactiveBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0xFF2C2C2E.toInt())
            }

            if (mode == "scroll") {
                scrollModeBtn.background = activeBg
                scrollModeBtn.setTextColor(Color.WHITE)
                tapModeBtn.background = inactiveBg
                tapModeBtn.setTextColor(0xFF8E8E93.toInt())
            } else {
                tapModeBtn.background = activeBg
                tapModeBtn.setTextColor(Color.WHITE)
                scrollModeBtn.background = inactiveBg
                scrollModeBtn.setTextColor(0xFF8E8E93.toInt())
            }
        }
        updateModeButtons(prefs.floaterActionMode.value)

        tapModeBtn.setOnClickListener {
            prefs.setFloaterActionMode("click")
            updateModeButtons("click")
        }
        scrollModeBtn.setOnClickListener {
            prefs.setFloaterActionMode("scroll")
            updateModeButtons("scroll")
        }

        modeRow.addView(tapModeBtn)
        modeRow.addView(scrollModeBtn)
        settingsPanel.addView(modeRow)

        // Scroll Distance Section
        val distanceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val distanceTitle = TextView(this).apply {
            text = "Scroll Distance (Default: 550 dp):"
            setTextColor(0xFF8E8E93.toInt())
            textSize = 11f
        }

        val distanceControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val minusBtn = TextView(this).apply {
            text = "  -50  "
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(0xFF2C2C2E.toInt())
            }
            background = btnBg
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val distanceDisplay = TextView(this).apply {
            text = "${prefs.floaterScrollDistanceDp.value} dp"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val plusBtn = TextView(this).apply {
            text = "  +50  "
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(0xFF2C2C2E.toInt())
            }
            background = btnBg
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        minusBtn.setOnClickListener {
            val current = prefs.floaterScrollDistanceDp.value
            prefs.setFloaterScrollDistanceDp((current - 50).coerceAtLeast(200))
            distanceDisplay.text = "${prefs.floaterScrollDistanceDp.value} dp"
        }
        plusBtn.setOnClickListener {
            val current = prefs.floaterScrollDistanceDp.value
            prefs.setFloaterScrollDistanceDp((current + 50).coerceAtMost(1000))
            distanceDisplay.text = "${prefs.floaterScrollDistanceDp.value} dp"
        }

        distanceControls.addView(minusBtn)
        distanceControls.addView(distanceDisplay)
        distanceControls.addView(plusBtn)

        distanceContainer.addView(distanceTitle)
        distanceContainer.addView(distanceControls)
        settingsPanel.addView(distanceContainer)

        // Double Click = Double Tap Toggle
        val doubleTapRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }

        val doubleTapTitle = TextView(this).apply {
            text = "Double click = double tap"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val doubleTapSwitch = Switch(this).apply {
            isChecked = prefs.floaterDoubleTapLike.value
            setOnCheckedChangeListener { _, isChecked ->
                prefs.setFloaterDoubleTapLike(isChecked)
            }
        }
        doubleTapRow.addView(doubleTapTitle)
        doubleTapRow.addView(doubleTapSwitch)
        settingsPanel.addView(doubleTapRow)

        // Reset to Default Button
        val resetBtn = TextView(this).apply {
            text = "Reset Defaults"
            setTextColor(0xFFFF9F0A.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
            setOnClickListener {
                prefs.resetFloaterSettings()
                updateModeButtons("click")
                distanceDisplay.text = "550 dp"
                doubleTapSwitch.isChecked = false
                Toast.makeText(this@FloatingClickerService, "Reset clicker to defaults", Toast.LENGTH_SHORT).show()
                try {
                    windowManager?.updateViewLayout(rootLayout, params)
                } catch (_: Exception) {}
            }
        }
        settingsPanel.addView(resetBtn)

        // Hide Clicker Button inside settings popup
        val hideBtn = TextView(this).apply {
            text = "Hide Button"
            setTextColor(0xFFFF453A.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0x25FF453A.toInt())
                setStroke(dp(1), 0x55FF453A.toInt())
            }
            background = btnBg
            setPadding(0, dp(8), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            setOnClickListener {
                stopSelf()
            }
        }
        settingsPanel.addView(hideBtn)

        // ================= STYLIZED CONTROL PILL =================
        val controlPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
            ).apply {
                topMargin = dp(4)
            }
            setPadding(dp(12), 0, dp(10), 0)
            val pillBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(17).toFloat()
                setColor(0xEE121212.toInt())
                setStroke(dp(1), 0x55FFFFFF.toInt())
            }
            background = pillBg
        }

        // Stylized "iTag Click" Label (no green dot, no red cross)
        val coordLabel = TextView(this).apply {
            text = "iTag Click"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
        }

        // Settings Gear Button
        val settingsGearBtn = TextView(this).apply {
            text = "  ⚙"
            setTextColor(0xFF0A84FF.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(2), 0)
            setOnClickListener {
                val isCurrentlyVisible = settingsPanel.visibility == View.VISIBLE
                settingsPanel.visibility = if (isCurrentlyVisible) View.GONE else View.VISIBLE
                try {
                    windowManager?.updateViewLayout(rootLayout, params)
                } catch (_: Exception) {}
            }
        }

        controlPill.addView(coordLabel)
        controlPill.addView(settingsGearBtn)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(4))
        }

        rootLayout.addView(targetFrame)
        rootLayout.addView(spacer)
        rootLayout.addView(controlPill)
        rootLayout.addView(settingsPanel)

        val dragTouchListener = View.OnTouchListener { _, event ->
            if (isSimulatingGesture) return@OnTouchListener false
            val p = params ?: return@OnTouchListener false
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
                    if (isViewAttached) {
                        try {
                            windowManager?.updateViewLayout(rootLayout, p)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }

        targetFrame.setOnTouchListener(dragTouchListener)
        coordLabel.setOnTouchListener(dragTouchListener)

        floatingRootView = rootLayout
        try {
            windowManager?.addView(rootLayout, params)
            isViewAttached = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view: ${e.message}")
            stopSelf()
        }
    }

    private fun executeWithOverlayBypass(
        timeoutMs: Long = 450L,
        action: (targetX: Float, targetY: Float, onFinished: () -> Unit) -> Unit
    ) {
        if (isGestureInProgress) {
            Log.d(TAG, "Ignoring gesture request: another gesture is already in progress")
            return
        }

        if (!IFindAccessibilityService.isRunning()) {
            Toast.makeText(
                this@FloatingClickerService,
                "Please enable iFind in Accessibility settings",
                Toast.LENGTH_SHORT
            ).show()
            Log.w(TAG, "Cannot dispatch gesture: IFindAccessibilityService is not connected")
            return
        }

        isGestureInProgress = true
        isSimulatingGesture = true

        val p = params ?: return
        val density = resources.displayMetrics.density

        // 1. Calculate exact screen coordinates of the reticle center dot while view is still attached
        val location = IntArray(2)
        centerDotView?.getLocationOnScreen(location)
        val dotW = centerDotView?.width ?: 0
        val dotH = centerDotView?.height ?: 0

        val targetX = if (dotW > 0 && location[0] > 0) {
            location[0] + dotW / 2f
        } else {
            p.x + (44f * density)
        }

        val targetY = if (dotH > 0 && location[1] > 0) {
            location[1] + dotH / 2f
        } else {
            p.y + (44f * density)
        }

        // 2. Temporarily detach the overlay so the underlying target app is 100% unobstructed
        // and cannot be intercepted or blocked by Android's untrusted touch filter
        safeRemoveView()

        var isRestored = false
        fun restoreOverlay() {
            if (isRestored) return
            isRestored = true
            safeAddView()
            isSimulatingGesture = false
            isGestureInProgress = false
        }

        val safetyRunnable = Runnable {
            restoreOverlay()
        }
        mainHandler.postDelayed(safetyRunnable, timeoutMs)

        // 3. Dispatch gesture after brief 35ms delay to ensure WindowManager/InputDispatcher has flushed the window removal
        mainHandler.postDelayed({
            action(targetX, targetY) {
                mainHandler.removeCallbacks(safetyRunnable)
                mainHandler.post {
                    restoreOverlay()
                }
            }
        }, 35L)
    }

    private fun listenToTrackerButtonEvents() {
        val app = IFindApp.instance
        val bleManager = app.bleManager
        val prefs = app.preferencesManager

        buttonListenerJob = scope.launch {
            bleManager.buttonEvents.collect { event ->
                val density = resources.displayMetrics.density

                try {
                    when (event) {
                        is ButtonEvent.SinglePress -> {
                            val mode = prefs.floaterActionMode.value
                            vibratePhone()

                            if (mode == "scroll") {
                                val distanceDp = prefs.floaterScrollDistanceDp.value
                                val distancePx = distanceDp * density

                                executeWithOverlayBypass(timeoutMs = 500L) { _, _, onFinished ->
                                    val dispatched = IFindAccessibilityService.scrollVerticalAt(
                                        distancePx = distancePx,
                                        onComplete = {
                                            floatingRootView?.let { animateScrollFeedback(it) }
                                            onFinished()
                                        }
                                    )
                                    Log.d(TAG, "Floater scrollVerticalAt dist=$distancePx dispatched=$dispatched")
                                }
                            } else {
                                executeWithOverlayBypass(timeoutMs = 400L) { targetX, targetY, onFinished ->
                                    val dispatched = IFindAccessibilityService.clickAt(
                                        x = targetX,
                                        y = targetY,
                                        onComplete = {
                                            floatingRootView?.let { animateClickFeedback(it) }
                                            onFinished()
                                        }
                                    )
                                    Log.d(TAG, "Floater clickAt ($targetX, $targetY) dispatched=$dispatched")
                                }
                            }
                        }

                        is ButtonEvent.DoublePress -> {
                            if (prefs.floaterDoubleTapLike.value) {
                                Log.i(TAG, "Double press: executing Double-Tap Like")
                                vibratePhone()

                                executeWithOverlayBypass(timeoutMs = 600L) { targetX, targetY, onFinished ->
                                    val dispatched = IFindAccessibilityService.doubleClickAt(
                                        x = targetX,
                                        y = targetY,
                                        onComplete = {
                                            floatingRootView?.let { animateClickFeedback(it) }
                                            onFinished()
                                        }
                                    )
                                    Log.d(TAG, "Floater doubleClickAt ($targetX, $targetY) dispatched=$dispatched")
                                }
                            }
                        }

                        else -> Unit
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error handling floater gesture event: ${t.message}", t)
                }
            }
        }
    }

    private fun animateScrollFeedback(view: View) {
        view.animate()
            .translationY(-30f)
            .setDuration(120)
            .withEndAction {
                view.animate().translationY(0f).setDuration(120).start()
            }
            .start()
    }

    private fun animateClickFeedback(view: View) {
        view.animate()
            .scaleX(1.25f)
            .scaleY(1.25f)
            .setDuration(75)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(75)
                    .start()
            }
            .start()
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
        safeRemoveView()
        floatingRootView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        _isFloatingClickerActive.value = false
        super.onDestroy()
    }

    companion object {
        private val _isFloatingClickerActive = MutableStateFlow(false)
        val isFloatingClickerActive: StateFlow<Boolean> = _isFloatingClickerActive.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, FloatingClickerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to startForegroundService, fallback to startService", t)
                try {
                    context.startService(intent)
                } catch (_: Throwable) {}
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingClickerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
