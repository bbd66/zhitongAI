<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] bg-black text-white font-body-md min-h-screen relative">
      <header class="fixed top-0 left-1/2 -translate-x-1/2 w-[414px] border-b-4 border-white bg-black text-white flex justify-between items-center px-6 h-24 z-40">
      <div class="flex items-center gap-4">
        <h1 class="font-lexend font-black uppercase text-3xl text-white">智瞳</h1>
      </div>
    </header>

      <main class="pt-24 pb-28 px-6 w-full flex flex-col gap-stack-gap">
        <div class="w-full bg-deep-blue border-4 border-white rounded-xl p-8 flex items-center justify-between">
          <div class="flex flex-col gap-2">
            <span class="text-label-lg font-label-lg uppercase text-white">环境状态</span>
            <h2 class="text-headline-lg font-headline-lg text-white">光线良好</h2>
          </div>
          <span class="material-symbols-outlined text-white" style="font-size: 64px;">light_mode</span>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <router-link to="/camerapage" class="flex flex-col items-center justify-center gap-4 py-12 px-4 bg-deep-blue border-4 border-white rounded-xl active:scale-95 transition-transform" @click="handleModuleClick('开始拍摄')">
            <span class="material-symbols-outlined text-white">explore</span>
            <span class="font-button-text text-[22px] leading-tight text-white text-center">开始拍摄</span>
          </router-link>
          <button class="flex flex-col items-center justify-center gap-4 py-12 px-4 bg-black border-4 border-white rounded-xl active:scale-95 transition-transform" @click="handleModuleClick('公交模式')">
            <span class="material-symbols-outlined text-white">directions_bus</span>
            <span class="font-button-text text-[22px] leading-tight text-white text-center">公交模式</span>
          </button>
          <button class="flex flex-col items-center justify-center gap-4 py-12 px-4 bg-black border-4 border-white rounded-xl active:scale-95 transition-transform" @click="readPageInfo">
            <span class="material-symbols-outlined text-white">text_fields</span>
            <span class="font-button-text text-[22px] leading-tight text-white text-center">文字朗读</span>
          </button>
          <router-link to="/userpersonal" class="flex flex-col items-center justify-center gap-4 py-12 px-4 bg-black border-4 border-white rounded-xl active:scale-95 transition-transform" @click="handleModuleClick('个性设置')">
            <span class="material-symbols-outlined text-white">settings</span>
            <span class="font-button-text text-[22px] leading-tight text-white text-center">个性设置</span>
          </router-link>
        </div>

        <div class="mt-8 border-4 border-white p-6 bg-black rounded-xl flex flex-col gap-8 items-center">
          <div class="w-full flex flex-col gap-4">
            <div class="bg-deep-blue text-white p-4 font-label-lg border-2 border-white rounded-lg inline-block w-fit uppercase">周边服务</div>
            <h3 class="text-headline-md font-headline-md leading-tight text-white">检测到最近盲道<br/>距离 15 米</h3>
            <p class="text-body-md text-on-surface-variant">为您自动规划无障碍路径</p>
            <button class="w-full h-20 bg-white text-black font-button-text border-4 border-white rounded-xl active:bg-zinc-300 transition-colors" @click="handleModuleClick('立即开启')">
              立即开启
            </button>
          </div>
        </div>
      </main>

      <nav class="fixed bottom-0 left-1/2 -translate-x-1/2 w-[414px] border-t-4 border-white bg-black flex justify-around items-stretch h-28 z-50">
        <router-link to="/user01" class="bg-deep-blue text-white flex flex-col items-center justify-center h-full flex-1 border-x-2 border-white active:bg-blue-800 transition-colors">
          <span class="material-symbols-outlined">home</span>
          <span class="font-lexend font-bold text-lg uppercase">主页</span>
        </router-link>
        <router-link to="/camerapage" class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-900 active:bg-blue-800 transition-colors">
          <span class="material-symbols-outlined">visibility</span>
          <span class="font-lexend font-bold text-lg uppercase">相机</span>
        </router-link>
        <router-link to="/userpersonal" class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-900 active:bg-blue-800 transition-colors">
          <span class="material-symbols-outlined">account_circle</span>
          <span class="font-lexend font-bold text-lg uppercase">个人</span>
        </router-link>
      </nav>
    </div>
  </div>
</template>

<script>
import voiceInteraction from '../utils/voiceInteraction.js'

export default {
  name: 'user01',
  data() {
    return {
      pageInfo: {
        title: '用户主页',
        modules: ['开始拍摄', '公交模式', '文字朗读', '个性设置'],
        environment: '环境状态：光线良好',
        service: '周边服务：检测到最近盲道，距离15米，为您自动规划无障碍路径'
      }
    }
  },
  mounted() {
    setTimeout(() => {
      voiceInteraction.announcePageWithFeatures('/user01', [
        '开始拍摄', '公交模式', '文字朗读', '个性设置'
      ], true) // true = 播报完成后自动重新启动语音识别
    }, 500)
  },
  methods: {
    handleModuleClick(moduleName) {
      voiceInteraction.speak(`已选择${moduleName}`)
    },
    async readPageInfo() {
      voiceInteraction.speak('正在朗读当前页面信息')
      await voiceInteraction.speak(this.pageInfo.title)
      await voiceInteraction.speak(this.pageInfo.environment)
      await voiceInteraction.speak(`可用功能：${this.pageInfo.modules.join('、')}`)
      await voiceInteraction.speak(this.pageInfo.service)
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