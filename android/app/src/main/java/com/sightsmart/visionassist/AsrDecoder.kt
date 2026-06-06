package com.sightsmart.visionassist

import org.json.JSONArray

class AsrDecoder(tokensJson: String, private val predictorBias: Int = 1) {

    private val tokenList: List<String> = run {
        val arr = JSONArray(tokensJson)
        (0 until arr.length()).map { arr.getString(it) }
    }

    val vocabSize: Int get() = tokenList.size

    fun decode(logits: Array<FloatArray>, tokenNum: Int): String {
        require(tokenList.isNotEmpty()) { "词汇表为空" }

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

        val yseq = listOf(1) + tokenIds + listOf(2)
        val filtered = yseq.subList(1, yseq.size - 1)
            .filter { it != 0 && it != 2 }
            .take(maxOf(0, tokenNum - predictorBias))

        val rawTokens = filtered
            .filter { it in tokenList.indices }
            .map { tokenList[it] }

        return sentencePostprocess(rawTokens)
    }

    private fun sentencePostprocess(tokens: List<String>): String {
        val merged = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.endsWith("@@")) {
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

        return buildString {
            for ((idx, token) in merged.withIndex()) {
                if (idx > 0) {
                    val prev = merged[idx - 1]
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

    private fun isCjkChar(c: Char): Boolean {
        val code = c.code
        return (code in 0x4E00..0x9FFF) ||
                (code in 0x3400..0x4DBF) ||
                (code in 0x20000..0x2A6DF) ||
                (code in 0x2A700..0x2B73F) ||
                (code in 0x2B740..0x2B81F) ||
                (code in 0x2B820..0x2CEAF) ||
                (code in 0xF900..0xFAFF) ||
                (code in 0x2F800..0x2FA1F)
    }
}
