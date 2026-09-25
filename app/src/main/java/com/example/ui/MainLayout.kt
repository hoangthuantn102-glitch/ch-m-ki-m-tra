package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.AnswerKeyParser
import com.example.data.ExamConfig
import com.example.data.GradingResult
import com.example.data.StudentAnswerDetail
import com.example.data.TestType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

enum class AppTab(val label: String, val icon: ImageVector) {
    GRADING("Chấm Bài", Icons.Default.CameraAlt),
    CONFIG("Đáp Án", Icons.Default.Settings),
    HISTORY("Lịch Sử", Icons.Default.History),
    PC_CONNECT("Kết Nối PC", Icons.Default.Computer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(viewModel: GradingViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.GRADING) }
    
    val config by viewModel.configState.collectAsStateWithLifecycle()
    val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val serverIp by viewModel.serverIpAddress.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chấm Trắc Nghiệm AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isServerRunning && serverIp != null) {
                            Text(
                                text = "PC kết nối: $serverIp",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Offline - Chưa mở PC remote",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                actions = {
                    if (isServerRunning) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
   
                                Text(
                                    text = "IP: 8080",
                                    fontSize = 10.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.GRADING -> GradingTabScreen(viewModel, config)
                AppTab.CONFIG -> ConfigTabScreen(viewModel, config)
                AppTab.HISTORY -> HistoryTabScreen(viewModel)
                AppTab.PC_CONNECT -> PcConnectTabScreen(viewModel, isServerRunning, serverIp)
            }
        }
    }
}

// ==========================================
// TAB 1: GRADING TAB (CHẤM BÀI)
// ==========================================

