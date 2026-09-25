@file:androidx.annotation.OptIn(
    androidx.camera.core.ExperimentalLensFacing::class,
    androidx.camera.camera2.interop.ExperimentalCamera2Interop::class
)
package com.example.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.media.ExifInterface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.delay

/**
 * Decodes and rotates bitmap according to EXIF metadata.
 */
fun decodeAndRotateBitmap(path: String): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(path) ?: return null
    try {
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postRotate(180f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }
        if (!matrix.isIdentity) {
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    } catch (e: Exception) {
        Log.e("CameraPreview", "Exif rotation failed", e)
    }
    return bitmap
}

/**
 * Checks if a CameraInfo corresponds to an external USB webcam connected via Type-C / OTG.
 */
fun isUsbWebcam(info: CameraInfo): Boolean {
    if (info.lensFacing == CameraSelector.LENS_FACING_EXTERNAL) return true
    return try {
        val c2Info = Camera2CameraInfo.from(info)
        val facing = c2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
        val id = c2Info.cameraId
        facing == CameraMetadata.LENS_FACING_EXTERNAL ||
                id.lowercase().contains("ext") ||
                id.lowercase().contains("usb")
    } catch (e: Throwable) {
        false
    }
}

/**
 * Scans connected USB devices for standard UVC (USB Video Class) cameras.
 */
fun getConnectedUsbCameraNames(context: Context): List<String> {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
    val names = mutableListOf<String>()
    try {
        usbManager.deviceList?.values?.forEach { dev ->
            val isVideo = (dev.deviceClass == UsbConstants.USB_CLASS_VIDEO) ||
                    (0 until dev.interfaceCount).any { dev.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }
            if (isVideo) {
                val name = dev.productName?.takeIf { it.isNotBlank() } ?: "Webcam USB Type-C"
                names.add(name)
            }
        }
    } catch (e: Exception) {
        Log.e("CameraPreview", "USB check error", e)
    }
    return names
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DirectCameraView(
    modifier: Modifier = Modifier,
    isAutoScanning: Boolean = false,
    isIdle: Boolean = true,
    captureTrigger: Int = 0,
    autoCaptureTrigger: Int = 0,
    autoCaptureWarning: String? = null,
    showBottomCaptureButton: Boolean = false,
    capturedCount: Int = 0,
    totalPages: Int = 1,
    onCameraFrame: ((Bitmap) -> Unit)? = null,
    onImageCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Yêu cầu quyền camera",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cần Quyền Truy Cập Camera",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ứng dụng cần quyền camera để quét và chấm điểm bài làm trực tiếp từ camera điện thoại hoặc webcam USB gắn ngoài Type-C.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cấp Quyền Camera", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        CameraXPreviewComponent(
            modifier = modifier,
            isAutoScanning = isAutoScanning,
            isIdle = isIdle,
            captureTrigger = captureTrigger,
            autoCaptureTrigger = autoCaptureTrigger,
            autoCaptureWarning = autoCaptureWarning,
            showBottomCaptureButton = showBottomCaptureButton,
            capturedCount = capturedCount,
            totalPages = totalPages,
            onCameraFrame = onCameraFrame,
            onImageCaptured = onImageCaptured,
            onClose = onClose
        )
    }
}

