package com.example.asrdemo

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.asrdemo.databinding.ActivityMainBinding
import kotlinx.coroutines.*

/**
 * 主 Activity — 语音识别 Demo 界面
 *
 * 功能:
 * 1. 初始化 ASR 引擎
 * 2. 按下录音按钮 → 录一句话 → 显示识别结果
 * 3. 流式识别支持（边录边识别）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var asrEngine: AsrEngine

    private var isRecording = false
    private var recordThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 麦克风权限请求
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                toggleRecording()
            } else {
                Toast.makeText(this, R.string.permission_mic, Toast.LENGTH_LONG).show()
            }
        }

    // ===================== 生命周期 =====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        asrEngine = AsrEngine(this)

        // 初始化引擎（在后台线程）
        scope.launch {
            updateStatus("正在初始化引擎…")
            withContext(Dispatchers.IO) {
                val ok = asrEngine.initialize()
                if (!ok) {
                    withContext(Dispatchers.Main) {
                        val errorMsg = asrEngine.lastError ?: "未知错误"
                        updateStatus("初始化失败: $errorMsg")
                        Toast.makeText(this@MainActivity, "引擎初始化失败: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (asrEngine.isInitialized) {
                updateStatus("就绪，点击按钮开始录音")
            }
        }

        // 录音按钮
        binding.recordButton.setOnClickListener {
            if (!asrEngine.isInitialized) {
                Toast.makeText(this, "引擎尚未就绪", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkMicPermissionAndToggle()
        }

        // 清空按钮
        binding.clearButton.setOnClickListener {
            binding.resultText.text = getString(R.string.result_hint)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopRecording()
        asrEngine.release()
    }

    // ===================== 录音 =====================

    /** 检查权限后切换录音状态 */
    private fun checkMicPermissionAndToggle() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> {
                toggleRecording()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(this, R.string.permission_mic, Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    /** 启动录音（3秒固定时长一句话识别） */
    private fun startRecording() {
        isRecording = true
        binding.recordButton.text = getString(R.string.stop_record)
        binding.resultText.text = getString(R.string.status_recording)

        recordThread = Thread {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()

            // 采集 3 秒音频
            val totalSamples = sampleRate * 3  // 48000 个采样点 (3秒 @ 16kHz)
            val audioBuffer = ShortArray(totalSamples)
            var bytesRead = 0
            val readBuffer = ShortArray(bufferSize / 2)

            while (isRecording && bytesRead < totalSamples) {
                val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (read > 0) {
                    val remain = totalSamples - bytesRead
                    val toCopy = read.coerceAtMost(remain)
                    System.arraycopy(readBuffer, 0, audioBuffer, bytesRead, toCopy)
                    bytesRead += toCopy
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            if (bytesRead > 0) {
                // 截取实际读取的部分
                val actualAudio = if (bytesRead < totalSamples) {
                    audioBuffer.copyOf(bytesRead)
                } else audioBuffer

                runOnUiThread { updateStatus("识别中…") }

                // 执行识别
                var result = ""
                try {
                    result = asrEngine.recognize(actualAudio, sampleRate)
                    runOnUiThread {
                        binding.resultText.text = result.ifEmpty { "[未识别到语音]" }
                        binding.recordButton.text = getString(R.string.start_record)
                        updateStatus("就绪")
                        isRecording = false
                    }
                } catch (e: Exception) {
                    val errMsg = "识别异常: ${e.javaClass.simpleName}: ${e.message}"
                    android.util.Log.e("ASR", errMsg, e)
                    runOnUiThread {
                        binding.resultText.text = errMsg
                        binding.statusText.text = "错误"
                        binding.recordButton.text = getString(R.string.start_record)
                        isRecording = false
                        Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                runOnUiThread {
                    binding.resultText.text = "[录音失败]"
                    binding.recordButton.text = getString(R.string.start_record)
                    updateStatus("就绪")
                    isRecording = false
                }
            }
        }.apply { start() }
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        audioRecord = null
        binding.recordButton.text = getString(R.string.start_record)
        updateStatus("就绪")
    }

    // ===================== UI 辅助 =====================

    private fun updateStatus(text: String) {
        binding.statusText.text = text
    }
}
