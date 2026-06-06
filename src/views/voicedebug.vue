<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] bg-black text-white min-h-screen relative flex flex-col items-center justify-center p-6">
      <h1 class="font-lexend font-black uppercase text-3xl text-white mb-4">语音功能诊断</h1>

      <div class="w-full space-y-4">
        <div class="bg-zinc-800 p-4 rounded-lg">
          <h3 class="font-bold mb-2">1. 语音合成 (TTS)</h3>
          <p class="text-sm">支持状态: <span :class="status.ttsSupported ? 'text-green-400' : 'text-red-400'">
            {{ status.ttsSupported ? '✓ 支持' : '✗ 不支持' }}
          </span></p>
          <button @click="testSpeak" class="mt-2 px-4 py-2 bg-blue-600 rounded text-white">测试播报</button>
        </div>

        <div class="bg-zinc-800 p-4 rounded-lg">
          <h3 class="font-bold mb-2">2. 语音识别 (STT)</h3>
          <p class="text-sm">支持状态: <span :class="status.sttSupported ? 'text-green-400' : 'text-red-400'">
            {{ status.sttSupported ? '✓ 支持' : '✗ 不支持' }}
          </span></p>
          <p class="text-sm">识别中: <span :class="status.isListening ? 'text-green-400' : 'text-gray-400'">
            {{ status.isListening ? '✓ 是' : '否' }}
          </span></p>
          <p class="text-sm text-red-400" v-if="status.error">{{ status.error }}</p>
          <div class="flex gap-2 mt-2">
            <button @click="startRecognition" :disabled="!status.sttSupported || status.isListening"
                    class="px-4 py-2 bg-green-600 rounded text-white disabled:bg-gray-600">
              开始识别
            </button>
            <button @click="stopRecognition" v-if="status.isListening" class="px-4 py-2 bg-red-600 rounded text-white">
              停止
            </button>
          </div>
        </div>

        <div v-if="status.lastTranscript" class="bg-zinc-800 p-4 rounded-lg">
          <h3 class="font-bold mb-2">识别结果</h3>
          <p class="text-xl text-yellow-400">{{ status.lastTranscript }}</p>
        </div>

        <div class="bg-zinc-800 p-4 rounded-lg">
          <h3 class="font-bold mb-2">诊断日志</h3>
          <div class="text-xs text-gray-400 max-h-40 overflow-y-auto font-mono">
            <p v-for="(log, i) in logs" :key="i">{{ log }}</p>
          </div>
        </div>

        <div class="bg-blue-900/30 p-4 rounded-lg border border-blue-600">
          <h3 class="font-bold mb-2 text-blue-400">ℹ️ 说明</h3>
          <p class="text-sm text-gray-300">
            本应用使用端侧原生 ONNX 模型进行完全离线语音识别。
          </p>
          <p class="text-sm text-gray-300 mt-2">
            语音合成使用 Android 系统 TTS 引擎，无需网络连接。
          </p>
        </div>

        <div class="bg-green-900/30 p-4 rounded-lg border border-green-600">
          <h3 class="font-bold mb-2 text-green-400">🎯 使用指南</h3>
          <div class="text-sm text-gray-300 space-y-1">
            <p><strong>1. 点击"开始识别"按钮</strong></p>
            <p class="text-gray-400">→ 系统会播报: "已开启语音识别，请说话"</p>
            <p class="text-gray-400">→ 等待 1.5 秒后开始录音</p>
            <p class="text-gray-400">→ 底部显示绿色"🎤 语音识别已开启"</p>
            <p class="text-gray-400 mt-2"><strong>2. 说出语音指令</strong></p>
            <p class="text-gray-400">→ 系统实时识别并显示结果</p>
            <p class="text-gray-400">→ 系统会播报: "识别到: xxx"</p>
            <p class="text-gray-400 mt-2"><strong>3. 支持的语音命令</strong></p>
            <p class="text-gray-400">• <span class="text-yellow-400">"现在是什么页面"</span> - 播报当前所在页面</p>
            <p class="text-gray-400">• "当前页面" - 播报当前所在页面</p>
            <p class="text-gray-400">• "我在哪里" - 播报当前所在页面</p>
            <p class="text-gray-400 mt-1">• "进入相机" - 跳转到相机拍摄页</p>
            <p class="text-gray-400">• "进入首页" - 跳转到用户主页</p>
            <p class="text-gray-400">• "进入模式选择" - 跳转到模式选择页</p>
            <p class="text-gray-400">• "进入个人中心" - 跳转到个人中心页</p>
            <p class="text-gray-400">• "进入紧急处理" - 跳转到紧急处理页</p>
            <p class="text-gray-400">• "进入监护人" - 跳转到监护人主页</p>
            <p class="text-gray-400">• "进入紧急求助" - 跳转到紧急求助页</p>
            <p class="text-gray-400 mt-1">• "音量调大" / "音量调小" - 调节语音音量</p>
            <p class="text-gray-400">• "亮度调亮" / "亮度调暗" - 调节屏幕亮度</p>
            <p class="text-gray-400">• "关闭语音" - 停止语音识别</p>
            <p class="text-gray-400">• "停止语音" - 停止语音识别</p>
            <p class="text-gray-400 mt-2"><strong>4. 示例用法</strong></p>
            <p class="text-gray-400">→ 说"现在是什么页面" → 系统播报:"当前页面是语音功能诊断"</p>
            <p class="text-gray-400">→ 说"进入相机" → 系统播报:"正在进入相机拍摄页"并跳转</p>
            <p class="text-gray-400">→ 说"音量调大" → 系统播报:"音量已增大"</p>
            <p class="text-gray-400">→ 说"亮度调暗" → 系统播报:"请在系统设置中调节亮度"</p>
          </div>
        </div>
      </div>

      <button @click="goBack" class="mt-6 px-6 py-3 bg-gray-600 rounded text-white">返回</button>
    </div>
  </div>