@Composable
fun GradingTabScreen(viewModel: GradingViewModel, config: ExamConfig) {
    val context = LocalContext.current
    val gradingUiState by viewModel.gradingUiState.collectAsStateWithLifecycle()
    val autoTransitionMode by viewModel.autoTransitionMode.collectAsStateWithLifecycle()
    val isAutoScanning by viewModel.isAutoScanning.collectAsStateWithLifecycle()
    val autoCaptureTrigger by viewModel.autoCaptureTrigger.collectAsStateWithLifecycle()
    val autoCaptureWarning by viewModel.autoCaptureWarning.collectAsStateWithLifecycle()
    
    val capturedBitmaps = remember { androidx.compose.runtime.mutableStateListOf<Bitmap>() }
    var showLiveCamera by remember { mutableStateOf(true) }
    var isFullCameraScanningMode by remember { mutableStateOf(false) }

    var localCaptureTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(viewModel.remoteActionFlow) {
        viewModel.remoteActionFlow.collect { action ->
            if (action == "capture") {
                localCaptureTrigger++
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = decodeUriToBitmap(context, uri)
            if (bitmap != null) {
                capturedBitmaps.add(bitmap)
                if (capturedBitmaps.size >= config.totalPages) {
                    showLiveCamera = false
                    viewModel.gradePhotos(capturedBitmaps.toList())
                }
            }
        }
    }

    // Auto Transition countdown handling
    if (gradingUiState is GradingUiState.Success && autoTransitionMode) {
        var countdown by remember(gradingUiState) { mutableStateOf(4) }
        LaunchedEffect(gradingUiState) {
            countdown = 4
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            // Auto transition trigger: reset state, clear bitmap, open camera
            viewModel.resetGradingUiState()
            capturedBitmaps.clear()
            showLiveCamera = true
        }

        // Display countdown banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { countdown / 4f },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Tự động chuyển bài thi tiếp theo trong $countdown giây...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                TextButton(
                    onClick = {
                        viewModel.resetGradingUiState()
                        capturedBitmaps.clear()
                        showLiveCamera = true
                    }
                ) {
                    Text("Chuyển Ngay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 800

    @Composable
    fun ExamConfigCard() {
        val examConfigs by viewModel.examConfigs.collectAsStateWithLifecycle()
        var showConfigDropdown by remember { mutableStateOf(false) }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Lớp học & Bài kiểm tra hiện tại",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { showConfigDropdown = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${config.examName} - Lớp: ${config.className}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tổng số: ${config.totalQuestions} câu | ${config.pointsPerCorrect} điểm/câu",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Chọn bài kiểm tra",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showConfigDropdown,
                        onDismissRequest = { showConfigDropdown = false },
                        modifier = Modifier.fillMaxWidth(if (isWideScreen) 0.3f else 0.9f)
                    ) {
                        if (examConfigs.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Chưa có danh sách đề thi nào") },
                                onClick = { showConfigDropdown = false }
                            )
                        } else {
                            examConfigs.forEach { cfg ->
                                val isSelected = cfg.id == config.id
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${cfg.examName} - Lớp ${cfg.className}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${cfg.totalQuestions} câu | ${cfg.pointsPerCorrect} điểm/câu | ${if (cfg.testType == TestType.ANSWER_TABLE) "Bảng ô" else "Khoanh trên đề"}",
                                                    fontSize = 11.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectConfig(cfg)
                                        showConfigDropdown = false
                                    },
                                    modifier = Modifier.background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PaperStyleCard() {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Hình Thức Làm Bài",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Chọn cách học sinh khoanh đáp án trên giấy thi để AI nhận diện chuẩn nhất",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCircled = config.testType == TestType.CIRCLED_ON_SHEET
                    val isTable = config.testType == TestType.ANSWER_TABLE
                    
                    ElevatedButton(
                        onClick = { viewModel.setTestType(TestType.CIRCLED_ON_SHEET) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (isCircled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isCircled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("⭕ Đề Thi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Khoanh trên đề", fontSize = 9.sp)
                        }
                    }

                    ElevatedButton(
                        onClick = { viewModel.setTestType(TestType.ANSWER_TABLE) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (isTable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isTable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("📊 Bảng Đáp Án", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Điền vào bảng ô", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TransitionModeCard() {
        val requireStudentName by viewModel.requireStudentName.collectAsStateWithLifecycle()

        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Cấu Hình Quét & Chuyển Bài Thi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tùy chỉnh chế độ chụp tự động, báo kết quả và nhập tên học sinh",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(14.dp))
                
                // 1. Quét tự động khi rõ nét
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "📸 Tự chụp khi hình ảnh rõ nét",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Hệ thống tự động chụp và báo kết quả ngay khi thấy bài thi rõ nét",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = isAutoScanning,
                        onCheckedChange = { viewModel.setAutoScanning(it) }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 10.dp))

                // 2. Chuyển bài tự động
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "⚡ Tự động chuyển bài sau khi chấm",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Tự động làm sạch và mở lại camera để chấm bài tiếp theo",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = autoTransitionMode,
                        onCheckedChange = { viewModel.setAutoTransitionMode(it) }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 10.dp))

                // 3. Yêu cầu nhập tên HS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "✍️ Yêu cầu nhập tên học sinh",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Yêu cầu nhập tên nếu AI không tự đọc được tên từ bài thi (Tắt nếu muốn lưu ẩn danh)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = requireStudentName,
                        onCheckedChange = { viewModel.setRequireStudentName(it) }
                    )
                }
            }
        }
    }

    @Composable
    fun CameraOrSnapshotSection(heightDp: Int = 320) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nhận Diện Bài Thi Học Sinh",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAutoScanning) "AI đang quét và chấm tự động (Hỗ trợ Webcam USB Type-C 🔌)" else "Nhấp chụp ảnh để bắt đầu chấm (Hỗ trợ Webcam USB Type-C 🔌)",
                            fontSize = 11.sp,
                            color = if (isAutoScanning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                            fontWeight = if (isAutoScanning) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    
                    // Auto Scanning Switch (AI Tự Động Quét)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Quét Tự Động", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = isAutoScanning,
                            onCheckedChange = { viewModel.setAutoScanning(it) },
                            thumbContent = {
                                if (isAutoScanning) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (!showLiveCamera && capturedBitmaps.isNotEmpty()) {
                    // Display captured static preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heightDp.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = capturedBitmaps.lastOrNull(),
                            contentDescription = "Scanned Page Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = {
                                capturedBitmaps.clear()
                                showLiveCamera = true
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = Color.White)
                        }
                    }
                } else if (showLiveCamera) {
                    // Display the direct live CameraX preview with dynamic expanded frame
                    DirectCameraView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isAutoScanning) (heightDp + 80).dp else heightDp.dp),
                        isAutoScanning = isAutoScanning,
                        isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.size < config.totalPages),
                        captureTrigger = localCaptureTrigger,
                        autoCaptureTrigger = autoCaptureTrigger,
                        autoCaptureWarning = autoCaptureWarning,
                        capturedCount = capturedBitmaps.size,
                        totalPages = config.totalPages,
                        onCameraFrame = { viewModel.broadcastCameraFrame(it) },
                        onImageCaptured = { bitmap ->
                            capturedBitmaps.add(bitmap)
                            if (capturedBitmaps.size >= config.totalPages) {
                                showLiveCamera = false
                                viewModel.clearCameraFrame()
                                viewModel.gradePhotos(capturedBitmaps.toList())
                            }
                        },
                        onClose = {
                            showLiveCamera = false
                            viewModel.clearCameraFrame()
                        }
                    )
                } else {
                    // Placeholder Box when camera is closed and no image is loaded
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable { showLiveCamera = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Upload icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Mở camera trực tiếp hoặc chọn từ thư viện", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!showLiveCamera && capturedBitmaps.isEmpty()) {
                        Button(
                            onClick = { showLiveCamera = true },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp)
                                .testTag("camera_open_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = "Camera")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bật Camera", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else if (!showLiveCamera && capturedBitmaps.isNotEmpty()) {
                        Button(
                            onClick = {
                                capturedBitmaps.clear()
                                showLiveCamera = true
                                viewModel.resetGradingUiState()
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp)
                                .testTag("camera_retake_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retake")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chụp Bài Khác", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("gallery_picker_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thư Viện", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    @Composable
    fun ResultSection() {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            when (val state = gradingUiState) {
                is GradingUiState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "BẮT ĐẦU CHẤM ĐIỂM",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nhấp chụp ảnh bài thi từ camera hoặc chọn file ảnh từ thư viện thiết bị. Hệ thống AI sẽ tự động phân tích và trả kết quả chấm ngay lập tức.",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                is GradingUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AI Đang Phân Tích Bài Thi...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Mô hình Gemini đang đọc chữ viết/ô khoanh tròn và so sánh đáp án. Quá trình này diễn ra khoảng 10 giây.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is GradingUiState.Success -> {
                    ResultSummaryCard(
                        result = state.result,
                        autoTransitionMode = autoTransitionMode,
                        onReset = {
                            viewModel.resetGradingUiState()
                            capturedBitmaps.clear()
                            showLiveCamera = true
                        }
                    )
                }
                is GradingUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lỗi Chấm Bài AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (capturedBitmaps.isNotEmpty()) viewModel.gradePhotos(capturedBitmaps.toList())
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Thử lại", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thử Lại", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.resetGradingUiState()
                                    capturedBitmaps.clear()
                                    showLiveCamera = true
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Bỏ Qua", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun StartGradingCard() {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Xác Nhận & Bắt Đầu Chấm Bài",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bài kiểm tra: ${config.examName} | Lớp: ${config.className}\nTổng số: ${config.totalQuestions} câu | ${if (config.testType == TestType.ANSWER_TABLE) "Bảng ô đáp án" else "Khoanh trực tiếp trên đề"} | ${config.totalPages} Trang",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Số trang đề thi:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { if (config.totalPages > 1) viewModel.setTotalPages(config.totalPages - 1) },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                                .size(30.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Giảm số trang", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${config.totalPages} Trang",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(
                            onClick = { if (config.totalPages < 5) viewModel.setTotalPages(config.totalPages + 1) },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                                .size(30.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tăng số trang", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isFullCameraScanningMode = true
                        showLiveCamera = true
                        capturedBitmaps.clear()
                        viewModel.resetGradingUiState()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_grading_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BẮT ĐẦU CHẤM BÀI", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }
    }

    if (isFullCameraScanningMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Camera or Scanned Photo View
            if (!showLiveCamera && capturedBitmaps.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = capturedBitmaps.lastOrNull(),
                        contentDescription = "Scanned Paper",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                DirectCameraView(
                    modifier = Modifier.fillMaxSize(),
                    isAutoScanning = isAutoScanning,
                    isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.size < config.totalPages),
                    captureTrigger = localCaptureTrigger,
                        autoCaptureTrigger = autoCaptureTrigger,
                        autoCaptureWarning = autoCaptureWarning,
                    capturedCount = capturedBitmaps.size,
                    totalPages = config.totalPages,
                    onCameraFrame = { viewModel.broadcastCameraFrame(it) },
                    onImageCaptured = { bitmap ->
                        capturedBitmaps.add(bitmap)
                        if (capturedBitmaps.size >= config.totalPages) {
                            showLiveCamera = false
                            viewModel.clearCameraFrame()
                            viewModel.gradePhotos(capturedBitmaps.toList())
                        }
                    },
                    onClose = {
                        isFullCameraScanningMode = false
                        viewModel.clearCameraFrame()
                    }
                )
            }

            // Top Control Bar Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { isFullCameraScanningMode = false },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Thoát", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${config.examName} - Lớp ${config.className}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${config.totalQuestions} câu | ${if (config.testType == TestType.ANSWER_TABLE) "Bảng ô" else "Khoanh đề"}",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isAutoScanning) "Tự Động" else "Thủ Công",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAutoScanning) Color(0xFF81C784) else Color.White
                    )
                    Switch(
                        checked = isAutoScanning,
                        onCheckedChange = { viewModel.setAutoScanning(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            // Bottom Control and Result Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (val state = gradingUiState) {
                    is GradingUiState.Loading -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Column {
                                    Text("AI Đang Phân Tích & Chấm Bài Thi...", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                    Text("Vui lòng giữ cố định camera...", fontSize = 11.sp, color = Color.LightGray)
                                }
                            }
                        }
                    }
                    is GradingUiState.Success -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResultSummaryCard(
                                result = state.result,
                                autoTransitionMode = autoTransitionMode,
                                onReset = {
                                    viewModel.resetGradingUiState()
                                    capturedBitmaps.clear()
                                    showLiveCamera = true
                                }
                            )
                            Button(
                                onClick = {
                                    viewModel.resetGradingUiState()
                                    capturedBitmaps.clear()
                                    showLiveCamera = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Camera, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CHỤP BÀI TIẾP THEO", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is GradingUiState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Lỗi Chấm Bài", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(state.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { if (capturedBitmaps.isNotEmpty()) viewModel.gradePhotos(capturedBitmaps.toList()) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Thử Lại")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.resetGradingUiState()
                                            capturedBitmaps.clear()
                                            showLiveCamera = true
                                        }
                                    ) {
                                        Text("Bỏ Qua")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(30.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Thư viện", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thư Viện", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { localCaptureTrigger++ },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Camera, contentDescription = "Chụp", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (config.totalPages > 1) "Chụp Trang ${capturedBitmaps.size + 1}/${config.totalPages}" else "Chụp Bài", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { isFullCameraScanningMode = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Thoát", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thoát", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    } else {
        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left column (Config card, paper style selection, transition settings)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExamConfigCard()
                    PaperStyleCard()
                    TransitionModeCard()
                }

                // Right column (Start Grading button & Recent Result Section)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StartGradingCard()
                    ResultSection()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { ExamConfigCard() }
                item { PaperStyleCard() }
                item { TransitionModeCard() }
                item { StartGradingCard() }
                item { ResultSection() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultSummaryCard(
    result: GradingResult,
    autoTransitionMode: Boolean,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "KẾT QUẢ CHẤM ĐIỂM CHI TIẾT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = result.studentName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onReset,
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Quét bài khác", tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // BẢNG THỐNG KÊ (Tổng câu, Đúng, Sai, Điểm)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tổng Số Câu", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${result.totalQuestions}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Divider(modifier = Modifier.height(30.dp).width(1.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Số Câu Đúng", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${result.correctCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Divider(modifier = Modifier.height(30.dp).width(1.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val incorrectCount = result.gradedCount - result.correctCount
                    Text("Số Câu Sai", fontSize = 10.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("$incorrectCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                }
                Divider(modifier = Modifier.height(30.dp).width(1.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tổng Điểm", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f", result.score),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "CHI TIẾT ĐÁP ÁN TỪNG CÂU:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // BẢNG CHI TIẾT TỪNG CÂU
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            result.answers.forEach { ans ->
                val isCorrect = ans.isCorrect
                val isGraded = ans.isGraded
                
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            !isGraded -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            isCorrect -> Color(0xFFE8F5E9) // Light green background
                            else -> Color(0xFFFFEBEE) // Light red background
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = when {
                            !isGraded -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            isCorrect -> Color(0xFF81C784)
                            else -> Color(0xFFE57373)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Câu ${ans.questionNumber}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!isGraded) {
                                Text(
                                    text = "Bỏ qua",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else if (isCorrect) {
                                // Correct: student answer is drawn green
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2E7D32), CircleShape)
                                        .size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ans.studentAnswer,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else {
                                // Incorrect: student answer B (red) and correct C (yellow) next to it
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFC62828), CircleShape)
                                        .size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val displayAns = when {
                                        ans.studentAnswer == "Trống" || ans.studentAnswer.isEmpty() || ans.studentAnswer == "-" -> "-"
                                        ans.studentAnswer == "Nhiều đáp án" || ans.studentAnswer.contains("NHIỀU") || ans.studentAnswer.equals("X", ignoreCase = true) -> "X"
                                        else -> ans.studentAnswer
                                    }
                                    Text(
                                        text = displayAns,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("➔", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFFF59D), CircleShape) // Light Yellow
                                        .border(BorderStroke(1.dp, Color(0xFFFBC02D)), CircleShape)
                                        .size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ans.correctAnswer,
                                        color = Color(0xFF5D4037), // Dark brown for extreme visibility
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!autoTransitionMode) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("next_grading_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.NavigateNext, contentDescription = "Chấm bài tiếp theo", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CHẤM BÀI TIẾP THEO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// TAB 2: CONFIGURATION TAB (ĐÁP ÁN)
// ==========================================

@Composable
fun ConfigTabScreen(viewModel: GradingViewModel, config: ExamConfig) {
    val context = LocalContext.current
    var csvPasteText by remember { mutableStateOf("") }
    var isCsvSectionExpanded by remember { mutableStateOf(false) }
    var isKeyLocked by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val examConfigs by viewModel.examConfigs.collectAsStateWithLifecycle()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📚 Quản Lý Đáp Án Các Lớp & Bài",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Chọn, thêm mới hoặc xóa các bộ đáp án lớp học",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.createNewConfig("Bài Kiểm Tra Mới", "Lớp Mới", config.testType, config.totalQuestions)
                                Toast.makeText(context, "Đã tạo lớp và đề mới!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Thêm mới", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Thêm Mới", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (examConfigs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            examConfigs.forEach { cfg ->
                                val isSelected = cfg.id == config.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectConfig(cfg) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, contentDescription = "Đang chọn", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                }
                                                Text(
                                                    text = "${cfg.examName} - Lớp: ${cfg.className}",
                                                    fontWeight = FontWeight.Bold,
                   
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "${cfg.totalQuestions} câu | ${cfg.pointsPerCorrect} điểm/câu",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteConfig(cfg.id)
                                                    Toast.makeText(context, "Đã xóa bộ đáp án!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Thông Tin Lớp & Đề Thi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    var examNameText by remember(config.examName) { mutableStateOf(config.examName) }
                    var classNameText by remember(config.className) { mutableStateOf(config.className) }

                    OutlinedTextField(
                        value = examNameText,
                        onValueChange = {
                            examNameText = it
                            viewModel.updateExamMeta(it, classNameText)
                        },
                        label = { Text("Tên Bài Kiểm Tra") },
                        placeholder = { Text("Ví dụ: Giữa Kỳ 1, Kiểm Tra 15 Phút...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = classNameText,
                        onValueChange = {
                            classNameText = it
                            viewModel.updateExamMeta(examNameText, it)
                        },
                        label = { Text("Tên Lớp Học") },
                        placeholder = { Text("Ví dụ: 12A1, 10B2...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveConfigToDatabase(config)
                                Toast.makeText(context, "Đã lưu đáp án thành công cho ${config.className} - ${config.examName}!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Lưu", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lưu Đáp Án", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.deleteConfig(config.id)
                                Toast.makeText(context, "Đã xóa bài kiểm tra này!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Xóa Bài Này", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Basic Settings Card (total questions & points)
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Thiết Lập Đề Thi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Row: Total Pages Count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Số Trang Bài Trắc Nghiệm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Chụp đủ số trang quy định mới thực hiện chấm", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { if (config.totalPages > 1) viewModel.setTotalPages(config.totalPages - 1) },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Giảm số trang", modifier = Modifier.size(18.dp))
                            }
                            
                            Text(
                                text = "${config.totalPages} Trang",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )


                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row: Total Questions Count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Tổng Số Câu Hỏi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Chỉnh hoặc gõ số lượng câu", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { if (config.totalQuestions > 1) viewModel.setTotalQuestions(config.totalQuestions - 1) },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                            }
                            
                            var tempTotalText by remember(config.totalQuestions) { mutableStateOf(config.totalQuestions.toString()) }
                            OutlinedTextField(
                                value = tempTotalText,
                                onValueChange = { newValue ->
                                    tempTotalText = newValue
                                    val parsed = newValue.toIntOrNull()
                                    if (parsed != null && parsed in 1..100) {
                                        viewModel.setTotalQuestions(parsed)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp),
                                modifier = Modifier.width(75.dp).height(50.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )

                            IconButton(
                                onClick = { if (config.totalQuestions < 100) viewModel.setTotalQuestions(config.totalQuestions + 1) },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 14.dp))

                    // Row: Score Per Answer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Điểm Một Câu Đúng", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Chỉnh hoặc gõ điểm/câu", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { if (config.pointsPerCorrect > 0.05) viewModel.setPointsPerCorrect(config.pointsPerCorrect - 0.05) },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                            }
                            
                            var tempPointsText by remember(config.pointsPerCorrect) { mutableStateOf(String.format(Locale.US, "%.2f", config.pointsPerCorrect)) }
                            OutlinedTextField(
                                value = tempPointsText,
                                onValueChange = { newValue ->
                                    tempPointsText = newValue
                                    val parsed = newValue.toDoubleOrNull()
                                    if (parsed != null && parsed > 0.0 && parsed <= 100.0) {
                                        viewModel.setPointsPerCorrect(parsed)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp),
                                modifier = Modifier.width(85.dp).height(50.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )

                            IconButton(
                                onClick = { if (config.pointsPerCorrect < 100.0) viewModel.setPointsPerCorrect(config.pointsPerCorrect + 0.05) },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            // Dynamic Selector of Questions to Grade
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Lựa Chọn Chấm Câu Nào", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Hỗ trợ chấm toàn bộ hoặc tùy chọn", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        
                        Text(
                            text = if (config.selectedQuestions.isEmpty()) "Chấm tất cả" else "Chấm ${config.selectedQuestions.size} câu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.clearQuestionSelection() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Text("Chấm Tất Cả (All)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.selectAllQuestions() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Chỉ Chọn Hết", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Flow of chips representing questions
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (i in 1..config.totalQuestions) {
                            val isGraded = config.isQuestionGraded(i)
                            Surface(
                                modifier = Modifier
                                    .clickable { viewModel.toggleQuestionSelection(i) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isGraded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isGraded) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "Câu $i",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGraded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // Import Answer Key from CSV / clipboard Card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { isCsvSectionExpanded = !isCsvSectionExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📥", fontSize = 18.sp)
                            Column {
                                Text("Nhập Đáp Án Từ Excel / CSV", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Nhập danh sách đáp án nhanh chóng", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(if (isCsvSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Expand")
                    }

                    if (isCsvSectionExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Định dạng hỗ trợ:\n1,A hoặc 1.A hoặc chỉ cần viết A B C D theo thứ tự cách nhau bằng dấu cách/xuống dòng.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = csvPasteText,
                            onValueChange = { csvPasteText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Dán nội dung Excel/CSV đáp án tại đây...", fontSize = 12.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                            maxLines = 8,
                            minLines = 4
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick Paste Clipboard Button
                            OutlinedButton(
                                onClick = {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clip.primaryClip?.getItemAt(0)
                                    val clipText = item?.text?.toString() ?: ""
                                    if (clipText.isNotEmpty()) {
                                        csvPasteText = clipText
                                        Toast.makeText(context, "Đã dán từ bộ nhớ đệm!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Bộ nhớ đệm trống!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Dán từ Clipboard", fontSize = 11.sp)
                            }

                            // Import Action Button
                            Button(
                                onClick = {
                                    if (csvPasteText.isNotBlank()) {
                                        viewModel.importConfigFromCsv(csvPasteText)
                                        Toast.makeText(context, "Đã import khóa đáp án mới!", Toast.LENGTH_SHORT).show()
                                        csvPasteText = ""
                                        isCsvSectionExpanded = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Nhập Đáp Án (Import)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            // Master Answer Key Grid with Lock toggle button
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Khóa Đáp Án Thủ Công",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isKeyLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = if (isKeyLocked) "Đã Khóa" else "Đang Mở",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isKeyLocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isKeyLocked) 
                                    "Khóa đáp án đang BẬT. Nhấp biểu tượng khóa để mở cho phép chỉnh sửa." 
                                else 
                                    "Nhấp chọn đáp án đúng cho từng câu hỏi phía dưới:",
                                fontSize = 11.sp,
                                color = if (isKeyLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                        }

                        // Lock / Unlock Toggle Button
                        IconButton(
                            onClick = { isKeyLocked = !isKeyLocked },
                            modifier = Modifier
                                .background(
                                    color = if (isKeyLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                )
                                .size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (isKeyLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isKeyLocked) "Mở khóa đáp án" else "Khóa đáp án",
                                tint = if (isKeyLocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (i in 1..config.totalQuestions) {
                            val activeAnswer = config.masterKeys[i] ?: "A"
                            val isGraded = config.isQuestionGraded(i)
                            val canEdit = isGraded && !isKeyLocked
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isGraded) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Câu $i",
                                    fontWeight = FontWeight.Bold,
       // fontSize = 13.sp,
                                    color = if (canEdit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                )
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("A", "B", "C", "D").forEach { option ->
                                        val isSelected = activeAnswer == option
                                        val boxColor = when {
                                            isSelected && !isKeyLocked -> MaterialTheme.colorScheme.primary
                                            isSelected && isKeyLocked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(
                                                    color = boxColor,
                                                    shape = CircleShape
                                                )
                                                .clickable(enabled = canEdit) {
                                                    viewModel.updateMasterKey(i, option)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = option,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 3: HISTORY LOGS TAB (LỊCH SỬ)
// ==========================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryTabScreen(viewModel: GradingViewModel) {
    val results by viewModel.historicalResults.collectAsStateWithLifecycle()
    var expandedResultId by remember { mutableStateOf<Int?>(null) }

    var selectedClassFilter by remember { mutableStateOf("Tất cả lớp") }
    var selectedExamFilter by remember { mutableStateOf("Tất cả bài kiểm tra") }
    var searchQuery by remember { mutableStateOf("") }

    val availableClasses = remember(results) {
        val list = results.map { it.className.ifEmpty { "Chưa phân lớp" } }.distinct().sorted()
        listOf("Tất cả lớp") + list
    }

    val availableExams = remember(results) {
        val list = results.map { it.examName.ifEmpty { "Chưa đặt tên bài" } }.distinct().sorted()
        listOf("Tất cả bài kiểm tra") + list
    }

    val filteredResults = remember(results, selectedClassFilter, selectedExamFilter, searchQuery) {
        results.filter { item ->
            val itemClass = item.className.ifEmpty { "Chưa phân lớp" }
            val itemExam = item.examName.ifEmpty { "Chưa đặt tên bài" }

            val matchesClass = selectedClassFilter == "Tất cả lớp" || itemClass == selectedClassFilter
            val matchesExam = selectedExamFilter == "Tất cả bài kiểm tra" || itemExam == selectedExamFilter
            val matchesSearch = searchQuery.isBlank() || item.studentName.contains(searchQuery, ignoreCase = true)

            matchesClass && matchesExam && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Lịch Sử Chấm Điểm", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "Hiển thị: ${filteredResults.size}/${results.size} bài thi",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (results.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearAllHistory() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xóa Tất Cả", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm theo tên học sinh...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filters Row (Class & Exam)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Lọc:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)

            // Class Dropdown Chip
            var showClassMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selectedClassFilter != "Tất cả lớp",
                    onClick = { showClassMenu = true },
                    label = { Text("🏫 $selectedClassFilter", fontSize = 11.sp) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                DropdownMenu(
                    expanded = showClassMenu,
                    onDismissRequest = { showClassMenu = false }
                ) {
                    availableClasses.forEach { cls ->
                        DropdownMenuItem(
                            text = { Text(cls, fontSize = 12.sp, fontWeight = if (cls == selectedClassFilter) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                selectedClassFilter = cls
                                showClassMenu = false
                            }
                        )
                    }
                }
            }

            // Exam Dropdown Chip
            var showExamMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selectedExamFilter != "Tất cả bài kiểm tra",
                    onClick = { showExamMenu = true },
                    label = { Text("📝 $selectedExamFilter", fontSize = 11.sp) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                DropdownMenu(
                    expanded = showExamMenu,
                    onDismissRequest = { showExamMenu = false }
                ) {
                    availableExams.forEach { exm ->
                        DropdownMenuItem(
                            text = { Text(exm, fontSize = 12.sp, fontWeight = if (exm == selectedExamFilter) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                selectedExamFilter = exm
                                showExamMenu = false
                            }
                        )
                    }
                }
            }

            if (selectedClassFilter != "Tất cả lớp" || selectedExamFilter != "Tất cả bài kiểm tra" || searchQuery.isNotEmpty()) {
                AssistChip(
                    onClick = {
                        selectedClassFilter = "Tất cả lớp"
                        selectedExamFilter = "Tất cả bài kiểm tra"
                        searchQuery = ""
                    },
                    label = { Text("Xóa lọc", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (results.isEmpty()) "Chưa có kết quả chấm thi nào được lưu." else "Không tìm thấy kết quả phù hợp bộ lọc.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredResults, key = { it.id }) { item ->
                    val isExpanded = expandedResultId == item.id
                    HistoryLogItem(
                        result = item,
                        isExpanded = isExpanded,
                        onToggle = {
                            expandedResultId = if (isExpanded) null else item.id
                        },
                        onDelete = {
                            viewModel.deleteResult(item.id)
                        },
                        onUpdateStudentName = { newName ->
                            viewModel.updateResultStudentName(item.id, newName)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryLogItem(
    result: GradingResult,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onUpdateStudentName: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateText = dateFormat.format(Date(result.timestamp))

    var isEditingName by remember { mutableStateOf(false) }
    var editedNameText by remember { mutableStateOf(result.studentName) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        border = if (isExpanded) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditingName) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = editedNameText,
                                onValueChange = { editedNameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = {
                                if (editedNameText.isNotBlank()) {
                                    onUpdateStudentName(editedNameText.trim())
                                }
                                isEditingName = false
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Lưu", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = result.studentName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = {
                                    editedNameText = result.studentName
                                    isEditingName = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Đổi tên", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (result.className.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Lớp ${result.className}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (result.examName.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = result.examName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = dateText,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Total points badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFF81C784))
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f pts", result.score),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Expanded panel showing detail answers and original student paper
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    if (!result.imagePath.isNullOrEmpty()) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = result.imagePath,
                                    contentDescription = "Bài làm gốc của học sinh",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Câu đúng: ${result.correctCount} / ${result.gradedCount}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Tổng số câu đề: ${result.totalQuestions}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }

                    // Grid flow of student answers
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        result.answers.forEach { ans ->
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .background(
                                        color = when {
                                            !ans.isGraded -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ans.isCorrect -> Color(0xFFE8F5E9)
                                            else -> Color(0xFFFFEBEE)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text("Câu ${ans.questionNumber}", fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val displayAns = when {
                                            !ans.isGraded -> "Bỏ"
                                            ans.studentAnswer == "Trống" || ans.studentAnswer.isEmpty() || ans.studentAnswer == "-" -> "-"
                                            ans.studentAnswer == "Nhiều đáp án" || ans.studentAnswer.contains("NHIỀU") || ans.studentAnswer.equals("X", ignoreCase = true) -> "X"
                                            else -> ans.studentAnswer
                                        }
                                        Text(
                                            text = displayAns,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (!ans.isGraded) MaterialTheme.colorScheme.outline else if (ans.isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                        if (ans.isGraded && !ans.isCorrect) {
                                            Text(
                                                text = "(${ans.correctAnswer})",
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(start = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 4: PC CONNECT TAB (KẾT NỐI PC REMOTE)
// ==========================================

@Composable
fun PcConnectTabScreen(
    viewModel: GradingViewModel,
    isServerRunning: Boolean,
    serverIp: String?
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Visual Banner card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("💻", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Điều Khiển Trên Máy Tính",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mở rộng quyền quản lý khóa đáp án, nhập xuất CSV, xem và quản lý bảng điểm từ màn hình máy tính.",
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item {
            // Connection Status and Actions
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trạng Thái Server",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (isServerRunning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ) {
                            Text(
                                text = if (isServerRunning) "Đang hoạt động" else "Đang tắt",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isServerRunning) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isServerRunning && serverIp != null) {
                        // IP Copy Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(ClipData.newPlainText("PC Server IP", serverIp))
                                    Toast.makeText(context, "Đã sao chép liên kết IP!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("NHẬP ĐỊA CHỈ NÀY TRÊN TRÌNH DUYỆT PC:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = serverIp,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("👉 Click để sao chép địa chỉ", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        // Warning IP not found
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚠️ Server chưa khởi chạy. Vui lòng bật nút phía dưới hoặc kiểm tra kết nối Wi-Fi.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Power Switch Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startServer() },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            enabled = !isServerRunning
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bật Server", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = { viewModel.stopServer() },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                            enabled = isServerRunning
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tắt Server", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            // Standalone Web App Download Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💻", fontSize = 20.sp)
                        Text(
                            text = "Bản Web App Độc Lập (Dành Cho PC)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Phần mềm chạy trực tiếp trên trình duyệt máy tính, dùng Webcam của máy tính để chấm bài thi mà không cần dùng điện thoại.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isServerRunning && !serverIp.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Link tải ZIP trên PC: $serverIp/download-standalone-zip",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "💡 Mẹo: Bật Server ở trên, rồi mở trình duyệt trên máy tính để tải trọn gói file ZIP mã nguồn về máy tính chỉ với 1 click!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            // Instructions Card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hướng Dẫn Kết Nối Từng Bước",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    InstructionStepRow(stepNum = "1", text = "Kết nối điện thoại và máy tính (PC/Laptop) của bạn vào cùng một mạng Wi-Fi.")
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionStepRow(stepNum = "2", text = "Bật tính năng 'Bật Server' phía trên để mở cổng kết nối.")
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionStepRow(stepNum = "3", text = "Mở trình duyệt web (Chrome, Edge, Safari...) trên máy tính.")
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionStepRow(stepNum = "4", text = "Nhập chính xác địa chỉ IP (ví dụ: http://192.168.1.5:8080) vào thanh địa chỉ trình duyệt trên máy tính và nhấn Enter.")
                }
            }
        }
    }
}

@Composable
fun InstructionStepRow(stepNum: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNum,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==========================================
// UTILITY FUNCTIONS
// ==========================================

fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
