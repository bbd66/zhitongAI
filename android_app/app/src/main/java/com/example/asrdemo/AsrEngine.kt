
package com.example.asrdemo

import ai.onnxruntime.*
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ## ASR 引擎 — 语音识别的核心入口
 *
 * 将 **Fbank 特征提取 → ONNX Runtime 推理 → CTC 解码** 组装为一条完整流水线。
 *
 * ### 典型用法（一句识别）
 *
 * ```kotlin
 * // 在 Activity / Fragment 中
 * val engine = AsrEngine(this)
 *
 * lifecycleScope.launch(Dispatchers.IO) {
 *     engine.initialize()
 *     val result = engine.recognize(yourPcmData, 16000)
 *     withContext(Dispatchers.Main) { textView.text = result }
 * }
 *
 * override fun onDestroy() {
 *     engine.release()
 *     super.onDestroy()
 * }
 * ```
 *
 * ### 典型用法（流式识别）
 *
 * ```kotlin
 * engine.startStreaming(8000,
 *     onPartial = { partial -> textView.text = partial },
 *     onFinal  = { final  -> sendMessage(final) }
 * )
 *
 * // 在 AudioRecord 的循环中:
 * while (isRecording) {
 *     val read = recorder.read(buffer, 0, buffer.size)
 *     engine.feedChunk(buffer.copyOf(read))
 * }
 *
 * engine.stopStreaming()
 * ```
 *
 * ### 依赖的 assets 文件
 *
 * 运行前需确保 `app/src/main/assets/` 下存在以下 3 个文件：
 * - **`model_quant.onnx`** — Paraformer 量化模型（~69MB）
 * - **`tokens.json`** — 词表文件（JSON 数组，约 9 万个 token）
 * - **`am.mvn`** — Kaldi NNet 格式的 CMVN 归一化参数
 *
 * 可通过项目中的 `setup_assets.bat` / `setup_assets.sh` 自动复制。
 *
 * @param context Android Context（用于访问 assets 和文件系统）
 */
class AsrEngine(private val context: Context) {

    companion object {
        /** Assets 中的 ONNX 模型文件名 */
        private const val MODEL_FILE = "model_quant.onnx"

        /** Assets 中的词表文件名 */
        private const val TOKENS_FILE = "tokens.json"

        /** Assets 中的 CMVN 参数文件名 */
        private const val CMVN_FILE = "am.mvn"

        /** Paraformer 模型的 predictor_bias 超参数，用于解码截断 */
        private const val PRED_BIAS = 1

        /** 模型内部采样率（所有输入音频最终会降采样到此值） */
        private const val TARGET_SR = 8000
    }

    // ---- 内部状态 ----
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var extractor: FbankExtractor? = null
    private var decoder: AsrDecoder? = null

    @Volatile private var streaming = false
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onFinalCallback: ((String) -> Unit)? = null

    // ---- 公开属性 ----

    /**
     * 引擎是否已完成初始化。
     *
     * 在调用 [initialize] 成功返回 `true` 后为 `true`；
     * [release] 后或初始化失败时为 `false`。
     *
     * 所有识别方法（[recognize]、[startStreaming]）都需要在初始化后才可调用。
     */
    var isInitialized: Boolean = false
        private set

    /**
     * 上一次初始化失败的错误信息。
     *
     * 如果初始化成功，此值为 `null`；
     * 如果初始化失败，可通过此属性获取具体异常信息用于 UI 展示或调试。
     */
    var lastError: String? = null
        private set

    /**
     * 是否正在流式识别中。
     *
     * 调用 [startStreaming] 后变为 `true`，[stopStreaming] 后恢复为 `false`。
     * 流式期间不可重复调用 [startStreaming]。
     */
    val isStreaming: Boolean get() = streaming

    /** 模型输入 "speech" 的名称（供 ORT 会话查找） */
    private val inputNameFeats = "speech"

    /** 模型输入 "speech_lengths" 的名称 */
    private val inputNameLen = "speech_lengths"

    // ========================================================================
    // 生命周期管理
    // ========================================================================

