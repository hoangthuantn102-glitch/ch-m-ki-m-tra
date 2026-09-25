import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Update signature of DirectCameraView
sig = """fun DirectCameraView(
    modifier: Modifier = Modifier,
    isAutoScanning: Boolean = false,
    isIdle: Boolean = true,
    captureTrigger: Int = 0,
    autoCaptureTrigger: Int = 0,
    autoCaptureWarning: String? = null,
    showBottomCaptureButton: Boolean = false,
"""
content = re.sub(
    r"fun DirectCameraView\(\n    modifier: Modifier = Modifier,\n    isAutoScanning: Boolean = false,\n    isIdle: Boolean = true,\n    captureTrigger: Int = 0,\n    showBottomCaptureButton: Boolean = false,",
    sig,
    content
)

# Update CameraXPreviewComponent usage inside DirectCameraView
usage = """        CameraXPreviewComponent(
            modifier = modifier,
            isAutoScanning = isAutoScanning,
            isIdle = isIdle,
            captureTrigger = captureTrigger,
            autoCaptureTrigger = autoCaptureTrigger,
            autoCaptureWarning = autoCaptureWarning,
            showBottomCaptureButton = showBottomCaptureButton,"""
content = re.sub(
    r"        CameraXPreviewComponent\(\n            modifier = modifier,\n            isAutoScanning = isAutoScanning,\n            isIdle = isIdle,\n            captureTrigger = captureTrigger,\n            showBottomCaptureButton = showBottomCaptureButton,",
    usage,
    content
)

# Update signature of CameraXPreviewComponent
sig2 = """fun CameraXPreviewComponent(
    modifier: Modifier = Modifier,
    isAutoScanning: Boolean = false,
    isIdle: Boolean = true,
    captureTrigger: Int = 0,
    autoCaptureTrigger: Int = 0,
    autoCaptureWarning: String? = null,
    showBottomCaptureButton: Boolean = false,"""
content = re.sub(
    r"fun CameraXPreviewComponent\(\n    modifier: Modifier = Modifier,\n    isAutoScanning: Boolean = false,\n    isIdle: Boolean = true,\n    captureTrigger: Int = 0,\n    showBottomCaptureButton: Boolean = false,",
    sig2,
    content
)

# Auto-capture logic
auto_logic = """    // Auto-capture logic driven by AI analysis (autoCaptureTrigger)
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
                            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
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
    }"""
content = re.sub(
    r"    // Auto-capture logic when in auto scanning mode and UI state is Idle\n    LaunchedEffect\(isAutoScanning, isIdle, imageCaptureUseCase, capturedCount\) \{.*?\n                    \}\n                \)\n            \}\n        \}\n    \}",
    auto_logic,
    content,
    flags=re.DOTALL
)

# Display autoCaptureWarning overlay
overlay = """            }
        }

        // Warning Overlay
        if (isAutoScanning && !autoCaptureWarning.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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

        // Take Photo / Capture Action Button"""
content = content.replace("            }\n        }\n\n        // Take Photo / Capture Action Button", overlay)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)
