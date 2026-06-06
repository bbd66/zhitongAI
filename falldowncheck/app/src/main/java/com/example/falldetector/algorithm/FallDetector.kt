package com.example.falldetector.algorithm

import kotlin.math.*

/**
 * 跌倒检测核心算法 — 多特征加权评分 (v3.1 Android/Kotlin 翻译版)
 *
 * 与 fallDetection.js / optimized_fall_detector.py 逐行对齐。
 *
 * 评分体系:
 *   特征 1: 自由落体深度     ( 0~ 20)
 *   特征 2: 自由落体时长     ( 0~ 10)
 *   特征 3: 冲击峰值        ( 0~ 25) — FF 后 0~3 样本内计数
 *   特征 4: 本地角度变化    ( 0~ 15)
 *   特征 5: 事件后稳定性    ( 0~ 15)
 *   特征 6: 事件隔离度      ( 0~ 15)
 *   特征 7: FF 后紧接冲击   (-20~ 10) — 关键区分
 *   特征 8: 最大角速度      ( 0~ 15)
 *   特征 9: 末尾静止+姿态   (-10~ 20)
 *   特征10: SVM 最大跳变    ( 0~ 15)
 *   特征11: 角度持续性      (-10~ 10)
 *   特征12: 高冲击聚类数    (-15~  5)
 *   特征13: 高 g 值占比     (-15~  0)
 */
object FallDetector {

    private const val G = 9.81f

    // ==================== 工具函数 ====================

    private fun List<Float>.mean(): Float {
        if (isEmpty()) return 0f
        var sum = 0f
        for (v in this) sum += v
        return sum / size
    }

    /** 总体标准差 (除以 n, 非 n-1) — 与 JS `pstdev` 一致 */
    private fun List<Float>.pstdev(): Float {
        if (size < 2) return 0f
        val m = mean()
        var sqSum = 0f
        for (v in this) sqSum += (v - m) * (v - m)
        return sqrt(sqSum / size)
    }

    /**
     * 计算加速度向量与参考方向之间的夹角 (度)
     * 与 JS `calAngle(gx, gy, gz, refX=0, refY=0, refZ=G)` 逐行一致
     */
    private fun calAngle(
        gx: Float, gy: Float, gz: Float,
        refX: Float, refY: Float, refZ: Float
    ): Float {
        val rn = sqrt(refX * refX + refY * refY + refZ * refZ)
        if (rn < 1e-6f) return 0f
        val ux = refX / rn; val uy = refY / rn; val uz = refZ / rn

        val gn = sqrt(gx * gx + gy * gy + gz * gz)
        if (gn < 1e-6f) return 0f
        val vx = gx / gn; val vy = gy / gn; val vz = gz / gn

        val dot = ux * vx + uy * vy + uz * vz
        return Math.toDegrees(acos(dot.coerceIn(-1f, 1f)).toDouble()).toFloat()
    }

    // ==================== 静止判断 ====================

    /**
     * 检查窗口内是否静止
     * @return Pair(isStable: Boolean, score: Float)
     */
    private fun isPersistentlyStable(
        svm: List<Float>, angles: List<Float>,
        startIdx: Int, windowLen: Int, angleStartRef: Float
    ): Pair<Boolean, Float> {
        val endIdx = minOf(startIdx + windowLen, svm.size)
        if (endIdx - startIdx < 5) return Pair(false, 0f)

        val segSvm = svm.subList(startIdx, endIdx)
        val segAngles = angles.subList(startIdx, endIdx)
        val avgSvm = segSvm.mean()
        val stdSvm = segSvm.pstdev()
        val svmStable = (avgSvm > 0.8f && avgSvm < 1.2f) && (stdSvm < 0.3f)

        val avgAngle = segAngles.mean()
        val anglePersists = abs(avgAngle - angleStartRef) > 10 && abs(avgAngle - angleStartRef) < 70

        return if (svmStable && anglePersists) Pair(true, 15f)
        else if (svmStable) Pair(true, 8f)
        else Pair(false, 0f)
    }

    // ==================== FF 区域数据结构 ====================

    private data class FfRegion(
        val start: Int, val end: Int, val len: Int, val minSvm: Float
    )