</template>

<script>
import { NativeASR } from '../capacitor/plugins/NativeASR/NativeASR.js'
import { NativeTTS } from '../capacitor/plugins/NativeTTS/NativeTTS.js'

export default {
  name: 'VoiceDebugPage',
  data() {
    return {
      status: {
        ttsSupported: false,
        sttSupported: false,
        isListening: false,
        error: null,
        lastTranscript: ''
      },
      logs: [],
      nativeASRAvailable: false,
      nativeTTSAvailable: false
    }
  },
  mounted() {
    this.runDiagnostics()
  },
  beforeUnmount() {
    this.stopRecognition()
  },
  methods: {
    log(msg) {
      const time = new Date().toLocaleTimeString()
      this.logs.unshift(`[${time}] ${msg}`)
      if (this.logs.length > 50) this.logs.pop()
    },
    async runDiagnostics() {
      this.log('开始诊断...')

      await this.checkNativePlugins()

      if (this.nativeTTSAvailable) {
        this.status.ttsSupported = true
        this.log('TTS支持: ✓ (原生Android TTS)')
      } else {
        this.status.ttsSupported = false
        this.log('TTS支持: ✗ (原生插件不可用)')
      }

      if (this.nativeASRAvailable) {
        this.status.sttSupported = true
        this.log('STT支持: ✓ (原生ONNX模型)')
      } else {
        this.status.sttSupported = false
        this.log('STT支持: ✗ (原生插件不可用)')
      }

      this.log('诊断完成')
    },
    async checkNativePlugins() {
      try {
        const asrInfo = await NativeASR.isInitialized()
        if (asrInfo.value) {
          this.nativeASRAvailable = true
        } else {
          const result = await NativeASR.initialize()
          if (result.success) {
            this.nativeASRAvailable = true
          }
        }
      } catch (e) {
        console.log('Native ASR not available:', e)
        this.nativeASRAvailable = false
      }

      try {
        const ttsResult = await NativeTTS.isSupported()
        if (ttsResult.value) {
          this.nativeTTSAvailable = true
        }
      } catch (e) {
        console.log('Native TTS not available:', e)
        this.nativeTTSAvailable = false
      }
    },
    async testSpeak() {
      this.log('测试语音播报...')
      
      try {
        if (this.nativeTTSAvailable) {
          await NativeTTS.speak({
            text: '语音功能测试成功，如果你能听到这段话，说明语音播报正常工作。',
            lang: 'zh-CN',
            rate: 1.0
          })
          this.log('✓ 原生语音播报已启动')
        } else {
          this.log('✗ 语音播报不可用，请安装原生应用')
          this.status.error = '语音播报不可用，请安装原生应用'
        }
      } catch (error) {
        console.error('语音播报失败:', error)
        this.log(`✗ 语音播报失败: ${error.message}`)
        this.status.error = `语音播报失败: ${error.message}`
      }
    },
    async startRecognition() {
      if (!this.status.sttSupported || this.status.isListening) return

      this.log('请求麦克风权限...')

      try {
        const result = await NativeASR.startListening()
        if (result.success) {
          this.status.isListening = true
          this.status.error = null
          this.log('✓ 原生ONNX语音识别已启动，请说话!')
          
          NativeASR.addListener('onResult', (event) => {
            console.log('识别结果:', event.text)
            this.log(`识别结果: "${event.text}"`)
            this.status.lastTranscript = event.text
            // 注意：这里不进行语音播报，只在日志中显示
          })

          // 注意：启动时也不进行语音播报，只记录日志
          this.log('已开启语音识别，请说话')
        } else {
          this.status.error = '启动原生语音识别失败'
          this.log('✗ 启动原生语音识别失败')
        }
      } catch (error) {
        console.error('启动识别失败:', error)
        this.status.error = error.message.includes('权限') 
          ? '麦克风权限被拒绝，请在设置中允许麦克风访问' 
          : `启动失败: ${error.message}`
        
        this.log(`✗ ${this.status.error}`)
        // 注意：错误也不进行语音播报，只记录日志
      }
    },
    async stopRecognition() {
      if (!this.status.isListening) return

      this.log('停止识别...')

      await NativeASR.stopListening()

      this.status.isListening = false
      this.log('✓ 已停止')
      // 注意：停止时也不进行语音播报，只记录日志
    },
    async speakText(text) {
      try {
        if (this.nativeTTSAvailable) {
          await NativeTTS.speak({
            text: text,
            lang: 'zh-CN',
            rate: 1.0
          })
        }
      } catch (e) {
        console.error('语音播报失败:', e)
      }
    },
    goBack() {
      this.$router.back()
    }
  }
}
</script>
