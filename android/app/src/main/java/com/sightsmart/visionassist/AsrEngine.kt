package com.sightsmart.visionassist

import ai.onnxruntime.*
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AsrEngine(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "model_quant.onnx"
        private const val TOKENS_FILE = "tokens.json"
        private const val CMVN_FILE = "am.mvn"
        private const val PRED_BIAS = 1
        private const val TARGET_SR = 8000
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var extractor: FbankExtractor? = null
    private var decoder: AsrDecoder? = null

    @Volatile private var streaming = false
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onFinalCallback: ((String) -> Unit)? = null

    var isInitialized: Boolean = false
        private set

    var lastError: String? = null
        private set

    val isStreaming: Boolean get() = streaming

    private val inputNameFeats = "speech"
    private val inputNameLen = "speech_lengths"

    fun initialize(): Boolean = try {
        ortEnv = OrtEnvironment.getEnvironment()

        val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }

        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        ortSession = ortEnv!!.createSession(modelBytes, opts)

        val tokensJson = context.assets.open(TOKENS_FILE).use { it.bufferedReader().readText() }
        decoder = AsrDecoder(tokensJson, PRED_BIAS)

        extractor = FbankExtractor(
            context.assets.open(CMVN_FILE).use { it.bufferedReader().readText() }
        )

        isInitialized = true
        true
    } catch (e: Exception) {
        e.printStackTrace()
        lastError = "${e.javaClass.simpleName}: ${e.message ?: "未知错误"}"
        isInitialized = false
        false
    }

    fun release() {
        ortSession?.close()
        ortEnv?.close()
        ortSession = null
        ortEnv = null
        isInitialized = false
    }

    fun recognize(pcmData: ShortArray, sampleRate: Int): String {
        if (!isInitialized) {
            lastError = "引擎未初始化，请先调用 initialize()"
            return ""
        }

        return try {
            val audio8k = if (sampleRate != TARGET_SR) {
                resample(pcmData, sampleRate, TARGET_SR)
            } else pcmData

            if (audio8k.isEmpty()) {
                return ""
            }

            val extractor = this.extractor
            if (extractor == null) {
                lastError = "特征提取器未初始化"
                return ""
            }

            val (feats, featsLen) = extractor.extract(audio8k)
            if (featsLen < 1) {
                return ""
            }

            val session = ortSession
            val env = ortEnv
            if (session == null || env == null) {
                lastError = "ONNX会话未初始化"
                return ""
            }

            val decoder = this.decoder
            if (decoder == null) {
                lastError = "解码器未初始化"
                return ""
            }

            val featDim = FbankExtractor.FEAT_DIM
            val flatFeats = FloatArray(featsLen * featDim) { idx ->
                feats[idx / featDim][idx % featDim]
            }
            val shape = longArrayOf(1L, featsLen.toLong(), featDim.toLong())

            val featsBuf = ByteBuffer.allocateDirect(flatFeats.size * 4)
                .order(ByteOrder.nativeOrder())
            featsBuf.asFloatBuffer().put(flatFeats)
            featsBuf.rewind()
            
            val featsTensor = OnnxTensor.createTensor(env, featsBuf, shape, OnnxJavaType.FLOAT)
                ?: run {
                    lastError = "无法创建特征张量"
                    return ""
                }

            val lenBuf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            lenBuf.asIntBuffer().put(intArrayOf(featsLen))
            lenBuf.rewind()
            
            val featsLenTensor = OnnxTensor.createTensor(env, lenBuf, longArrayOf(1), OnnxJavaType.INT32)
                ?: run {
                    featsTensor.close()
                    lastError = "无法创建长度张量"
                    return ""
                }

            val result = session.run(
                mapOf(
                    inputNameFeats to featsTensor,
                    inputNameLen to featsLenTensor
                )
            )

            result.use { r ->
                val logitsTensor = r.get(0) as? OnnxTensor
                val tokenNumTensor = r.get(1) as? OnnxTensor

                if (logitsTensor == null || tokenNumTensor == null) {
                    lastError = "模型输出为空"
                    return ""
                }

                val logitsShape = logitsTensor.info.shape
                if (logitsShape.size < 3) {
                    lastError = "模型输出形状不正确"
                    return ""
                }

                val tOut = logitsShape[1].toInt()
                val vocabSize = logitsShape[2].toInt()

                val logitsData = FloatArray(tOut * vocabSize)
                logitsTensor.floatBuffer.get(logitsData)

                val logits2d = Array(tOut) { t ->
                    FloatArray(vocabSize) { v ->
                        logitsData[t * vocabSize + v]
                    }
                }

                val tokenNumData = IntArray(1)
                tokenNumTensor.intBuffer.get(tokenNumData)

                decoder.decode(logits2d, tokenNumData[0])
            }
        } catch (e: Exception) {
            android.util.Log.e("ASR", "recognize 内部异常", e)
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            ""
        }
    }

    fun startStreaming(
        sampleRate: Int,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit
    ) {
        require(isInitialized) { "引擎未初始化" }
        require(!streaming) { "已经在流式识别中" }

        streaming = true
        onPartialCallback = onPartial
        onFinalCallback = onFinal
    }

    fun feedChunk(pcmData: ShortArray) {
        require(streaming) { "未启动流式识别" }

        val result = recognize(pcmData, TARGET_SR)
        if (result.isNotEmpty()) {
            onPartialCallback?.invoke(result)
        }
    }

    fun stopStreaming(): String {
        streaming = false
        onFinalCallback?.invoke("")
        onPartialCallback = null
        onFinalCallback = null
        return ""
    }

    private fun resample(input: ShortArray, origSr: Int, targetSr: Int): ShortArray {
        if (origSr == targetSr) return input
        val ratio = targetSr.toDouble() / origSr
        val outLen = (input.size * ratio).toInt()
        if (outLen < 1) return ShortArray(0)

        val output = ShortArray(outLen)
        for (i in 0 until outLen) {
            val srcIdx = (i / ratio).toInt().coerceIn(0, input.size - 1)
            output[i] = input[srcIdx]
        }
        return output
    }
}
