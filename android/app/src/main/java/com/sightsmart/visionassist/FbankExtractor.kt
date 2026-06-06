package com.sightsmart.visionassist

import kotlin.math.*

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
        val F_MAX = SAMPLE_RATE / 2f
        const val FEAT_DIM = N_MELS * LFR_M
    }

    private val frameLength = SAMPLE_RATE * FRAME_LENGTH_MS / 1000
    private val frameShift = SAMPLE_RATE * FRAME_SHIFT_MS / 1000
    private val leftContext = (LFR_M - 1) / 2
    private val rightContext = (LFR_M - 1) / 2

    private val cmvnShift: FloatArray
    private val cmvnScale: FloatArray

    init {
        val parsed = parseCmvn(cmvnText)
        cmvnShift = parsed.first
        cmvnScale = parsed.second
    }

    private val hammingWindow = FloatArray(frameLength) { i ->
        (0.54 - 0.46 * cos(2 * PI * i / (frameLength - 1))).toFloat()
    }

    private val melFilterbank: Array<FloatArray> = run {
        val nFreqBins = FFT_SIZE / 2 + 1
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

    private fun hzToMel(hz: Float): Float =
        2595f * log10((1 + hz / 700f).toDouble()).toFloat()

    private fun melToHz(mel: Float): Float =
        700f * (10.0.pow((mel / 2595f).toDouble()).toFloat() - 1f)

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

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n > 0 && n and (n - 1) == 0) { "FFT size must be power of 2" }

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

    fun extract(pcmData: ShortArray): Pair<Array<FloatArray>, Int> {
        val audio = FloatArray(pcmData.size) { pcmData[it].toFloat() / Short.MAX_VALUE }

        for (i in audio.size - 1 downTo 1) {
            audio[i] = audio[i] - PRE_EMPH_COEFF * audio[i - 1]
        }

        val nFrames = max(0, (audio.size - frameLength) / frameShift + 1)
        if (nFrames < 1) return Pair(emptyArray(), 0)

        val nFreqBins = FFT_SIZE / 2 + 1
        val powerSpectra = Array(nFrames) { FloatArray(nFreqBins) }

        for (t in 0 until nFrames) {
            val offset = t * frameShift
            val real = FloatArray(FFT_SIZE) { i ->
                if (i < frameLength) audio[offset + i] * hammingWindow[i] else 0f
            }
            val imag = FloatArray(FFT_SIZE)

            fft(real, imag)

            for (k in 0 until nFreqBins) {
                powerSpectra[t][k] = real[k] * real[k] + imag[k] * imag[k]
            }
        }

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

        val tLfr = ceil(nFrames.toDouble() / LFR_N).toInt()
        val lfrFeats = Array(tLfr) { FloatArray(FEAT_DIM) }
        for (i in 0 until tLfr) {
            val center = i * LFR_N
            for (j in 0 until LFR_M) {
                val srcIdx = (center - leftContext + j).coerceIn(0, nFrames - 1)
                System.arraycopy(melFeats[srcIdx], 0, lfrFeats[i], j * N_MELS, N_MELS)
            }
        }

        for (i in 0 until tLfr) {
            for (j in 0 until FEAT_DIM) {
                lfrFeats[i][j] = (lfrFeats[i][j] + cmvnShift[j]) * cmvnScale[j]
            }
        }

        return Pair(lfrFeats, tLfr)
    }
}
