package com.example.asrdemo

import org.json.JSONArray

/**
 * 解码器
 *
 * 完成:
 * 1. 加载 tokens.json 词汇表
 * 2. argmax 取最大概率 token id
 * 3. 过滤特殊 token: blank(0), sos(1), eos(2)
 * 4. id → token 文字映射
 * 5. BPE 子词后处理 (如 "gu@@")
 */
class AsrDecoder(tokensJson: String, private val predictorBias: Int = 1) {

    private val tokenList: List<String> = run {
        val arr = JSONArray(tokensJson)
        (0 until arr.length()).map { arr.getString(it) }
    }

    val vocabSize: Int get() = tokenList.size

    /**
     * 解码
     * @param logits 模型输出 logits, shape [T, vocab_size]
     * @param tokenNum 模型输出的预测 token 数 (token_num[0])
     * @return 识别文本
     */
    fun decode(logits: Array<FloatArray>, tokenNum: Int): String {
        require(tokenList.isNotEmpty()) { "词汇表为空" }

        // 1. argmax: 对每个时间步取最大概率的 token id
        val tokenIds = logits.map { row ->
            var maxIdx = 0
            var maxVal = row[0]
            for (i in 1 until row.size) {
                if (row[i] > maxVal) {
                    maxVal = row[i]
                    maxIdx = i
                }
            }
            maxIdx
        }

        // 2. 添加 sos(1) / eos(2)，然后移除空白符
        // Python 端: yseq = [1] + argmax.tolist() + [2]; token_int = yseq[1:-1] 然后过滤
        val yseq = listOf(1) + tokenIds + listOf(2)
        val filtered = yseq.subList(1, yseq.size - 1)
            .filter { it != 0 && it != 2 }
            .take(maxOf(0, tokenNum - predictorBias))

        // 3. id → 文字 + 后处理（合并 BPE 子词、去除中文间的空格）
        //    与 FunASR sentence_postprocess 逻辑一致
        val rawTokens = filtered
            .filter { it in tokenList.indices }
            .map { tokenList[it] }

        return sentencePostprocess(rawTokens)
    }

    /**
     * 后处理：合并 BPE 子词 (以 @@ 结尾)，中文不添加空格
     */
    private fun sentencePostprocess(tokens: List<String>): String {
        val merged = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.endsWith("@@")) {
                // BPE 子词，去掉 @@ 并与后续非 @@ 合并
                val sb = StringBuilder(token.dropLast(2))
                i++
                while (i < tokens.size && tokens[i].endsWith("@@")) {
                    sb.append(tokens[i].dropLast(2))
                    i++
                }
                if (i < tokens.size) {
                    sb.append(tokens[i])
                    i++
                }
                merged.add(sb.toString())
            } else {
                merged.add(token)
                i++
            }
        }

        // 拼接：中文单字直接相连，其他用空格分隔
        return buildString {
            for ((idx, token) in merged.withIndex()) {
                if (idx > 0) {
                    val prev = merged[idx - 1]
                    // 中文单字之间不加空格；非中文之间加空格
                    val curIsCjk = token.length == 1 && isCjkChar(token[0])
                    val prevIsCjk = prev.length == 1 && isCjkChar(prev[0])
                    if (!(curIsCjk && prevIsCjk)) {
                        append(" ")
                    }
                }
                append(token)
            }
        }
    }

    /** 判断是否为 CJK 统一表意文字（中/日/韩） */
    private fun isCjkChar(c: Char): Boolean {
        val code = c.code
        return (code in 0x4E00..0x9FFF) ||    // CJK Unified
                (code in 0x3400..0x4DBF) ||   // CJK Extension A
                (code in 0x20000..0x2A6DF) || // CJK Extension B
                (code in 0x2A700..0x2B73F) || // CJK Extension C
                (code in 0x2B740..0x2B81F) || // CJK Extension D
                (code in 0x2B820..0x2CEAF) || // CJK Extension E
                (code in 0xF900..0xFAFF) ||   // CJK Compatibility
                (code in 0x2F800..0x2FA1F)    // CJK Compatibility Supplement
    }
}
