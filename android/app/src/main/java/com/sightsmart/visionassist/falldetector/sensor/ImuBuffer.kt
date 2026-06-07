package com.sightsmart.visionassist.falldetector.sensor

import com.sightsmart.visionassist.falldetector.algorithm.ImuSample
import kotlin.math.sqrt

/**
 * 5 秒滑动窗口缓冲管理器
 *
 * 窗口参数:
 *   - 窗口长度 ~5 秒 (@50Hz → 约 250 样本)
 *   - 足够覆盖一次完整跌倒事件 (FF前60 + FF + 冲击后60 + 末尾35%)
 *   - 每 1 秒触发一次算法分析
 */
class ImuBuffer(
    private val windowSizeSamples: Int = 250
) {
    private val samples = ArrayDeque<ImuSample>()

    /** 当前最新的重力分量 (TYPE_GRAVITY) */
    var latestGravityX = 0f
        private set
    var latestGravityY = 0f
        private set
    var latestGravityZ = 9.81f
        private set

    /**
     * 添加一帧加速度计数据 (TYPE_ACCELEROMETER)
     * SVM = √(x² + y² + z²) / 9.81  (与 JS 版完全一致)
     */
    fun addRawAcc(timestamp: Long, ax: Float, ay: Float, az: Float) {
        val svm = sqrt(ax * ax + ay * ay + az * az) / 9.81f
        samples.addLast(
            ImuSample(
                timestamp = timestamp,
                svm_g = svm,
                gravity_x = latestGravityX,
                gravity_y = latestGravityY,
                gravity_z = latestGravityZ
            )
        )
        // 限制缓冲区大小
        while (samples.size > windowSizeSamples) {
            samples.removeFirst()
        }
    }

    /** 更新重力分量 (TYPE_GRAVITY) */
    fun updateGravity(gx: Float, gy: Float, gz: Float) {
        latestGravityX = gx
        latestGravityY = gy
        latestGravityZ = gz
    }

    /** 获取当前所有样本的快照 */
    fun getSamples(): List<ImuSample> = samples.toList()

    /** 当前缓冲样本数 */
    val size: Int get() = samples.size

    /** 最新的 SVM 值 */
    val latestSvm: Float
        get() = samples.lastOrNull()?.svm_g ?: 1.0f

    /** 最新的角度 */
    fun latestAngle(refX: Float, refY: Float, refZ: Float): Float {
        val gx = latestGravityX
        val gy = latestGravityY
        val gz = latestGravityZ
        val rn = sqrt(refX * refX + refY * refY + refZ * refZ)
        if (rn < 1e-6f) return 0f
        val ux = refX / rn; val uy = refY / rn; val uz = refZ / rn
        val gn = sqrt(gx * gx + gy * gy + gz * gz)
        if (gn < 1e-6f) return 0f
        val vx = gx / gn; val vy = gy / gn; val vz = gz / gn
        val dot = ux * vx + uy * vy + uz * vz
        return Math.toDegrees(
            kotlin.math.acos(dot.coerceIn(-1f, 1f).toDouble())
        ).toFloat()
    }

    /** 清空缓冲区 */
    fun clear() {
        samples.clear()
    }
}
