<template>
  <div
    class="fixed top-0 right-0 z-50 select-none w-24 h-24 flex items-center justify-center"
    @mousedown="onPress"
    @mouseup="onRelease"
    @mouseleave="onRelease"
    @touchstart.prevent="onPress"
    @touchend.prevent="onRelease"
    @touchcancel.prevent="onRelease"
  >
    <div
      :class="[
        'w-14 h-14 rounded-full flex items-center justify-center transition-all duration-150',
        isPressed
          ? 'bg-red-500 scale-110 shadow-lg shadow-red-500/50'
          : 'bg-blue-600 hover:bg-blue-700'
      ]"
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="currentColor"
        :class="['w-7 h-7 text-white transition-transform', isPressed ? 'scale-110' : '']"
      >
        <path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
        <path d="M19 10a1 1 0 0 0-2 0 5 5 0 0 1-10 0 1 0 0 0-2 0 7 7 0 0 0 6 6.93V20H8a1 1 0 1 0 0 2h8a1 1 0 1 0 0-2h-3v-3.07A7 7 0 0 0 19 10Z" />
      </svg>
    </div>

    <!-- 录音波纹动画 -->
    <div
      v-if="isPressed"
      class="absolute inset-0 flex items-center justify-center pointer-events-none"
    >
      <div class="w-14 h-14 rounded-full bg-red-400/30 animate-ping" />
    </div>
  </div>
</template>

<script>
import voiceInteraction from '../utils/voiceInteraction.js'

export default {
  name: 'MicrophoneButton',
  data() {
    return {
      isPressed: false
    }
  },
  methods: {
    async onPress() {
      if (this.isPressed) return
      this.isPressed = true
      await voiceInteraction.startPushToTalk()
    },
    async onRelease() {
      if (!this.isPressed) return
      this.isPressed = false
      await voiceInteraction.stopPushToTalk()
    }
  }
}
</script>

<style scoped>
@keyframes ping {
  75%, 100% {
    transform: scale(2);
    opacity: 0;
  }
}

.animate-ping {
  animation: ping 1s cubic-bezier(0, 0, 0.2, 1) infinite;
}
</style>