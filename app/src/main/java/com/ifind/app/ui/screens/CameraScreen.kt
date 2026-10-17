package com.ifind.app.ui.screens

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ifind.app.ble.BleManager
import com.ifind.app.ble.model.ButtonEvent
import com.ifind.app.data.PreferencesManager
import com.ifind.app.ui.theme.AccentAmber
import com.ifind.app.ui.theme.AccentCoral
import com.ifind.app.ui.theme.AccentEmerald
import com.ifind.app.ui.theme.BrandCyan
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "CameraScreen"

private fun applyWarmthToBitmap(src: Bitmap, warmthPercent: Int): Bitmap {
    if (warmthPercent == 0) return src
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val w = (warmthPercent / 100f).coerceIn(-0.4f, 0.5f)
    val rScale = 1.0f + (w * 0.16f)
    val gScale = 1.0f + (w * 0.04f)
    val bScale = 1.0f - (w * 0.16f)

    val colorMatrix = ColorMatrix(
        floatArrayOf(
            rScale, 0f, 0f, 0f, 0f,
            0f, gScale, 0f, 0f, 0f,
            0f, 0f, bScale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}

private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap): Uri? {
    val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "iFind_$name.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.ORIENTATION, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/iFind")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    return try {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }
        uri
    } catch (e: Exception) {
        Log.e(TAG, "Error saving bitmap to MediaStore: ${e.message}", e)
        null
    }
}

