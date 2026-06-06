package com.example.falldetector.algorithm

/**
 * 单帧 IMU 样本 — 对应 JS 版 samples 数组中的每个元素
 *
 * @param timestamp     采集时刻 (System.nanoTime())
 * @param svm_g         全加速度 SVM (来自 TYPE_ACCELEROMETER 原始值) / 9.81
 * @param gravity_x     TYPE_GRAVITY 传感器的 x 分量
 * @param gravity_y     TYPE_GRAVITY 传感器的 y 分量
 * @param gravity_z     TYPE_GRAVITY 传感器的 z 分量
 */
data class ImuSample(
    val timestamp: Long,
    val svm_g: Float,
    val gravity_x: Float,
    val gravity_y: Float,
    val gravity_z: Float
)