    // ==================== 单个 FF 区域评分 (特征1-6) ====================

    /**
     * @return Triple(score, components map, sustainedChange)
     */
    private fun scoreRegion(
        region: FfRegion, svm: List<Float>, angles: List<Float>,
        ffRegions: List<FfRegion>, n: Int
    ): Triple<Float, MutableMap<String, Float>, Float> {
        val fs = region.start; val fe = region.end
        val flen = region.len; val fmin = region.minSvm
        var score = 0f
        val comp = mutableMapOf<String, Float>()

        // ===== 特征1: 自由落体深度 (0~20) =====
        val depth = when {
            fmin >= 0.15f && fmin <= 0.50f -> (10f + 10f * (fmin - 0.15f) / 0.35f)
            fmin >= 0.05f && fmin < 0.15f  -> (8f * (fmin - 0.05f) / 0.10f)
            else -> 0f
        }.coerceIn(0f, 20f)
        score += depth
        comp["ff_depth"] = depth

        if (fmin < 0.1f) {
            val deepPenalty = minOf(10f, (0.1f - fmin) * 100f)
            score -= deepPenalty
            comp["ff_deep_penalty"] = -deepPenalty
        } else {
            comp["ff_deep_penalty"] = 0f
        }

        // ===== 特征2: 自由落体时长 (0~10) =====
        val dur = when {
            flen <= 1         -> 0f
            flen in 2..5      -> (5f + 5f * (flen - 2) / 3f)
            flen in 6..8      -> (8f - 3f * (flen - 5) / 3f)
            else              -> 0f
        }.coerceIn(0f, 10f)
        score += dur
        comp["ff_duration"] = dur

        // ===== 特征3: 冲击峰值 — FF后0~3样本 (0~25) =====
        val nearbyImps = (fe + 1..minOf(fe + 3, n - 1))
            .filter { svm[it] > 2.0f }
            .map { svm[it] }
        val maxImp = nearbyImps.maxOrNull() ?: 0f

        var imp: Float
        if (maxImp > 0f) {
            imp = when {
                maxImp > 4.0f -> 25f
                maxImp > 3.5f -> 20f + 5f * (maxImp - 3.5f) / 0.5f
                maxImp > 3.0f -> 15f + 5f * (maxImp - 3.0f) / 0.5f
                maxImp > 2.5f -> 10f + 5f * (maxImp - 2.5f) / 0.5f
                maxImp > 2.0f -> 5f + 5f * (maxImp - 2.0f) / 0.5f
                maxImp > 1.8f -> 2f
                else -> 0f
            }
        } else {
            val farImps = (maxOf(0, fe - 2)..minOf(fe + 15, n - 1))
                .filter { svm[it] > 2.0f }
                .map { svm[it] }
            if (farImps.isNotEmpty()) {
                val farMax = farImps.max()
                imp = when {
                    farMax > 2.5f -> 6f
                    farMax > 2.0f -> 3f
                    else -> 0f
                }
            } else {
                imp = 0f
                score -= 5f
                comp["no_impact_penalty"] = -5f
            }
        }
        imp = imp.coerceIn(-5f, 25f)
        score += imp
        comp["impact_peak"] = imp

        // ===== 特征4: 本地角度变化 (0~15) =====
        val preStart = maxOf(0, fs - 15)
        val preEnd = maxOf(3, fs - 2)
        val preAng = angles.subList(preStart, preEnd).mean()

        val postCheckStart = minOf(fe + 20, n - 5)
        val postCheckEnd = minOf(postCheckStart + 20, n)
        val sustainedChange = if (postCheckEnd > postCheckStart) {
            abs(angles.subList(postCheckStart, postCheckEnd).mean() - preAng)
        } else 0f

        var ang = when {
            sustainedChange > 45f -> 15f
            sustainedChange > 30f -> 12f
            sustainedChange > 20f -> 8f
            sustainedChange > 10f -> 4f
            sustainedChange > 5f  -> 2f
            else -> 0f
        }

        if (postCheckEnd > postCheckStart) {
            val last15Ang = if (n >= 15) angles.subList(n - 15, n).mean() else 0f
            val midAng = angles.subList(postCheckStart, postCheckEnd).mean()
            if (abs(last15Ang - midAng) < 15f && ang > 0f) {
                ang = minOf(15f, ang + 3f)
                comp["sustained_bonus"] = 3f
            }
        }

        score += ang
        comp["angle_change"] = ang

        // ===== 特征5: 持续静止稳定性 (0~15) =====
        val (_, stabScore) = isPersistentlyStable(
            svm, angles, minOf(fe + 15, n - 1), 30, preAng
        )
        score += stabScore
        comp["stability"] = stabScore

        // ===== 特征6: 事件隔离度 (0~15) =====
        val windowStart = maxOf(0, fs - 60)
        val windowEnd = minOf(n, fe + 60)
        val nearbyCount = ffRegions.count { r ->
            r.start >= windowStart && r.end <= windowEnd
        }
        val isolation = when {
            nearbyCount <= 2  -> 15f
            nearbyCount <= 5  -> 8f
            nearbyCount <= 10 -> 3f
            else              -> 0f
        }
        score += isolation
        comp["isolation"] = isolation

        comp["total"] = (score * 10f).roundToInt() / 10f
        return Triple(score, comp, sustainedChange)
    }

