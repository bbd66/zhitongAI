package com.sightsmart.visionassist.falldetector.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.sightsmart.visionassist.R
import com.sightsmart.visionassist.falldetector.algorithm.FallDetector
import com.sightsmart.visionassist.falldetector.sensor.ImuBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 前台 Service — 持续后台采集传感器并运行跌倒检测算法
 *
 * 数据流:
 *   TYPE_ACCELEROMETER → SVM → ImuBuffer.addRawAcc()
 *   TYPE_GRAVITY       → g分量 → ImuBuffer.updateGravity()
 *   每1秒 → 取出缓冲区 → FallDetector.computeOptimizedScore() → 判定跌倒
 */
class FallDetectionService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "fall_detection_channel"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_NAME = "跌倒检测"

        // 结果广播
        const val ACTION_RESULT = "com.sightsmart.visionassist.FALL_DETECTED"
        const val EXTRA_SCORE = "score"
        const val EXTRA_IS_FALL = "is_fall"
        const val EXTRA_COMPONENTS = "components"
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var accSensor: Sensor
    private lateinit var gravitySensor: Sensor
    private var vibrator: Vibrator? = null
    private var sensorsAvailable = false

    private val buffer = ImuBuffer(windowSizeSamples = 250)
    private var analysisJob: Job? = null

    // 参考姿态 (直立: 重力朝下 z=G)
    private val reference = Triple(0f, 0f, 9.81f)

    // 可调节阈值 (默认 60)
    @Volatile
    var scoreThreshold: Float = 60f

    // 运行状态
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // 最新分析结果
    private val _latestScore = MutableStateFlow(0f)
    val latestScore: StateFlow<Float> = _latestScore.asStateFlow()

    private val _isFall = MutableStateFlow(false)
    val isFall: StateFlow<Boolean> = _isFall.asStateFlow()

    private val _latestComponents = MutableStateFlow<Map<String, Float>>(emptyMap())
    val latestComponents: StateFlow<Map<String, Float>> = _latestComponents.asStateFlow()

    // 实时数据
    private val _realTimeSvm = MutableStateFlow(1.0f)
    val realTimeSvm: StateFlow<Float> = _realTimeSvm.asStateFlow()

    private val _realTimeAngle = MutableStateFlow(0f)
    val realTimeAngle: StateFlow<Float> = _realTimeAngle.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        // 检查传感器是否可用
        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val grav = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        
        if (acc == null || grav == null) {
            android.util.Log.e("FallDetectionService", "传感器不可用")
            sensorsAvailable = false
            stopSelf()
            return
        }
        
        accSensor = acc
        gravitySensor = grav
        sensorsAvailable = true

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!sensorsAvailable) {
            android.util.Log.e("FallDetectionService", "传感器不可用，停止服务")
            stopSelf()
            return START_NOT_STICKY
        }
        
        startForeground(NOTIFICATION_ID, buildNotification())

        sensorManager.registerListener(
            this, accSensor, SensorManager.SENSOR_DELAY_GAME
        )
        sensorManager.registerListener(
            this, gravitySensor, SensorManager.SENSOR_DELAY_GAME
        )
        _isRunning.value = true

        // 启动周期性分析 (每 1 秒)
        analysisJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(1000)
                runAnalysis()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        analysisJob?.cancel()
        sensorManager.unregisterListener(this)
        _isRunning.value = false
        super.onDestroy()
    }

    /**
     * Binder — 暴露 Service 实例，UI 可通过 bindService 获取并订阅 StateFlow
     */
    inner class LocalBinder : Binder() {
        fun getService(): FallDetectionService = this@FallDetectionService
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    // ==================== 传感器回调 ====================

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                buffer.addRawAcc(event.timestamp, ax, ay, az)
                _realTimeSvm.value = buffer.latestSvm
            }
            Sensor.TYPE_GRAVITY -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                buffer.updateGravity(gx, gy, gz)
            }
        }
        // 实时角度更新
        val (rx, ry, rz) = reference
        _realTimeAngle.value = buffer.latestAngle(rx, ry, rz)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ==================== 核心分析 ====================

    private fun runAnalysis() {
        val samples = buffer.getSamples()
        if (samples.size < 15) return

        val (isFallDetected, score, components) = FallDetector.computeOptimizedScore(
            samples = samples,
            reference = reference,
            ffThresh = 0.5f,
            impThresh = 2.0f,
            scoreThreshold = scoreThreshold
        )

        _latestScore.value = score
        _isFall.value = isFallDetected
        _latestComponents.value = components

        if (isFallDetected) {
            triggerFallAlert(score)
        }
    }

    // ==================== 报警 ====================

    private fun triggerFallAlert(score: Float) {
        android.util.Log.d("FallDetectionService", "触发摔倒警报，评分: $score")
        
        // 振动
        vibrator?.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 300, 200, 300, 200, 500),
                -1 // 不重复
            )
        )

        // 更新通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠ 检测到可能跌倒!")
            .setContentText("评分: ${score} (阈值: $scoreThreshold)")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        
        // 使用LocalBroadcastManager发送广播通知前端
        val intent = Intent(ACTION_RESULT)
        intent.putExtra(EXTRA_IS_FALL, true)
        intent.putExtra(EXTRA_SCORE, score)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        android.util.Log.d("FallDetectionService", "广播已发送")
    }

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "跌倒检测后台服务通知"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("跌倒检测运行中")
            .setContentText("正在监测传感器数据...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
