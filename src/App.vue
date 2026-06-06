<template>
  <div>
    <router-view />
    <MicrophoneButton />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import voiceInteraction from './utils/voiceInteraction'
import MicrophoneButton from './components/MicrophoneButton.vue'

const router = useRouter()
const route = useRoute()

const pageFeatures = {
  '/': [],
  '/selectmodle': ['选择不同模式'],
  '/user01': ['查看识别历史', '设置', '紧急求助'],
  '/guardian01': ['查看被监护人状态', '接收紧急通知'],
  '/camerapage': ['拍照识别', '实时识别', '语音播报识别结果'],
  '/userpersonal': ['个人资料管理', '语音设置', '偏好设置'],
  '/guardian_personal': ['监护人资料', '被监护人管理'],
  '/emergencepage': ['紧急情况处理', '快速求助', '联系监护人'],
  '/sospage': ['发起求救', '发送位置', '通知紧急联系人']
}

const handleCommand = (command) => {
  console.log('收到语音命令:', command)
}

const handleNavigate = (routePath) => {
  console.log('语音导航到:', routePath)
  router.push(routePath)
}

onMounted(async () => {
  await voiceInteraction.init(handleCommand, handleNavigate)

  voiceInteraction.announcePageWithFeatures(
    route.path,
    pageFeatures[route.path] || []
  )

  watch(route, (newRoute) => {
    if (newRoute.path !== voiceInteraction.getCurrentPage()) {
      setTimeout(() => {
        voiceInteraction.announcePageWithFeatures(
          newRoute.path,
          pageFeatures[newRoute.path] || []
        )
      }, 300)
    }
  })
})

onUnmounted(() => {
  voiceInteraction.destroy()
})

defineExpose({
  speak: (text) => voiceInteraction.speak(text),
  announcePage: (path, features) => voiceInteraction.announcePageWithFeatures(path, features),
  isVoiceSupported: () => voiceInteraction.isSupported()
})
</script>