@Composable
fun CameraXPreviewComponent(
    modifier: Modifier = Modifier,
    isAutoScanning: Boolean = false,
    isIdle: Boolean = true,
    captureTrigger: Int = 0,
    autoCaptureTrigger: Int = 0,
    autoCaptureWarning: String? = null,
    showBottomCaptureButton: Boolean = false,
    capturedCount: Int = 0,
    totalPages: Int = 1,
    onCameraFrame: ((Bitmap) -> Unit)? = null,
    onImageCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = com.example.AlwaysActiveLifecycleOwner

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var availableCameras by remember { mutableStateOf<List<CameraInfo>>(emptyList()) }
    var selectedCameraIndex by remember { mutableStateOf(0) }

    var isCapturing by remember { mutableStateOf(false) }
    var isFlashEnabled by remember { mutableStateOf(false) }
    var isFitMode by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var usbNoticeMessage by remember { mutableStateOf<String?>(null) }

    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // Foreground service to keep camera stream active
    DisposableEffect(Unit) {
        val serviceIntent = Intent(context, com.example.CameraForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("CameraPreview", "Start foreground service failed", e)
        }
        onDispose {
            try {
                context.stopService(serviceIntent)
            } catch (e: Exception) {}
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Update scale type whenever fit mode changes
    LaunchedEffect(isFitMode) {
        previewView.scaleType = if (isFitMode) {
            PreviewView.ScaleType.FIT_CENTER
        } else {
            PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Refresh available cameras function
    val refreshCameras: (Boolean) -> Unit = remember(cameraProviderFuture, context) {
        { autoSelectUsb ->
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val cameras = cameraProvider.availableCameraInfos
                    availableCameras = cameras

                    if (cameras.isNotEmpty()) {
                        val usbIndex = cameras.indexOfFirst { isUsbWebcam(it) }
                        if (autoSelectUsb && usbIndex != -1) {
                            selectedCameraIndex = usbIndex
                            usbNoticeMessage = "Đã kết nối và chọn Webcam USB Type-C 🔌"
                        } else if (selectedCameraIndex >= cameras.size) {
                            val backIndex = cameras.indexOfFirst { it.lensFacing == CameraSelector.LENS_FACING_BACK }
                            selectedCameraIndex = if (backIndex != -1) backIndex else 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CameraXPreview", "Failed to refresh cameras", e)
                    bindError = "Lỗi phát hiện camera: ${e.localizedMessage}"
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    // Initial camera discovery
    LaunchedEffect(Unit) {
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val cameras = cameraProvider.availableCameraInfos
                availableCameras = cameras

                // Prioritize USB webcam if plugged in, otherwise default to BACK camera
                val usbIndex = cameras.indexOfFirst { isUsbWebcam(it) }
                val backIndex = cameras.indexOfFirst { it.lensFacing == CameraSelector.LENS_FACING_BACK }

                if (usbIndex != -1) {
                    selectedCameraIndex = usbIndex
                    usbNoticeMessage = "Đang sử dụng Webcam USB Type-C 🔌"
                } else if (backIndex != -1) {
                    selectedCameraIndex = backIndex
                } else if (cameras.isNotEmpty()) {
                    selectedCameraIndex = 0
                }
            } catch (e: Exception) {
                Log.e("CameraXPreview", "Failed to get cameras", e)
                bindError = "Không tìm thấy thiết bị camera hoặc webcam"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Listen to CameraManager availability callback (hotplug detection for USB cameras)
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
    DisposableEffect(cameraManager) {
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                refreshCameras(true)
            }
            override fun onCameraUnavailable(cameraId: String) {
                refreshCameras(false)
            }
        }
        try {
            cameraManager?.registerAvailabilityCallback(callback, null)
        } catch (e: Exception) {
            Log.e("CameraPreview", "AvailabilityCallback registration failed", e)
        }
        onDispose {
            try {
                cameraManager?.unregisterAvailabilityCallback(callback)
            } catch (e: Exception) {}
        }
    }

    // Listen for USB device attach/detach broadcasts
    DisposableEffect(context) {
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val usbNames = getConnectedUsbCameraNames(context)
                        val nameStr = if (usbNames.isNotEmpty()) usbNames.first() else "Webcam USB"
                        usbNoticeMessage = "Đã cắm $nameStr qua cổng Type-C..."
                        // Give external camera HAL a moment to enumerate the UVC device
                        Handler(Looper.getMainLooper()).postDelayed({
                            refreshCameras(true)
                        }, 900)
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        usbNoticeMessage = "Đã rút Webcam USB. Đang chuyển về camera máy..."
                        refreshCameras(false)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(usbReceiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {}
        }
    }

    // Auto-dismiss notice message after 4.5 seconds
    LaunchedEffect(usbNoticeMessage) {
        if (usbNoticeMessage != null) {
            delay(4500)
            usbNoticeMessage = null
        }
    }

    // Manual/remote capture trigger
    LaunchedEffect(captureTrigger) {
        if (captureTrigger > 0 && imageCaptureUseCase != null && !isCapturing) {
            val imageCapture = imageCaptureUseCase ?: return@LaunchedEffect
            val photoFile = File.createTempFile("exam_scan_remote_", ".jpg", context.cacheDir)
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            isCapturing = true
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        isCapturing = false
                        try {
                            val bitmap = decodeAndRotateBitmap(photoFile.absolutePath)
                            if (bitmap != null) {
                                onImageCaptured(bitmap)
                            }
                        } catch (e: Exception) {
                            Log.e("CameraXPreview", "Remote Trigger failed decoding", e)
                        } finally {
                            try { photoFile.delete() } catch (e: Exception) {}
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        isCapturing = false
                        Log.e("CameraXPreview", "Remote Trigger error", exception)
                    }
                }
            )
        }
    }

    // Auto-capture logic driven by AI analysis
    LaunchedEffect(autoCaptureTrigger) {
        if (autoCaptureTrigger > 0 && isAutoScanning && isIdle && !isCapturing) {
            val imageCapture = imageCaptureUseCase ?: return@LaunchedEffect
            val photoFile = File.createTempFile("exam_scan_auto_", ".jpg", context.cacheDir)
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            isCapturing = true
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        isCapturing = false
                        try {
                            val bitmap = decodeAndRotateBitmap(photoFile.absolutePath)
                            if (bitmap != null) {
                                onImageCaptured(bitmap)
                            }
                        } catch (e: Exception) {
                            Log.e("CameraXPreview", "Decoding bitmap failed", e)
                        } finally {
                            try { photoFile.delete() } catch (e: Exception) {}
                        }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        isCapturing = false
                        Log.e("CameraXPreview", "Capture failed", exception)
                    }
                }
            )
        }
    }

    // Bind camera preview and capture use cases
    LaunchedEffect(selectedCameraIndex, availableCameras, onCameraFrame) {
        if (availableCameras.isEmpty()) return@LaunchedEffect
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val cameraInfo = availableCameras.getOrNull(selectedCameraIndex) ?: availableCameras.first()
                val cameraSelector = cameraInfo.cameraSelector

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val imageAnalysis = if (onCameraFrame != null) {
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().apply {
                            var lastBroadcastTime = 0L
                            setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastBroadcastTime >= 50L) {
                                    lastBroadcastTime = currentTime
                                    try {
                                        val bitmap = imageProxy.toBitmap()
                                        val rotated = if (imageProxy.imageInfo.rotationDegrees != 0) {
                                            val matrix = Matrix().apply {
                                                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                                            }
                                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                        } else {
                                            bitmap
                                        }
                                        onCameraFrame(rotated)
                                    } catch (e: Exception) {
                                        Log.e("CameraXPreview", "Error converting frame to bitmap", e)
                                    }
                                }
                                imageProxy.close()
                            }
                        }
                } else null

                val camera = if (imageAnalysis != null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                }

                cameraControl = camera.cameraControl
                imageCaptureUseCase = imageCapture
                bindError = null

                // Apply flash only if not a USB webcam (USB webcams don't have phone flash)
                if (!isUsbWebcam(cameraInfo)) {
                    cameraControl?.enableTorch(isFlashEnabled)
                }
            } catch (e: Exception) {
                Log.e("CameraXPreview", "Failed to bind camera use cases", e)
                bindError = "Lỗi khởi tạo camera: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                Log.e("CameraXPreview", "Cleanup failed", e)
            }
        }
    }

    val currentCameraInfo = availableCameras.getOrNull(selectedCameraIndex)
    val isCurrentCameraUsb = currentCameraInfo?.let { isUsbWebcam(it) } ?: false

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(
                BorderStroke(
                    2.dp,
                    if (isCurrentCameraUsb) Color(0xFF00BFA5) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(16.dp)
            )
    ) {
        if (bindError != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Videocam, contentDescription = "Error", tint = Color.LightGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = bindError!!,
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { refreshCameras(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Thử Lại / Quét Camera")
                    }
                }
            }
        } else {
            // Live camera view
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Camera Selector Row with Chips
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .fillMaxWidth(0.88f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (availableCameras.isEmpty()) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Đang tìm camera & webcam...",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                availableCameras.forEachIndexed { index, info ->
                    val isSelected = index == selectedCameraIndex
                    val isUsb = isUsbWebcam(info)

                    val label = when {
                        isUsb -> "Webcam USB (Type-C) 🔌"
                        info.lensFacing == CameraSelector.LENS_FACING_BACK -> {
                            val backCount = availableCameras.count { it.lensFacing == CameraSelector.LENS_FACING_BACK }
                            if (backCount > 1) "Cam Sau #${index + 1}" else "Cam Sau"
                        }
                        info.lensFacing == CameraSelector.LENS_FACING_FRONT -> "Cam Trước"
                        else -> "Camera ${index + 1}"
                    }

                    val chipColor = when {
                        isSelected && isUsb -> Color(0xFF00796B)
                        isSelected -> MaterialTheme.colorScheme.primary
                        isUsb -> Color(0xFF004D40).copy(alpha = 0.8f)
                        else -> Color.Black.copy(alpha = 0.65f)
                    }

                    val contentColor = Color.White
                    val borderStroke = when {
                        isSelected && isUsb -> BorderStroke(1.5.dp, Color(0xFF80CBC4))
                        isSelected -> null
                        isUsb -> BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.7f))
                        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    }

                    Surface(
                        onClick = {
                            isFlashEnabled = false
                            selectedCameraIndex = index
                            if (isUsb) {
                                usbNoticeMessage = "Đang xem qua Webcam USB Type-C 🔌"
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = chipColor,
                        border = borderStroke,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("camera_chip_$index")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isUsb -> Icons.Default.Usb
                                    info.lensFacing == CameraSelector.LENS_FACING_BACK -> Icons.Default.Videocam
                                    else -> Icons.Default.Camera
                                },
                                contentDescription = null,
                                tint = if (isUsb) Color(0xFF80CBC4) else contentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = label,
                                color = contentColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Dedicated "Quét USB" refresh chip
                Surface(
                    onClick = {
                        val names = getConnectedUsbCameraNames(context)
                        refreshCameras(true)
                        if (names.isNotEmpty()) {
                            usbNoticeMessage = "Đang kết nối: ${names.joinToString()}"
                        } else {
                            usbNoticeMessage = "Đang quét cổng Type-C tìm Webcam USB..."
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color(0xFF80CBC4).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("refresh_usb_cameras_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Quét lại Webcam USB",
                            tint = Color(0xFF80CBC4),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Tìm USB",
                            color = Color(0xFF80CBC4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active USB Webcam badge indicator (if USB webcam is selected)
        if (isCurrentCameraUsb) {
            Surface(
                color = Color(0xDD004D40),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF00BFA5)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "USB Active",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "USB Type-C",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Control Panel overlay on the right side
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Flash Toggle button (only on phone cameras)
            if (!isCurrentCameraUsb) {
                IconButton(
                    onClick = {
                        isFlashEnabled = !isFlashEnabled
                        cameraControl?.enableTorch(isFlashEnabled)
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash toggle",
                        tint = if (isFlashEnabled) Color.Yellow else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Aspect ratio toggle (Fit Center vs Fill Center for document webcams)
            IconButton(
                onClick = { isFitMode = !isFitMode },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = if (isFitMode) "Đầy khung" else "Vừa khung",
                    tint = if (isFitMode) Color(0xFF00E676) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Camera switch button
            if (availableCameras.size > 1) {
                IconButton(
                    onClick = {
                        isFlashEnabled = false
                        selectedCameraIndex = (selectedCameraIndex + 1) % availableCameras.size
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quick USB Scan icon button
            IconButton(
                onClick = {
                    refreshCameras(true)
                    val names = getConnectedUsbCameraNames(context)
                    usbNoticeMessage = if (names.isNotEmpty()) {
                        "Đã tìm thấy: ${names.joinToString()}"
                    } else {
                        "Chưa thấy webcam USB Type-C. Hãy cắm cáp OTG và thử lại."
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = "Quét Webcam USB",
                    tint = Color(0xFF80CBC4),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // USB notice toast / banner at bottom
        AnimatedVisibility(
            visible = usbNoticeMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showBottomCaptureButton) 80.dp else 16.dp)
        ) {
            Surface(
                color = Color(0xEE212121),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF00BFA5)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = usbNoticeMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { usbNoticeMessage = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Warning Overlay
        if (isAutoScanning && !autoCaptureWarning.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = autoCaptureWarning,
                    color = if (autoCaptureWarning.contains("Tuyệt vời")) Color.Green else Color.Yellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Take Photo / Capture Action Button at bottom center (if enabled)
        if (showBottomCaptureButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = {
                        val imageCapture = imageCaptureUseCase ?: return@Button
                        val photoFile = File.createTempFile("exam_scan_", ".jpg", context.cacheDir)
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        isCapturing = true
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    isCapturing = false
                                    try {
                                        val bitmap = decodeAndRotateBitmap(photoFile.absolutePath)
                                        if (bitmap != null) {
                                            onImageCaptured(bitmap)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CameraXPreview", "Decoding bitmap failed", e)
                                    } finally {
                                        try { photoFile.delete() } catch (e: Exception) {}
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    Log.e("CameraXPreview", "Capture failed", exception)
                                }
                            }
                        )
                    },
                    enabled = !isCapturing && imageCaptureUseCase != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("direct_capture_trigger")
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Camera, contentDescription = "Chụp hình", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (totalPages > 1) "Chụp Trang ${capturedCount + 1}/$totalPages" else "Chụp Để Chấm Bài",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
