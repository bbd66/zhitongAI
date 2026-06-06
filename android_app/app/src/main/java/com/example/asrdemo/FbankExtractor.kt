package com.example.asrdemo

import kotlin.math.*

/**
 * Fbank 特征提取器
 *
 * 完成:
 * 1. 预加重 (pre-emphasis)
 * 2. STFT → 功率谱 (256点FFT, Hamming窗)
 * 3. Mel三角滤波器组 (80个, 0~4000Hz)
 * 4. log
 * 5. LFR (Lookahead Frame Reduction, m=7, n=6) → 80→560维
 * 6. CMVN (从 am.mvn 解析 shift/scale)
 */
class FbankExtractor(cmvnText: String) {

    companion object {
        const val SAMPLE_RATE = 8000
        const val FRAME_LENGTH_MS = 25
        const val FRAME_SHIFT_MS = 10
        const val N_MELS = 80
        const val FFT_SIZE = 256
        const val LFR_M = 7
        const val LFR_N = 6
        const val PRE_EMPH_COEFF = 0.97f
        const val F_MIN = 0f
        val F_MAX = SAMPLE_RATE / 2f  // 4000 Hz
        const val FEAT_DIM = N_MELS * LFR_M  // 560
    }

    // 帧移/帧长（采样点）
    private val frameLength = SAMPLE_RATE * FRAME_LENGTH_MS / 1000  // 200
    private val frameShift = SAMPLE_RATE * FRAME_SHIFT_MS / 1000   // 80
    private val leftContext = (LFR_M - 1) / 2   // 3
    private val rightContext = (LFR_M - 1) / 2  // 3

    // CMVN 参数（从构造函数传入的文本中解析）
    private val cmvnShift: FloatArray
    private val cmvnScale: FloatArray

    init {
        val parsed = parseCmvn(cmvnText)
        cmvnShift = parsed.first
        cmvnScale = parsed.second
    }

    // 预计算：Hamming 窗 + Mel 滤波器组
    private val hammingWindow = FloatArray(frameLength) { i ->
        (0.54 - 0.46 * cos(2 * PI * i / (frameLength - 1))).toFloat()
    }

    private val melFilterbank: Array<FloatArray> = run {
        val nFreqBins = FFT_SIZE / 2 + 1  // 129
        val melMin = hzToMel(F_MIN)
        val melMax = hzToMel(F_MAX)
        val melPoints = FloatArray(N_MELS + 2) { i ->
            melMin + (melMax - melMin) * i / (N_MELS + 1)
        }
        val binPoints = melPoints.map { melToHz(it) * FFT_SIZE.toFloat() / SAMPLE_RATE }

        Array(N_MELS) { m ->
            FloatArray(nFreqBins).also { fb ->
                val fL = binPoints[m]
                val fC = binPoints[m + 1]
                val fR = binPoints[m + 2]
                for (b in 0 until nFreqBins) {
                    val bf = b.toFloat()
                    fb[b] = when {
                        bf < fL || bf > fR -> 0f
                        bf <= fC -> (bf - fL) / (fC - fL)
                        else -> (fR - bf) / (fR - fC)
                    }
                }
            }
        }
    }

    // ---------- 工具函数 ----------

    private fun hzToMel(hz: Float): Float =
        2595f * log10((1 + hz / 700f).toDouble()).toFloat()

    private fun melToHz(mel: Float): Float =
        700f * (10.0.pow((mel / 2595f).toDouble()).toFloat() - 1f)

    /** 解析 CMVN (从 Kaldi NNet 格式的 am.mvn 文本) */
    private fun parseCmvn(cmvnText: String): Pair<FloatArray, FloatArray> {
        val shiftMatch =
            Regex("""<AddShift>\s+\d+\s+\d+\s*<LearnRateCoef>\s+0\s*\[([^\]]+)\]""", RegexOption.DOT_MATCHES_ALL)
                .find(cmvnText)
        val scaleMatch =
            Regex("""<Rescale>\s+\d+\s+\d+\s*<LearnRateCoef>\s+0\s*\[([^\]]+)\]""", RegexOption.DOT_MATCHES_ALL)
                .find(cmvnText)

        val shift = shiftMatch?.groupValues?.get(1)
            ?.trim()?.split(Regex("\\s+"))?.map { it.toFloat() }?.toFloatArray()
            ?: error("无法解析 am.mvn: AddShift 未找到")
        val scale = scaleMatch?.groupValues?.get(1)
            ?.trim()?.split(Regex("\\s+"))?.map { it.toFloat() }?.toFloatArray()
            ?: error("无法解析 am.mvn: Rescale 未找到")

        require(shift.size == FEAT_DIM) { "CMVN shift 维度错误: ${shift.size}, 期望 $FEAT_DIM" }
        require(scale.size == FEAT_DIM) { "CMVN scale 维度错误: ${scale.size}, 期望 $FEAT_DIM" }
        return Pair(shift, scale)
    }

