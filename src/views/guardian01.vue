<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] bg-black text-white min-h-screen relative">
      <!-- TopAppBar -->
      <header class="fixed top-0 left-1/2 -translate-x-1/2 w-[414px] border-b-4 border-white bg-black z-40 flex justify-between items-center px-6 h-24">
        <div class="flex items-center">
          <h1 class="font-lexend font-black uppercase text-3xl text-white">智瞳</h1>
        </div>
        <div class="flex items-center">
          <span class="material-symbols-outlined text-white" style="font-size: 32px;">star</span>
        </div>
      </header>
      <!-- Main Content Canvas -->
      <main class="pt-24 pb-28 px-6 w-full overflow-y-auto">
        <div class="w-full flex flex-col gap-stack-gap">
          <!-- Simple Map Component -->
          <section class="w-full h-80 border-4 border-white bg-surface-container overflow-hidden relative rounded-xl">
            <img class="w-full h-full object-cover grayscale opacity-80" data-alt="A high-contrast industrial style digital map view of a city grid. The map uses a monochrome dark aesthetic with thick white lines for streets and a single bold blue glowing circle indicating a location. The visual style is clinical and functional, stripped of any artistic filters or soft gradients, appearing like a professional GPS interface for low-vision users." src="https://lh3.googleusercontent.com/aida-public/AB6AXuCtOsw75SzJ3y6kkbcCO_Ip3eaG0KZVtp2HSQ4UAwV2j4KBFceyUQQdg4A8deLMeSTVyaGtoXe_vhkuzB1z32-MuyIXmDbHnfE3O2_8mYr1OaJ-YutoWXSk8KAklbWnSMpgyBxG7UuaEoC3LlKtw4R1mY6ZWoCN_JhK1ORQQbwWG6FcUirfUdzotTI1sLM56PwOUK86hHR65Tnj2jSPAdO15rwz07pr_CxoliWoSTLAaJIOqLFiiGDz9w3FxCE9wj7vRTvzbOaf1iM"/>
            <div class="absolute inset-0 flex items-center justify-center pointer-events-none">
              <div class="w-12 h-12 bg-[#0052CC] border-4 border-white rounded-full"></div>
            </div>
            <div class="absolute top-4 right-4 bg-black border-2 border-white px-4 py-2 rounded-lg">
              <span class="font-label-lg text-label-lg text-white">GPS: 已激活</span>
            </div>
          </section>
          <!-- Status & Location Cards Grid -->
          <div class="grid grid-cols-1 gap-stack-gap">
            <!-- Status Card -->
            <div class="border-4 border-white bg-[#004d00] p-8 flex flex-col justify-center min-h-[160px] rounded-xl active:scale-95 transition-transform cursor-pointer">
              <span class="font-label-lg text-label-lg text-white mb-2">状态</span>
              <h2 class="font-headline-md text-headline-md text-white">状态：安全</h2>
            </div>
            <!-- Location Card -->
          </div>
          <!-- Emergency Call Button -->
          <router-link to="/emergencepage" class="w-full h-32 bg-[#0052CC] border-4 border-white rounded-xl flex items-center justify-center gap-6 active:bg-blue-800 active:scale-95 transition-all">
            <span class="material-symbols-outlined text-white" data-icon="call" style="font-variation-settings: 'FILL' 1;">call</span>
            <span class="font-button-text text-button-text text-white">紧急处理</span>
          </router-link>
          <!-- Recent Activity List -->
          <section class="mt-4">
            <h3 class="font-label-lg text-label-lg text-white mb-4 uppercase tracking-widest">最近日志</h3>
            <div class="border-4 border-white divide-y-4 divide-white bg-black rounded-xl overflow-hidden">
              <div class="h-24 flex items-center px-6">
                <span class="font-body-md text-white text-label-lg">上午 10:45 - 进入安全区域</span>
              </div>
              <div class="h-24 flex items-center px-6">
                <span class="font-body-md text-white text-label-lg">上午 09:12 - 设备已配对</span>
              </div>
            </div>
          </section>
        </div>
      </main>
      <!-- BottomNavBar -->
      <nav class="fixed bottom-0 left-1/2 -translate-x-1/2 w-[414px] border-t-4 border-white bg-black flex justify-around items-stretch h-28 z-50">
        <router-link class="bg-[#0052CC] text-white flex flex-col items-center justify-center h-full flex-1 transition-colors" to="/guardian01">
          <span class="material-symbols-outlined">home</span>
          <span class="font-lexend font-bold text-lg uppercase">首页</span>
        </router-link>
        <router-link class="text-white flex flex-col items-center justify-center h-full flex-1 border-l-4 border-white hover:bg-zinc-900 transition-colors" to="/guardian_personal">
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
  name: 'guardian01',
  mounted() {
    // 页面加载后语音播报，播报完成后自动重启识别
    setTimeout(() => {
      voiceInteraction.announcePageWithFeatures('/guardian01', [
        '查看被监护人状态', '查看位置信息', '紧急处理'
      ], true) // true = 播报完成后自动重新启动语音识别
    }, 500)
  }
}
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Lexend:wght@100..900&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap');

.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 700, 'GRAD' 0, 'opsz' 48;
  font-size: 48px;
}

body {
  background-color: #000000;
  color: #ffffff;
  -webkit-font-smoothing: antialiased;
}

body {
  min-height: max(884px, 100dvh);
}
</style>