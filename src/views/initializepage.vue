<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] font-lexend min-h-screen relative" @click="handlePageClick">
      <header class="fixed top-0 left-1/2 -translate-x-1/2 w-[414px] h-24 bg-black flex justify-between items-center px-6 border-b-4 border-white z-40">
        <div class="flex items-center">
          <h1 class="text-white font-lexend font-bold text-3xl uppercase tracking-tighter">智瞳</h1>
        </div>
        <span class="material-symbols-outlined text-white" style="font-size: 32px;">star</span>
      </header>

      <main class="pt-24 pb-8 px-6 flex flex-col items-center w-full bg-white min-h-screen overflow-y-auto">
        <h2 class="font-headline-lg text-black text-center mb-8 px-4"></h2>

        <router-link to="/selectmodle" class="w-[300px] h-[300px] rounded-full deep-blue-btn flex items-center justify-center brutal-border active:scale-95 transition-transform" @click.stop="handleStartClick">
          <span class="material-symbols-outlined text-white" style="font-size: 140px; font-variation-settings: 'FILL' 1;">touch_app</span>
        </router-link>

        <div class="w-[382px] h-32 bg-zinc-400 border-4 border-zinc-600 rounded-xl flex items-center justify-center mt-8">
          <p class="font-headline-md text-white tracking-widest text-center">震动反馈教学区</p>
        </div>

        <!-- 点击提示 -->
        <div v-if="!hasInteracted" class="mt-8 text-center">
          <p class="text-zinc-600 text-lg">请点击页面任意位置开始</p>
        </div>
      </main>
    </div>
  </div>
</template>

<script>
import voiceInteraction from '../utils/voiceInteraction.js'

export default {
  name: 'initializepage',
  data() {
    return {
      hasInteracted: false
    }
  },
  async mounted() {
    voiceInteraction.init(
      (command) => {
        console.log('语音命令:', command)
      },
      (route) => {
        this.$router.push(route)
      }
    )
  },
  methods: {
    async handlePageClick() {
      if (!this.hasInteracted) {
        this.hasInteracted = true
        await this.initVoiceInteraction()
      }
    },

    async handleStartClick() {
      if (!this.hasInteracted) {
        this.hasInteracted = true
        await this.initVoiceInteraction()
      }
    },

    async initVoiceInteraction() {
      try {
        await voiceInteraction.speak('欢迎使用智瞳应用，正在加载语音识别模型')

        const modelLoaded = await voiceInteraction.loadModel()

        if (modelLoaded) {
          await voiceInteraction.speak('模型加载成功')
          console.log('模型加载成功，用户可通过右上角麦克风按钮进行语音输入')
        } else {
          await voiceInteraction.speak('模型加载失败')
        }
      } catch (error) {
        console.error('自动开启语音交互失败:', error)
      }
    }
  },
  beforeUnmount() {
  }
}
</script>

<style scoped>
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 700, 'GRAD' 0, 'opsz' 48;
  font-size: 84px;
}

.brutal-border {
  border: 4px solid #000000;
}

.deep-blue-btn {
  background-color: #0052CC;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.animate-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
</style>