    // ---------- Radix-2 DIT FFT ----------

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n > 0 && n and (n - 1) == 0) { "FFT size must be power of 2" }

        // Bit-reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = real[i]; real[i] = real[j]; real[j] = t
                t = imag[i]; imag[i] = imag[j]; imag[j] = t
            }
        }

        // Butterfly
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len
            val wLenR = cos(ang).toFloat()
            val wLenI = sin(ang).toFloat()
            for (i in 0 until n step len) {
                var wR = 1f
                var wI = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + half] * wR - imag[i + k + half] * wI
                    val vI = real[i + k + half] * wI + imag[i + k + half] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + half] = uR - vR
                    imag[i + k + half] = uI - vI
                    val tR = wR * wLenR - wI * wLenI
                    val tI = wR * wLenI + wI * wLenR
                    wR = tR
                    wI = tI
                }
            }
            len = len shl 1
        }
    }

    // ---------- 主提取函数 ----------

    /**
     * 从 PCM 数据提取特征
     * @param pcmData 输入 PCM (16-bit, 8000Hz, mono)
     * @return (特征数组 [T_lfr, 560], 有效长度 T_lfr)
     */
    fun extract(pcmData: ShortArray): Pair<Array<FloatArray>, Int> {
        // 1. 归一化到 [-1, 1]
        val audio = FloatArray(pcmData.size) { pcmData[it].toFloat() / Short.MAX_VALUE }

        // 2. 预加重: y[t] = x[t] - 0.97 * x[t-1]
        for (i in audio.size - 1 downTo 1) {
            audio[i] = audio[i] - PRE_EMPH_COEFF * audio[i - 1]
        }

        // 3. STFT → 功率谱
        val nFrames = max(0, (audio.size - frameLength) / frameShift + 1)
        if (nFrames < 1) return Pair(emptyArray(), 0)

        val nFreqBins = FFT_SIZE / 2 + 1  // 129
        val powerSpectra = Array(nFrames) { FloatArray(nFreqBins) }

        for (t in 0 until nFrames) {
            val offset = t * frameShift
            // 加窗
            val real = FloatArray(FFT_SIZE) { i ->
                if (i < frameLength) audio[offset + i] * hammingWindow[i] else 0f
            }
            val imag = FloatArray(FFT_SIZE)

            fft(real, imag)

            // 功率谱: |FFT|^2
            // 只取前 nFreqBins 个 (对称)
            for (k in 0 until nFreqBins) {
                powerSpectra[t][k] = real[k] * real[k] + imag[k] * imag[k]
            }
        }

        // 4. Mel 滤波 + log
        val melFeats = Array(nFrames) { FloatArray(N_MELS) }
        for (t in 0 until nFrames) {
            for (m in 0 until N_MELS) {
                var sum = 0f
                for (k in 0 until nFreqBins) {
                    sum += powerSpectra[t][k] * melFilterbank[m][k]
                }
                melFeats[t][m] = ln(max(sum, 1e-10f).toDouble()).toFloat()
            }
        }

        // 5. LFR: 帧拼接 + 下采样
        val tLfr = ceil(nFrames.toDouble() / LFR_N).toInt()
        val lfrFeats = Array(tLfr) { FloatArray(FEAT_DIM) }
        for (i in 0 until tLfr) {
            val center = i * LFR_N
            for (j in 0 until LFR_M) {
                val srcIdx = (center - leftContext + j).coerceIn(0, nFrames - 1)
                System.arraycopy(melFeats[srcIdx], 0, lfrFeats[i], j * N_MELS, N_MELS)
            }
        }

        // 6. CMVN: feat = (feat + shift) * scale
        for (i in 0 until tLfr) {
            for (j in 0 until FEAT_DIM) {
                lfrFeats[i][j] =
                    (lfrFeats[i][j] + cmvnShift[j]) * cmvnScale[j]
            }
        }

        return Pair(lfrFeats, tLfr)
    }
}