    // ==================== 主检测函数 ====================

    /**
     * 多特征加权评分跌倒检测
     *
     * @param samples   传感器样本列表
     * @param reference 参考姿态向量，默认直立 (0, 0, G)
     * @param ffThresh  自由落体 SVM 阈值
     * @param impThresh 冲击 SVM 阈值
     * @param scoreThreshold 判定跌倒的评分阈值 (默认 40)
     * @return Quadruple(是否跌倒, 总分, 阈值, 详情 Map)
     */
    fun computeOptimizedScore(
        samples: List<ImuSample>,
        reference: Triple<Float, Float, Float> = Triple(0f, 0f, G),
        ffThresh: Float = 0.5f,
        impThresh: Float = 2.0f,
        scoreThreshold: Float = 40f
    ): Quadruple<Boolean, Float, Map<String, Float>> {
        val n = samples.size
        if (n < 15) return Quadruple(false, 0f, emptyMap())

        val svm = samples.map { it.svm_g }
        val angles = samples.map { s ->
            calAngle(s.gravity_x, s.gravity_y, s.gravity_z, reference.first, reference.second, reference.third)
        }

        val n30 = (n * 30 / 100)
        val startAngle = angles.subList(0, n30).mean()
        val endAngle = angles.subList(n - n30, n).mean()
        val globalAngleChange = abs(endAngle - startAngle)

        // ---- 1. 寻找自由落体区域 ----
        val ffRegions = mutableListOf<FfRegion>()
        var i = 0
        while (i < n) {
            if (svm[i] < ffThresh) {
                val start = i
                while (i < n && svm[i] < ffThresh) i++
                val length = i - start
                val regionMin = svm.subList(start, i).min()
                ffRegions.add(FfRegion(start, i - 1, length, regionMin))
            } else {
                i++
            }
        }

        // ---- 2. 冲击点 ----
        val impIndices = (0 until n).filter { svm[it] > impThresh }

        // ---- 3. 全局特征预计算 ----

        // (A) SVM最大急动度
        var maxSvmDelta = 0f
        for (idx in 1 until n) {
            val delta = abs(svm[idx] - svm[idx - 1])
            if (delta > maxSvmDelta) maxSvmDelta = delta
        }

        // (B) 最大角速度 (帧间角度差)
        var maxAngleDelta = 0f
        for (idx in 1 until n) {
            val delta = abs(angles[idx] - angles[idx - 1])
            if (delta > maxAngleDelta) maxAngleDelta = delta
        }

        // (C) FF后紧接冲击验证
        var hasFfPrecededImpact = false
        var ffPrecededImpactCount = 0
        for (impIdx in impIndices) {
            if (svm[impIdx] < 2.3f) continue
            for (lookback in 1..3) {
                val checkIdx = impIdx - lookback
                if (checkIdx >= 0 && svm[checkIdx] < ffThresh) {
                    hasFfPrecededImpact = true
                    ffPrecededImpactCount++
                    break
                }
            }
        }

        val maxImpValue = svm.max()
        var maxImpNearFf = 0f
        for (region in ffRegions) {
            val fe = region.end
            for (p in (fe + 1)..minOf(fe + 5, n - 1)) {
                if (svm[p] > maxImpNearFf) maxImpNearFf = svm[p]
            }
        }

        // (D) 高冲击事件聚类计数
        var highImpClusters = 0
        var inCluster = false
        var clusterGap = 0
        for (idx in 0 until n) {
            val v = svm[idx]
            if (v > 2.3f) {
                if (!inCluster) {
                    highImpClusters++
                    inCluster = true
                }
                clusterGap = 0
            } else {
                if (inCluster) {
                    clusterGap++
                    if (clusterGap > 3) inCluster = false
                }
            }
        }

        // (E) 高g值样本占比
        val highGCount = svm.count { it > 2.3f }
        val highGRatio = highGCount.toFloat() / n

        // ---- 5. 对每个FF区域评分 ----
        var bestScore = 0f
        var bestComp = mutableMapOf<String, Float>()
        for (region in ffRegions) {
            val (s, comp, _) = scoreRegion(region, svm, angles, ffRegions, n)
            if (s > bestScore) {
                bestScore = s
                bestComp = comp
            }
        }

        // ---- 6. 回退: 无FF但有高冲击+大角度变化 ----
        if (ffRegions.isEmpty() || bestScore < 20f) {
            if (maxImpValue > 2.5f && globalAngleChange > 15f) {
                val impFb = when {
                    maxImpValue > 4.0f -> 20f
                    maxImpValue > 3.5f -> 15f
                    maxImpValue > 3.0f -> 10f
                    maxImpValue > 2.5f -> 5f
                    else -> 0f
                }
                val angFb = when {
                    globalAngleChange > 45f -> 12f
                    globalAngleChange > 30f -> 8f
                    globalAngleChange > 15f -> 4f
                    else -> 0f
                }
                val fallbackScore = impFb + angFb
                if (fallbackScore > bestScore) {
                    bestScore = fallbackScore
                    bestComp = mutableMapOf(
                        "ff_depth" to 0f, "ff_duration" to 0f, "ff_deep_penalty" to 0f,
                        "impact_peak" to impFb, "angle_change" to angFb,
                        "stability" to 0f, "isolation" to 0f, "repeat_penalty" to 0f,
                        "sustained_angle" to 0f, "maximum_jolt" to 0f, "maximum_ang_vel" to 0f,
                        "ff_preceded_impact" to 0f, "pre_stability" to 0f,
                        "total" to ((fallbackScore * 10f).roundToInt() / 10f),
                        "note" to -1f // marker for fallback
                    )
                }
            }
        }

        // ===================== 全局特征调整 =====================

        // ---- 重复事件惩罚 ----
        val totalFfRegions = ffRegions.size
        val repeatPenalty = when {
            totalFfRegions >= 25 -> 35f
            totalFfRegions >= 15 -> 25f
            totalFfRegions >= 10 -> 18f
            totalFfRegions >= 6  -> 10f
            totalFfRegions >= 3  -> 5f
            else -> 0f
        }
        bestScore -= repeatPenalty
        bestComp["repeat_penalty"] = -repeatPenalty

        // ---- 全局特征7: FF后紧接冲击验证 (-25~10) ----
        val ffImp = if (hasFfPrecededImpact) {
            when {
                ffPrecededImpactCount >= 2 && maxImpNearFf > 2.8f -> 10f
                ffPrecededImpactCount >= 1 && maxImpNearFf > 2.5f -> 5f
                maxImpNearFf > 2.3f -> 2f
                else -> 0f
            }
        } else {
            if (ffRegions.isNotEmpty() && maxImpNearFf > 2.0f) -25f
            else if (ffRegions.isNotEmpty()) -20f
            else 0f
        }
        bestScore += ffImp
        bestComp["ff_preceded_impact"] = ffImp

        // ---- 全局特征8: 最大角速度 (0~15) ----
        val angVel = when {
            maxAngleDelta > 30f -> 15f
            maxAngleDelta > 20f -> 12f
            maxAngleDelta > 10f -> 8f
            maxAngleDelta > 5f  -> 4f
            maxAngleDelta > 2f  -> 1f
            else -> 0f
        }
        bestScore += angVel
        bestComp["maximum_ang_vel"] = angVel

        // ---- 全局特征9: 末尾静止+姿态 (-10~20) ----
        val tailStartIdx = (n * 0.65f).toInt()
        if (n - tailStartIdx >= 20) {
            val tailSvm = svm.subList(tailStartIdx, n)
            val tailAngles = angles.subList(tailStartIdx, n)
            val tailAvg = tailSvm.mean()
            val tailStd = tailSvm.pstdev()
            val tailAngleEnd = tailAngles.mean()
            val anglePersist = abs(tailAngleEnd - startAngle)

            val isStill = (tailAvg > 0.85f && tailAvg < 1.15f) && (tailStd < 0.2f)
            val isReoriented = anglePersist > 20f

            val endState = when {
                isStill && isReoriented  -> 12f
                isStill && anglePersist > 10f -> 8f
                isStill                   -> 3f
                tailStd < 0.3f           -> if (anglePersist > 20f) 3f else 0f
                else                      -> -15f
            }
            bestScore += endState
            bestComp["end_state"] = endState
        } else {
            bestComp["end_state"] = 0f
        }

        // ---- 全局特征10: SVM最大跳变 (0~15) ----
        val jolt = when {
            maxSvmDelta > 2.5f -> 15f
            maxSvmDelta > 2.0f -> 12f
            maxSvmDelta > 1.5f -> 8f
            maxSvmDelta > 1.0f -> 4f
            maxSvmDelta > 0.5f -> 1f
            else -> 0f
        }
        bestScore += jolt
        bestComp["maximum_jolt"] = jolt

        // ---- 全局特征11: 角度持续性 (-10~10) ----
        val localChange = bestComp["angle_change"] ?: 0f
        val angleSustain = if (localChange > 0f) {
            val eventAngleChange = abs(endAngle - startAngle)
            var highImpFactor = 1.0f
            when {
                highImpClusters >= 3 -> highImpFactor = 0.3f
                highImpClusters == 2 -> highImpFactor = 0.6f
            }
            when {
                localChange >= 8f && eventAngleChange > 20f ->
                    floor(5f * highImpFactor).toInt().toFloat()
                localChange >= 4f && eventAngleChange > 10f ->
                    floor(3f * highImpFactor).toInt().toFloat()
                localChange >= 8f && eventAngleChange < 10f ->
                    floor(-10f * maxOf(highImpFactor, 0.5f)).toInt().toFloat()
                localChange >= 4f && eventAngleChange < 5f ->
                    floor(-5f * maxOf(highImpFactor, 0.5f)).toInt().toFloat()
                else -> 0f
            }
        } else 0f
        bestScore += angleSustain
        bestComp["sustained_angle"] = angleSustain

        // ---- 全局特征12: 高冲击事件聚类数 (-15~5) ----
        val clusterPenalty = when {
            highImpClusters >= 4 -> -15f
            highImpClusters == 3 -> -10f
            highImpClusters == 2 -> -5f
            highImpClusters == 1 -> 5f
            else -> 0f
        }
        bestScore += clusterPenalty
        bestComp["impact_clusters"] = clusterPenalty

        // ---- 全局特征13: 高g值样本占比 (-15~0) ----
        val highGPenalty = when {
            highGRatio > 0.15f -> -15f
            highGRatio > 0.08f -> -10f
            highGRatio > 0.05f -> -5f
            highGRatio > 0.03f -> -2f
            else -> 0f
        }
        bestScore += highGPenalty
        bestComp["high_g_ratio_penalty"] = highGPenalty

        // ---- 最终总分 ----
        val finalScore = (bestScore * 10f).roundToInt() / 10f
        bestComp["total"] = finalScore

        return Quadruple(finalScore >= scoreThreshold, finalScore, bestComp)
    }
}

/**
 * 简单的四元组，避免 Kotlin stdlib 缺少 Quadruple 的问题
 */
data class Quadruple<A, B, C>(
    val first: A,
    val second: B,
    val third: C
)
