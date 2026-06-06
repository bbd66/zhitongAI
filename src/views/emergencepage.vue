<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] bg-black text-on-background font-body-md antialiased min-h-screen relative">
      <header class="fixed top-0 left-1/2 -translate-x-1/2 w-[414px] border-b-4 border-white bg-black z-40 flex justify-between items-center px-6 h-24">
        <div class="flex items-center gap-4">
          <router-link to="/guardian01" class="material-symbols-outlined text-white active:scale-95 transition-transform">close</router-link>
        </div>
      </header>

      <main class="pt-24 pb-28 px-8 w-full flex flex-col gap-8 overflow-y-auto">
        <section class="flex-1">
          <button class="w-full h-full bg-secondary-container border-4 border-white rounded-2xl flex flex-col items-center justify-center gap-6 active:scale-95 transition-transform">
            <div class="p-8">
              <span class="material-symbols-outlined text-white" style="font-size: 80px;">call</span>
            </div>
            <span class="font-headline-lg text-white text-center px-4">呼叫紧急联系人</span>
          </button>
        </section>

        <section class="flex-1">
          <button class="w-full h-full bg-brand-blue border-4 border-white rounded-2xl flex flex-col items-center justify-center gap-6 active:scale-95 transition-transform">
            <div class="p-8">
              <span class="material-symbols-outlined text-white" style="font-size: 80px;">local_police</span>
            </div>
            <span class="font-headline-lg text-white text-center px-4">一键报警 110</span>
          </button>
        </section>

        <div class="w-full bg-black border-4 border-white p-4 text-center rounded-xl">
          <p class="text-white font-label-lg uppercase">当前位置: 北京市朝阳区建国路</p>
        </div>
      </main>

      <nav class="fixed bottom-0 left-1/2 -translate-x-1/2 w-[414px] border-t-4 border-white bg-black flex justify-around items-stretch h-28 z-50">
        <router-link class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-800 transition-colors" to="/guardian01">
          <span class="material-symbols-outlined">home</span>
          <span class="font-lexend font-bold text-lg uppercase">首页</span>
        </router-link>
        <router-link class="bg-[#00008B] text-white flex flex-col items-center justify-center h-full flex-1 border-x-4 border-white transition-colors" to="/guardian_personal">
          <span class="material-symbols-outlined">person</span>
          <span class="font-lexend font-bold text-lg uppercase">个人</span>
        </router-link>
      </nav>
    </div>
  </div>
</template>

<script>
import voiceInteraction from '../utils/voiceInteraction.js'

export default {
  name: 'emergencepage',
  mounted() {
    // 页面加载后语音播报，播报完成后自动重启识别
    setTimeout(() => {
      voiceInteraction.announcePageWithFeatures('/emergencepage', [
        '呼叫紧急联系人', '一键报警'
      ], true) // true = 播报完成后自动重新启动语音识别
    }, 500)
  }
}
</script>

<style scoped>
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 700, 'GRAD' 0, 'opsz' 48;
  font-size: 80px;
}
</style>