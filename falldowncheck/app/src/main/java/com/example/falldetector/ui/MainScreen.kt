package com.example.falldetector.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.falldetector.service.FallDetectionService
import com.example.falldetector.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private data class FallEvent(val time: String, val score: Float, val isFall: Boolean)

/**
 * Compose 主界面
 *
 * 布局:
 *   ┌─ 状态栏 (运行中/已停止)
 *   ├─ 实时 SVM / 角度
 *   ├─ 最近评分进度条
 *   ├─ SVM 波形图 (5秒)
 *   ├─ 阈值滑块 (默认40)
 *   ├─ [开始检测] [停止检测]
 *   └─ 事件日志列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRequestPermission: () -> Unit,
    hasNotificationPermission: Boolean
) {
    val context = LocalContext.current

    // Service 状态
    var detectionService by remember { mutableStateOf<FallDetectionService?>(null) }
    var serviceBound by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var isFall by remember { mutableStateOf(false) }
    var latestScore by remember { mutableFloatStateOf(0f) }
    var realTimeSvm by remember { mutableFloatStateOf(1.0f) }
    var realTimeAngle by remember { mutableFloatStateOf(0f) }
    var threshold by remember { mutableFloatStateOf(40f) }
    var components by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    // 事件日志: 时间 → 描述
    val events = remember { mutableStateListOf<FallEvent>() }
    val listState = rememberLazyListState()

    // SVM 历史 (最近 150 点用于波形图)
    val svmHistory = remember { mutableStateListOf<Float>() }
    repeat(150) { svmHistory.add(1.0f) }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Service 连接
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? FallDetectionService.LocalBinder
                detectionService = binder?.getService()
                serviceBound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                detectionService = null
                serviceBound = false
                isRunning = false
            }
        }
    }

    // 绑定 Service
    fun bindService() {
        val intent = Intent(context, FallDetectionService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        serviceBound = true
    }

    fun startService() {
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onRequestPermission()
            return
        }
        val intent = Intent(context, FallDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        if (!serviceBound) bindService()
        isRunning = true
    }

    fun stopService() {
        val intent = Intent(context, FallDetectionService::class.java)
        context.stopService(intent)
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
        detectionService = null
        isRunning = false
        latestScore = 0f
        realTimeSvm = 1.0f
        realTimeAngle = 0f
        isFall = false
        components = emptyMap()
    }

    // 订阅 Service 的 StateFlow，将所有数据同步到 UI state
    LaunchedEffect(detectionService) {
        val svc = detectionService ?: return@LaunchedEffect

        // SVM 波形历史缓冲区 (最多 150 点)
        val localHistory = mutableListOf<Float>()
        localHistory.addAll(svmHistory)

        launch {
            svc.realTimeSvm.collectLatest { svm ->
                realTimeSvm = svm
                localHistory.add(svm)
                while (localHistory.size > 150) {
                    localHistory.removeAt(0)
                }
                // 每收集到一批数据时刷新 svmHistory
                svmHistory.clear()
                svmHistory.addAll(localHistory)
            }
        }
        launch {
            svc.realTimeAngle.collectLatest { angle ->
                realTimeAngle = angle
            }
        }
        launch {
            svc.latestScore.collectLatest { score ->
                latestScore = score
            }
        }
        launch {
            svc.isFall.collectLatest { fall ->
                isFall = fall
            }
        }
        launch {
            svc.latestComponents.collectLatest { comps ->
                components = comps
            }
        }
    }

    // 记录评分事件
    LaunchedEffect(latestScore) {
        if (latestScore > 10f) {
            events.add(0, FallEvent(timeFormat.format(Date()), latestScore, isFall))
            if (events.size > 20) events.removeAt(events.size - 1)
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ===== 顶部状态栏 =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFall) FallDanger.copy(alpha = 0.15f)
                        else if (isRunning) SafeNormal.copy(alpha = 0.1f)
                        else SurfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Circle else Icons.Default.StopCircle,
                            contentDescription = null,
                            tint = if (isFall) FallDanger else if (isRunning) SafeNormal else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                isFall -> "检测到可能跌倒!"
                                isRunning -> "跌倒检测 — 运行中"
                                else -> "跌倒检测 — 已停止"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isFall) FallDanger else TextPrimary,
                            fontWeight = if (isFall) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ===== 实时指标卡片 (SVM + 角度) =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = "实时 SVM",
                        value = "%.2f g".format(realTimeSvm),
                        valueColor = when {
                            realTimeSvm < 0.5f -> FallWarning
                            realTimeSvm > 2.3f -> FallDanger
                            else -> TextPrimary
                        }
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = "实时角度",
                        value = "%.1f°".format(realTimeAngle),
                        valueColor = when {
                            realTimeAngle > 30f -> FallWarning
                            else -> TextPrimary
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ===== 最近评分 + 进度条 =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "最近评分",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "%.1f".format(latestScore),
                                style = MaterialTheme.typography.displayMedium,
                                color = when {
                                    latestScore >= 40f -> FallDanger
                                    latestScore >= 30f -> FallWarning
                                    else -> TextPrimary
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // 进度条
                        LinearProgressIndicator(
                            progress = { (latestScore / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                latestScore >= 40f -> FallDanger
                                latestScore >= 30f -> FallWarning
                                else -> Primary
                            },
                            trackColor = SurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "阈值: $threshold  |  ${(latestScore / threshold * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ===== SVM 波形图 =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SVM 波形 (5秒)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        SvmWaveformChart(
                            data = svmHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ===== 阈值滑块 =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp, 12.dp)) {
                        Text(
                            text = "检测阈值",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "20",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Slider(
                                value = threshold,
                                onValueChange = {
                                    threshold = it
                                    detectionService?.scoreThreshold = it
                                },
                                valueRange = 20f..70f,
                                steps = 49,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Primary,
                                    activeTrackColor = Primary,
                                    inactiveTrackColor = SurfaceVariant
                                )
                            )
                            Text(
                                text = "70",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "当前: $threshold  (默认: 40)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ===== 控制按钮 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { startService() },
                        modifier = Modifier.weight(1f),
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafeNormal,
                            disabledContainerColor = SafeNormal.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("开始检测")
                    }
                    Button(
                        onClick = { stopService() },
                        modifier = Modifier.weight(1f),
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FallDanger,
                            disabledContainerColor = FallDanger.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("停止检测")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ===== 评分分解详情 =====
                if (components.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "评分分解",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            ComponentsGrid(components)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ===== 事件日志列表 =====
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "事件日志",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(events, key = { it.time + it.score }) { event ->
                                EventLogItem(event)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 子组件 ====================

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SvmWaveformChart(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(SurfaceVariant.copy(alpha = 0.5f))) {
        if (data.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val padding = 12f
        val chartW = w - padding * 2
        val chartH = h - padding * 2

        // 绘制网格
        val gridColor = Color.White.copy(alpha = 0.08f)
        for (i in 0..4) {
            val y = padding + chartH * i / 4f
            drawLine(gridColor, Offset(padding, y), Offset(w - padding, y), strokeWidth = 1f)
        }

        // 绘制阈值线 (SVM = 0.5 FF阈值, SVM = 2.0 冲击阈值)
        val ffY = padding + chartH * (1f - 0.5f / 4f) // 0.5g line
        val impY = padding + chartH * (1f - 2.0f / 4f) // 2.0g line
        drawLine(
            FallWarning.copy(alpha = 0.4f),
            Offset(padding, ffY), Offset(w - padding, ffY),
            strokeWidth = 1.5f
        )
        drawLine(
            FallDanger.copy(alpha = 0.4f),
            Offset(padding, impY), Offset(w - padding, impY),
            strokeWidth = 1.5f
        )

        // 绘制 SVM 曲线
        val path = Path()
        val stepX = chartW / (data.size - 1)
        val maxSvm = 4f // Y轴范围 0~4g

        data.forEachIndexed { i, svm ->
            val x = padding + i * stepX
            val y = padding + chartH * (1f - svm.coerceIn(0f, maxSvm) / maxSvm)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = ChartLine,
            style = Stroke(width = 2f)
        )
    }
}

@Composable
private fun ComponentsGrid(components: Map<String, Float>) {
    val displayKeys = listOf(
        "ff_depth" to "FF深度",
        "ff_duration" to "FF时长",
        "impact_peak" to "冲击峰值",
        "angle_change" to "角度变化",
        "stability" to "稳定性",
        "isolation" to "隔离度",
        "repeat_penalty" to "重复惩罚",
        "ff_preceded_impact" to "FF→冲击",
        "maximum_ang_vel" to "最大角速度",
        "end_state" to "末尾静止",
        "maximum_jolt" to "SVM跳变",
        "sustained_angle" to "角度持续",
        "impact_clusters" to "冲击聚类",
        "high_g_ratio_penalty" to "高g惩罚",
        "total" to "总分"
    )

    Column {
        for ((key, label) in displayKeys) {
            val value = components[key] ?: continue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = if (value == value.toInt().toFloat()) "${value.toInt()}" else "%.1f".format(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        value > 0f -> SafeNormal
                        value < 0f -> FallDanger
                        else -> TextSecondary
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EventLogItem(event: FallEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.time,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = "score=%.1f".format(event.score),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                event.isFall -> FallDanger
                event.score >= 40f -> FallWarning
                else -> TextPrimary
            },
            fontWeight = if (event.isFall) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = if (event.isFall) "跌倒!" else "",
            style = MaterialTheme.typography.bodySmall,
            color = FallDanger,
            fontWeight = FontWeight.Bold
        )
    }
}
