<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] bg-black text-white min-h-screen relative flex flex-col items-center justify-center">
      <div class="w-full px-6">
        <div class="w-full text-center mb-12">
          <h1 class="font-lexend font-black uppercase text-4xl text-white mb-4">智瞳</h1>
          <h2 class="font-headline-md text-headline-md text-white">请选择模式</h2>
        </div>

        <div class="w-full flex flex-col space-y-8">
          <button
            @click="selectUserMode"
            class="w-full h-40 bg-primary-container border-4 border-white rounded-xl flex items-center justify-center space-x-6 active:scale-95 transition-transform hover:bg-blue-800"
          >
            <span class="material-symbols-outlined text-white" style="font-variation-settings: 'FILL' 1;">person</span>
            <span class="font-button-text text-button-text text-white">用户模式</span>
          </button>

          <button
            @click="selectGuardianMode"
            class="w-full h-40 bg-primary-container border-4 border-white rounded-xl flex items-center justify-center space-x-6 active:scale-95 transition-transform hover:bg-blue-800"
          >
            <span class="material-symbols-outlined text-white" style="font-variation-settings: 'FILL' 1;">shield</span>
            <span class="font-button-text text-button-text text-white">监护人模式</span>
          </button>
        </div>

        <p class="font-body-md text-body-md text-on-surface-variant text-center mt-12">
          通过选择您的身份，我们将为您提供最合适的辅助视界体验。
        </p>

        <button @click="goToVoiceDebug" class="mt-8 px-4 py-2 bg-zinc-700 border-2 border-white rounded-lg text-white text-sm">
          🎤 语音功能诊断
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import voiceInteraction from '../utils/voiceInteraction.js'

export default {
  name: 'selectmodle',
  mounted() {
    // 模式选择页面不播报功能列表，只显示页面内容
  },
  methods: {
    async selectUserMode() {
      await voiceInteraction.speak('已选择用户模式，正在跳转')
      this.$router.push('/user01')
    },
    async selectGuardianMode() {
      await voiceInteraction.speak('已选择监护人模式，正在跳转')
      this.$router.push('/guardian01')
    },
    goToVoiceDebug() {
      this.$router.push('/voicedebug')
    }
  }
}
</script>

<style scoped>
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 700, 'GRAD' 0, 'opsz' 48;
  font-size: 48px;
}
</style>