    /**
     * ## 初始化引擎
     *
     * 完成以下工作：
     * 1. 获取全局 ONNX Runtime 环境
     * 2. 从 app 的 **assets** 目录读取 `model_quant.onnx`，创建 ORT 会话
     * 3. 读取 `tokens.json` 加载词表
     * 4. 读取 `am.mvn` 加载 CMVN 参数
     *
     * 初始化 **不会** 写入内部存储，所有资源直接从 assets 内存加载。
     *
     * ### 线程安全
     * 建议在 **IO 线程** 中调用（协程 `Dispatchers.IO` 或 `AsyncTask`），
     * 因为模型加载涉及 ~69MB 文件读取和 ONNX 会话创建，耗时约 1~3 秒。
     *
     * ### 重复调用
     * 多次调用是安全的 —— 每次都会重新创建 ORT 会话（先释放旧会话）。
     *
     * @return `true` 表示初始化成功，[isInitialized] 变为 `true`；
     *         `false` 表示失败（模型文件缺失、格式错误等），[isInitialized] 仍为 `false`
     */
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

    /**
     * ## 释放引擎资源
     *
     * 关闭 ONNX Runtime 会话和环境，释放底层 native 内存。
     *
     * ### 何时调用
     * 在不需要再做语音识别时调用，例如：
     * - Activity 的 `onDestroy()` 中
     * - Fragment 的 `onDestroyView()` 中
     * - 整个应用退出时
     *
     * 释放后再次使用需要重新调用 [initialize]。
     */
    fun release() {
        ortSession?.close()
        ortEnv?.close()
        ortSession = null
        ortEnv = null
        isInitialized = false
    }

    // ========================================================================
    // 一句识别（离线模式）
    // ========================================================================

    /**
     * ## 一句识别
     *
     * 传入整段 PCM 音频，返回完整的识别文本。
     *
     * ### 处理流程
     * ```
     * PCM 音频 → 降采样到 8kHz → Fbank + LFR + CMVN 特征提取
     *     → ONNX Runtime 推理 → argmax 解码 → BPE 后处理 → 识别文本
     * ```
     *
     * ### 线程安全
     * 此方法可能耗时数百毫秒（取决于音频长度），建议在 **IO 线程**调用。
     * 如需直接从 UI 线程调用，确保音频不超过 3 秒（现代手机可 < 100ms）。
     *
     * ### 输入音频格式
     * - **采样率**: 任意（自动降采样到 8000Hz），推荐 8000Hz 或 16000Hz
     * - **位深**: 16-bit signed (PCM)
     * - **声道**: 单声道（仅使用第一声道逻辑，传入多声道需自行混音）
     * - **取值范围**: `[-32768, 32767]` 的原始 PCM 值（内部归一化到 `[-1, 1]`）
     *
     * @param pcmData 16-bit 线性 PCM 音频数据 (ShortArray)
     * @param sampleRate 输入音频的采样率（Hz），如 8000、16000、44100 等
     * @return 识别文本字符串。若音频过短或未识别到有效语音，返回空字符串 `""`
     *
     * @throws IllegalStateException 如果引擎尚未初始化（未调用 [initialize]）
     *
     * ### 示例
     * ```kotlin
     * // 从 AudioRecord 采集 3 秒 16kHz 音频
     * val buffer = ShortArray(16000 * 3)
     * recorder.read(buffer, 0, buffer.size)
     * val text = engine.recognize(buffer, 16000)
     * textView.text = text
     * ```
     */
    fun recognize(pcmData: ShortArray, sampleRate: Int): String {
        require(isInitialized) { "引擎未初始化，请先调用 initialize()" }

        return try {
            // 1. 降采样到 8kHz
            val audio8k = if (sampleRate != TARGET_SR) {
                resample(pcmData, sampleRate, TARGET_SR)
            } else pcmData

            // 2. 特征提取
            val (feats, featsLen) = extractor!!.extract(audio8k)
            if (featsLen < 1) return ""
            android.util.Log.d("ASR", "特征提取完成: featsLen=$featsLen, dim=${feats[0].size}")

            // 3. ONNX 推理
            val session = ortSession!!
            val env = ortEnv!!

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

            val lenBuf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            lenBuf.asIntBuffer().put(intArrayOf(featsLen))
            lenBuf.rewind()
            val featsLenTensor = OnnxTensor.createTensor(env, lenBuf, longArrayOf(1), OnnxJavaType.INT32)

            val result = session.run(
                mapOf(
                    inputNameFeats to featsTensor,
                    inputNameLen to featsLenTensor
                )
            )

            result.use { r ->
                val logitsTensor = r.get(0) as OnnxTensor
                val tokenNumTensor = r.get(1) as OnnxTensor

                val logitsShape = logitsTensor.info.shape
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

                decoder!!.decode(logits2d, tokenNumData[0])
            }
        } catch (e: Exception) {
            android.util.Log.e("ASR", "recognize 内部异常", e)
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            throw e
        }
    }

