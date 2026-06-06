<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] bg-[#0052CC] text-white font-lexend min-h-screen relative">
      <main class="flex flex-col items-center justify-between min-h-screen p-8 text-center pb-36">
        <div class="w-full pt-4">
          <span class="inline-block px-6 py-3 bg-black text-white border-4 border-white font-label-lg text-label-lg uppercase tracking-widest rounded-xl">
            紧急状态
          </span>
        </div>

        <div class="flex flex-col items-center gap-10">
          <div class="w-48 h-48 rounded-full border-[12px] border-white flex items-center justify-center animate-pulse bg-white/10">
            <span class="material-symbols-outlined text-white" style="font-size: 120px;">priority_high</span>
          </div>

          <div class="space-y-6 w-full">
            <h1 class="font-headline-lg text-headline-lg text-white leading-none drop-shadow-lg">
              检测到摔倒！
            </h1>
            <div class="font-body-lg text-body-lg text-black bg-white px-6 py-3 border-l-[12px] border-black rounded-r-xl shadow-xl">
              正在向监护人发送位置
            </div>
          </div>
        </div>

        <div class="w-full border-4 border-black bg-white p-4 flex items-center gap-4 rounded-2xl shadow-2xl">
          <div class="w-24 h-24 bg-zinc-200 border-2 border-black flex-shrink-0 rounded-lg overflow-hidden flex items-center justify-center bg-white">
            <span class="material-symbols-outlined text-black flex items-center justify-center w-full h-full" style="font-size: 48px;">location_on</span>
          </div>
          <div class="text-left">
            <span class="block font-label-lg text-label-lg text-zinc-500 uppercase">当前位置</span>
            <span class="block font-body-md text-body-md text-black">****</span>
          </div>
        </div>

        <div class="w-full space-y-6">
          <button class="w-full h-32 bg-white border-4 border-black border-b-[12px] active:border-b-4 active:translate-y-2 transition-all flex items-center justify-center gap-4 group rounded-2xl">
            <span class="material-symbols-outlined text-[#0052CC]" style="font-size: 80px; font-variation-settings: 'FILL' 1;">cancel</span>
            <span class="font-button-text text-button-text text-[#0052CC]">我没事，取消告警</span>
          </button>
          <p class="font-label-lg text-label-lg text-white font-bold">
            自动确认将在 10 秒后执行
          </p>
        </div>
      </main>

      <nav class="fixed bottom-0 left-1/2 -translate-x-1/2 w-[414px] border-t-4 border-white bg-black flex justify-around items-stretch h-28 z-50">
        <router-link to="/guardian01" class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-900 transition-colors">
          <span class="material-symbols-outlined">home</span>
          <span class="font-lexend font-bold text-lg uppercase">首页</span>
        </router-link>
        <router-link to="/guardian_personal" class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-900 transition-colors border-x-4 border-white">
          <span class="material-symbols-outlined">person</span>
          <span class="font-lexend font-bold text-lg uppercase">个人</span>
        </router-link>
        <button class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-900 transition-colors">
          <span class="material-symbols-outlined">call</span>
          <span class="font-lexend font-bold text-lg uppercase">报警</span>
        </button>
      </nav>
    </div>
  </div>
</template>

<script>
import voiceInteraction from '../utils/voiceInteraction.js'

export default {
  name: 'sospage',
  mounted() {
    // 页面加载后语音播报，播报完成后自动重启识别
    setTimeout(() => {
      voiceInteraction.announcePageWithFeatures('/sospage', [
        '取消告警', '等待自动确认'
      ], true) // true = 播报完成后自动重新启动语音识别
    }, 500)
  }
}
</script>

<style scoped>
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 700, 'GRAD' 0, 'opsz' 48;
}
</style>