@Composable
fun CameraScreen(
    bleManager: BleManager,
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val connectionState by bleManager.connectionState.collectAsState()
    val countdownSetting by preferencesManager.cameraCountdownSeconds.collectAsState()

    // 1. Open app in selfie (front camera) by default
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var mirrorSelfie by remember { mutableStateOf(true) } // Mirror on for selfie by default
    var warmthPercent by remember { mutableIntStateOf(10) } // 10% warmth by default in selfie
    var showWarmthSlider by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) } // Default flash off
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var isShutterBlinkActive by remember { mutableStateOf(false) }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: Camera? by remember { mutableStateOf(null) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    // Tap to Focus and Exposure Slider states
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var minExposure by remember { mutableIntStateOf(-4) }
    var maxExposure by remember { mutableIntStateOf(4) }
    var focusTimeoutJob by remember { mutableStateOf<Job?>(null) }

    // Last Photo thumbnail state
    var latestPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var latestPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFullscreenPhoto by remember { mutableStateOf(false) }

    val isConnected = connectionState.isConnected

    // Helper to query the most recently saved photo from Gallery/MediaStore
    fun loadLatestPhoto() {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val bitmap = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, contentUri)
                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                val maxDim = 1920
                                if (info.size.width > maxDim || info.size.height > maxDim) {
                                    val scale = Math.max(info.size.width, info.size.height).toFloat() / maxDim
                                    decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                                }
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, contentUri)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode full bitmap: ${e.message}")
                        null
                    }
                    latestPhotoUri = contentUri
                    if (bitmap != null) {
                        latestPhotoBitmap = bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading latest photo: ${e.message}")
        }
    }

    LaunchedEffect(Unit) {
        loadLatestPhoto()
    }

    // Shutter Trigger Execution with physical upright & mirror transform + warmth grading
    fun takePhoto() {
        val capture = imageCapture ?: return
        if (isCapturing) return

        isCapturing = true

        // Brief, gentle 70ms shutter dip (NO solid white screen flash when flash is off!)
        isShutterBlinkActive = true
        coroutineScope.launch {
            delay(70)
            isShutterBlinkActive = false
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val rawBitmap = imageProxy.toBitmap()
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                            // 1. Physically transform pixels to be upright and mirrored if requested
                            val matrix = Matrix()
                            if (rotationDegrees != 0) {
                                matrix.postRotate(rotationDegrees.toFloat())
                            }
                            if (lensFacing == CameraSelector.LENS_FACING_FRONT && mirrorSelfie) {
                                matrix.postScale(-1f, 1f)
                            }

                            val orientedBitmap = Bitmap.createBitmap(
                                rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                            )

                            // 2. Apply warmth grading if configured
                            val processedBitmap = if (warmthPercent != 0) {
                                applyWarmthToBitmap(orientedBitmap, warmthPercent)
                            } else {
                                orientedBitmap
                            }

                            // 3. Save directly to MediaStore
                            val savedUri = saveBitmapToMediaStore(context, processedBitmap)

                            withContext(Dispatchers.Main) {
                                isCapturing = false
                                if (savedUri != null) {
                                    latestPhotoUri = savedUri
                                    latestPhotoBitmap = processedBitmap
                                    Toast.makeText(context, "Saved selfie to Gallery!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error processing captured photo: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                isCapturing = false
                                Toast.makeText(context, "Photo processing error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Trigger photo with configured countdown
    LaunchedEffect(countdownRemaining) {
        if (countdownRemaining > 0) {
            delay(1000)
            val next = countdownRemaining - 1
            countdownRemaining = next
            if (next == 0) {
                takePhoto()
            }
        }
    }

    fun onShutterTriggered() {
        if (countdownRemaining > 0 || isCapturing) return
        if (countdownSetting > 0) {
            countdownRemaining = countdownSetting
        } else {
            takePhoto()
        }
    }

    // Collect BLE button events as Remote Shutter
    LaunchedEffect(Unit) {
        bleManager.buttonEvents.collect { event ->
            when (event) {
                is ButtonEvent.SinglePress -> {
                    onShutterTriggered()
                }
                else -> Unit
            }
        }
    }

    // Rebind CameraX preview and capture with exact 16:9 aspect ratio and maximum quality
    LaunchedEffect(lensFacing, cameraProvider, previewView) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect

        try {
            // Target 16:9 vertical preview and capture so preview and photo match 1:1
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }

            val capture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build()
            imageCapture = capture

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
            cameraControl = camera

            val expState = camera.cameraInfo.exposureState
            minExposure = expState.exposureCompensationRange.lower
            maxExposure = expState.exposureCompensationRange.upper
            exposureIndex = expState.exposureCompensationIndex
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to initialize camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Live Camera Preview Viewfinder with tap-to-focus and drag-to-adjust exposure
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(previewView, cameraControl) {
                    detectTapGestures { offset ->
                        val pv = previewView ?: return@detectTapGestures
                        val camera = cameraControl ?: return@detectTapGestures

                        focusPoint = offset
                        showFocusRing = true
                        focusTimeoutJob?.cancel()
                        focusTimeoutJob = coroutineScope.launch {
                            delay(3500)
                            showFocusRing = false
                        }

                        try {
                            val factory = pv.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            camera.cameraControl.startFocusAndMetering(action)
                        } catch (e: Exception) {
                            Log.e(TAG, "Focus error: ${e.message}")
                        }
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val view = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    previewView = view

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            cameraProvider = cameraProviderFuture.get()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    view
                },
                update = {
                    imageCapture?.flashMode = flashMode
                }
            )

            // Real-time Warmth Tint Overlay on live viewfinder
            if (warmthPercent != 0) {
                val tintColor = if (warmthPercent > 0) Color(0xFFFFA000) else Color(0xFF2196F3)
                val tintAlpha = (Math.abs(warmthPercent) / 100f * 0.16f).coerceIn(0f, 0.22f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tintColor.copy(alpha = tintAlpha))
                )
            }

            // 2. Google Pixel Style Tap-to-Focus Ring and Exposure Slider
            if (showFocusRing && focusPoint != null) {
                val pt = focusPoint!!
                val ringSize = 64.dp

                // Focus Ring with subtle animation
                Box(
                    modifier = Modifier
                        .offset { IntOffset((pt.x - 32.dp.toPx()).toInt(), (pt.y - 32.dp.toPx()).toInt()) }
                        .size(ringSize)
                        .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    )
                }

                // Vertical Exposure Slider Track with Sun icon (Slide up to brighten, slide down to darken)
                Box(
                    modifier = Modifier
                        .offset { IntOffset((pt.x + 40.dp.toPx()).toInt(), (pt.y - 60.dp.toPx()).toInt()) }
                        .height(120.dp)
                        .width(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(cameraControl, exposureIndex) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val camera = cameraControl ?: return@detectDragGestures
                                val range = (maxExposure - minExposure).coerceAtLeast(1)
                                val step = (-dragAmount.y / 20f).toInt()
                                if (step != 0) {
                                    val newExp = (exposureIndex + step).coerceIn(minExposure, maxExposure)
                                    if (newExp != exposureIndex) {
                                        exposureIndex = newExp
                                        try {
                                            camera.cameraControl.setExposureCompensationIndex(newExp)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WbSunny,
                            contentDescription = "Exposure",
                            tint = AccentAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        val expText = if (exposureIndex > 0) "+$exposureIndex" else "$exposureIndex"
                        Text(
                            text = expText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Shutter Curtain Blink (Brief, clean 70ms black fade; NO solid white screen!)
        AnimatedVisibility(
            visible = isShutterBlinkActive,
            enter = fadeIn(tween(30)),
            exit = fadeOut(tween(50)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
            )
        }

        // 4. Live Countdown Overlay
        if (countdownRemaining > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(130.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$countdownRemaining",
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandCyan
                        )
                    }
                }
            }
        }

        // 5. Minimal Google Pixel Style Top Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 38.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Tracker Status Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isConnected) AccentEmerald.copy(alpha = 0.8f) else AccentCoral.copy(alpha = 0.8f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (isConnected) AccentEmerald else AccentCoral,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) "iTAG SHUTTER READY" else "iTAG DISCONNECTED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Controls Group: Warmth, Mirror, Flash, Timer
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Warmth Adjustment Button
                    IconButton(
                        onClick = {
                            showWarmthSlider = !showWarmthSlider
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showWarmthSlider || warmthPercent != 0) AccentAmber.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WbSunny,
                            contentDescription = "Warmth",
                            tint = if (warmthPercent != 0) AccentAmber else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Mirror Selfie Toggle (only on front camera)
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        IconButton(
                            onClick = {
                                mirrorSelfie = !mirrorSelfie
                                Toast.makeText(
                                    context,
                                    if (mirrorSelfie) "Selfie Mirror: ON" else "Selfie Mirror: OFF (Natural)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (mirrorSelfie) BrandCyan.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Flip,
                                contentDescription = "Mirror Selfie",
                                tint = if (mirrorSelfie) BrandCyan else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Flash Toggle
                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                                else -> Icons.Rounded.FlashOff
                            },
                            contentDescription = "Flash",
                            tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) AccentAmber else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Countdown Timer Toggle
                    IconButton(
                        onClick = {
                            val nextSec = when (countdownSetting) {
                                0 -> 3
                                3 -> 5
                                else -> 0
                            }
                            preferencesManager.setCameraCountdown(nextSec)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        if (countdownSetting > 0) {
                            Text(
                                text = "${countdownSetting}s",
                                color = BrandCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = "Timer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Warmth Slider Bar Popup
            AnimatedVisibility(
                visible = showWarmthSlider,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Cool",
                            color = BrandCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = warmthPercent.toFloat(),
                            onValueChange = { warmthPercent = it.toInt() },
                            valueRange = -30f..50f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentAmber,
                                activeTrackColor = AccentAmber,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        val warmLabel = if (warmthPercent > 0) "+$warmthPercent%" else "$warmthPercent%"
                        Text(
                            "Warm ($warmLabel)",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 6. Google Pixel Style Bottom Camera Dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp, start = 28.dp, end = 28.dp)
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Gallery Thumbnail / Viewer Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .clickable {
                            if (latestPhotoUri != null) {
                                showFullscreenPhoto = true
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        type = "image/*"
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No photos found yet", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (latestPhotoBitmap != null) {
                        Image(
                            bitmap = latestPhotoBitmap!!.asImageBitmap(),
                            contentDescription = "Latest Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Google Pixel Style Shutter Button
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .border(3.5.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !isCapturing && countdownRemaining == 0) {
                            onShutterTriggered()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.Black,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                // Lens Switcher (Front ↔ Rear)
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            mirrorSelfie = true
                            warmthPercent = 10
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            warmthPercent = 0
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Fullscreen In-App Photo Inspector Dialog
        if (showFullscreenPhoto && latestPhotoUri != null) {
            Dialog(
                onDismissRequest = { showFullscreenPhoto = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (latestPhotoBitmap != null) {
                        Image(
                            bitmap = latestPhotoBitmap!!.asImageBitmap(),
                            contentDescription = "Fullscreen Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top Bar for Photo Viewer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showFullscreenPhoto = false },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(latestPhotoUri, "image/*")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open with"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open in Gallery", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