    // ========================================================================
    // 流式识别（实时模式）
    // ========================================================================

    /**
     * ## 启动流式识别
     *
     * 进入流式模式，后续通过 [feedChunk] 持续传入音频块，每块识别结果通过
     * `onPartial` 回调实时返回。
     *
     * ### 流式会话生命周期
     * ```
     * startStreaming() ──┬── feedChunk(block1) ──→ onPartial("结果1")
     *                    ├── feedChunk(block2) ──→ onPartial("结果2")
     *                    ├── ...
     *                    └── stopStreaming()  ──→ onFinal("")
     * ```
     *
     * ### 对比「一句识别」
     * - [recognize]：等待整段音频完成后返回一次最终结果 → 适合「按住说话」
     * - 流式模式：边录音边返回中间结果 → 适合「边说边显」
     *
     * ### 注意
     * - 流式模式内部每个块独立调用 [recognize]，**不维护跨块的上下文状态**
     *   （如需上下文感知，需自行实现音频拼接或引入 VAD）
     * - 建议每块音频时长 **1~3 秒**（采样率 8kHz 时为 8000~24000 点）
     *
     * @param sampleRate 输入音频的采样率，通常为 8000 或 16000
     * @param onPartial 每块音频识别完成后的 **部分结果** 回调（在引擎内部线程调用）
     * @param onFinal 流式结束时的 **最终结果** 回调（当前实现与 onPartial 无差异）
     *
     * @throws IllegalStateException 如果引擎未初始化或已在流式模式中
     *
     * ### 示例
     * ```kotlin
     * engine.startStreaming(
     *     sampleRate = 8000,
     *     onPartial = { text -> runOnUiThread { tv.text = text } },
     *     onFinal   = { text -> sendToServer(text) }
     * )
     * ```
     */
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

    /**
     * ## 喂入流式音频块
     *
     * 在流式模式下传入一段 PCM 音频进行识别。
     *
     * ### 调用时机
     * 必须在 [startStreaming] 之后、[stopStreaming] 之前调用。
     *
     * ### 输入格式
     * - 与 [recognize] 的 `pcmData` 格式完全相同（16-bit PCM）
     * - 采样率应与 [startStreaming] 传入的 `sampleRate` 一致
     * - 建议块大小: 1~3 秒 （太短会导致提取特征帧数不足，太长会增加延迟）
     *
     * @param pcmData 16-bit PCM 音频块
     *
     * @throws IllegalStateException 如果尚未进入流式模式
     */
    fun feedChunk(pcmData: ShortArray) {
        require(streaming) { "未启动流式识别" }

        val result = recognize(pcmData, TARGET_SR)
        if (result.isNotEmpty()) {
            onPartialCallback?.invoke(result)
        }
    }

    /**
     * ## 停止流式识别
     *
     * 退出流式模式，触发 [startStreaming] 传入的 `onFinal` 回调，
     * 并清空内部回调引用。
     *
     * ### 停止后
     * - [isStreaming] 变为 `false`
     * - 可再次调用 [startStreaming] 开始新一轮流式
     * - 引擎**不会**被释放，[recognize] 仍然可用
     *
     * @return 空字符串 `""`（当前实现中最终结果已在 `onFinal` 回调传递）
     */
    fun stopStreaming(): String {
        streaming = false
        onFinalCallback?.invoke("")
        onPartialCallback = null
        onFinalCallback = null
        return ""
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /**
     * 线性降采样（最近邻插值）。
     *
     * 将输入音频从 `origSr` 降采样到 `targetSr`。
     * 仅用于 [recognize] 内部，非公开 API。
     *
     * @param input 原始 PCM 数据
     * @param origSr 原始采样率
     * @param targetSr 目标采样率
     * @return 降采样后的 PCM 数据
     */
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
