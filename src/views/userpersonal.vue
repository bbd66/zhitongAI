<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] bg-black text-white font-body-md min-h-screen relative">
      <header class="fixed top-0 left-1/2 -translate-x-1/2 w-[414px] border-b-4 border-white bg-black z-40 flex justify-between items-center px-6 h-24">
        <h1 class="font-lexend font-black uppercase text-3xl text-white">智瞳</h1>
        <div class="flex items-center"><span class="material-symbols-outlined text-white" style="font-size: 32px;">star</span></div>
      </header>

      <main class="pt-24 pb-28 px-6 w-full overflow-y-auto flex flex-col gap-stack-gap">
        <!-- 头像和基本信息 -->
        <section class="flex items-center gap-6 py-6 border-b-4 border-white">
          <div class="w-32 h-32 rounded-lg border-4 border-white overflow-hidden bg-blue-900 flex-shrink-0 cursor-pointer" @click="uploadAvatar">
            <img v-if="avatarUrl" :src="avatarUrl" alt="头像" class="w-full h-full object-cover" />
            <div v-else class="w-full h-full flex items-center justify-center bg-blue-900">
              <span class="material-symbols-outlined text-white" style="font-size: 80px;">person</span>
            </div>
          </div>
          <div class="flex-1">
            <p class="font-headline-md text-white">{{ userInfo.name }}</p>
            <span class="bg-blue-900 text-white font-label-lg px-4 py-1 rounded border-2 border-white inline-block">已实名认证</span>
          </div>
        </section>

        <!-- 编辑个人信息按钮 -->
        <section class="border-4 border-white bg-surface-container rounded-lg p-6">
          <button 
            class="w-full bg-black border-4 border-white rounded-xl h-20 flex items-center justify-center gap-3 active:scale-95 transition-transform text-white font-button-text"
            @click="showEditForm = true"
          >
            <span class="material-symbols-outlined">edit</span>
            <span>编辑个人信息</span>
          </button>
        </section>

        <!-- 我的监护人 -->
        <section class="border-4 border-white bg-surface-container rounded-lg p-6 flex flex-col gap-4">
          <div class="flex items-center gap-4">
            <span class="material-symbols-outlined text-blue-700">health_and_safety</span>
            <h3 class="font-headline-md">我的监护人</h3>
          </div>
          <div class="bg-black border-2 border-white rounded-lg p-4 space-y-2">
            <p class="font-body-lg text-white">姓名: <span class="text-white">{{ userInfo.guardianName }}</span></p>
            <p class="font-body-lg text-white">电话: <span class="text-white">{{ userInfo.guardianPhone }}</span></p>
          </div>
        </section>

        <!-- 音量、亮度、语速调节 -->
        <section class="flex flex-col gap-6">
          <div class="p-6 border-4 border-white rounded-lg bg-surface">
            <div class="flex justify-between items-center mb-4">
              <label class="font-button-text"><span class="text-4xl font-bold">音量调节</span></label>
              <div class="flex items-center gap-2">
                <span class="text-2xl font-bold text-white">{{ volume }}%</span>
                <span class="material-symbols-outlined">volume_up</span>
              </div>
            </div>
            <input 
              class="h-12 cursor-pointer w-full" 
              type="range" 
              max="100" 
              min="0" 
              v-model="volume"
              @change="onVolumeChange"
            />
          </div>

          <div class="p-6 border-4 border-white rounded-lg bg-surface">
            <div class="flex justify-between items-center mb-4">
              <label class="font-button-text"><span class="text-4xl font-bold">亮度调节</span></label>
              <div class="flex items-center gap-2">
                <span class="text-2xl font-bold text-white">{{ brightness }}%</span>
                <span class="material-symbols-outlined">brightness_6</span>
              </div>
            </div>
            <input 
              class="h-12 cursor-pointer w-full" 
              type="range" 
              max="100" 
              min="0" 
              v-model="brightness"
              @change="onBrightnessChange"
            />
          </div>

          <div class="p-6 border-4 border-white rounded-lg bg-surface">
            <div class="flex justify-between items-center mb-4">
              <label class="font-button-text"><span class="text-4xl font-bold">语速调节</span></label>
              <span class="material-symbols-outlined">record_voice_over</span>
            </div>
            <input class="h-12 cursor-pointer w-full" type="range" max="100" min="0" value="50"/>
          </div>
        </section>

        <!-- 功能按钮 -->
        <section class="grid grid-cols-2 gap-4">
          <button 
            class="bg-black border-4 border-white rounded-xl h-40 flex flex-col items-center justify-center gap-2 active:scale-95 transition-transform"
            @click="goToStartPage"
          >
            <span class="material-symbols-outlined text-white">grid_view</span>
            <span class="font-button-text text-white">切换模式</span>
          </button>
          <button class="bg-blue-900 border-4 border-white rounded-xl h-40 flex flex-col items-center justify-center gap-2 active:scale-95 transition-transform" @click="showPairingCodeModal = true">
            <span class="material-symbols-outlined text-white">link</span>
            <span class="font-button-text text-white">配对码</span>
          </button>
          <button class="bg-black border-4 border-white rounded-xl h-40 flex flex-col items-center justify-center gap-2 active:scale-95 transition-transform">
            <span class="material-symbols-outlined text-white">help</span>
            <span class="font-button-text text-white">使用指南</span>
          </button>
          <button class="bg-black border-4 border-white rounded-xl h-40 flex flex-col items-center justify-center gap-2 active:scale-95 transition-transform">
            <span class="material-symbols-outlined text-white">feedback</span>
            <span class="font-button-text text-white">意见反馈</span>
          </button>
          <button class="bg-blue-900 border-4 border-white rounded-xl h-40 flex flex-col items-center justify-center gap-2 active:scale-95 transition-transform">
            <span class="material-symbols-outlined text-white">settings</span>
            <span class="font-button-text text-white">系统设置</span>
          </button>
          <router-link to="/" class="bg-black border-4 border-white rounded-xl h-40 flex flex-col items-center justify-center gap-2 active:scale-95 transition-transform">
            <span class="material-symbols-outlined text-error">logout</span>
            <span class="font-button-text text-error">退出登录</span>
          </router-link>
        </section>
      </main>

      <!-- 编辑表单弹窗 -->
      <div v-if="showEditForm" class="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-6">
        <div class="bg-black border-4 border-white rounded-xl p-6 w-full max-w-[380px] max-h-[80vh] overflow-y-auto">
          <div class="flex justify-between items-center mb-6">
            <h3 class="font-headline-md text-white">编辑个人信息</h3>
            <button @click="showEditForm = false" class="text-gray-400 hover:text-white">
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="space-y-4">
            <div class="bg-black border-2 border-white rounded-lg p-4">
              <label class="font-body-sm text-gray-400 block mb-2">用户名称</label>
              <input 
                v-model="userInfo.name" 
                type="text" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入用户名称"
              />
            </div>
            <div class="bg-black border-2 rounded-lg p-4" :class="{'border-red-500': userInfo.phone && !isValidPhone(userInfo.phone), 'border-white': !userInfo.phone || isValidPhone(userInfo.phone)}">
              <label class="font-body-sm text-gray-400 block mb-2">手机号码</label>
              <input 
                v-model="userInfo.phone" 
                type="tel" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入手机号码"
                maxlength="11"
                @input="filterPhoneInput('phone', $event)"
              />
              <p v-if="userInfo.phone && !isValidPhone(userInfo.phone)" class="text-red-500 text-sm mt-1">请输入11位数字的手机号码</p>
            </div>
            <div class="bg-black border-2 border-white rounded-lg p-4">
              <label class="font-body-sm text-gray-400 block mb-2">年龄</label>
              <input 
                v-model="userInfo.age" 
                type="number" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入年龄"
              />
            </div>
            <div class="bg-black border-2 border-white rounded-lg p-4">
              <label class="font-body-sm text-gray-400 block mb-2">紧急联系人</label>
              <input 
                v-model="userInfo.emergencyContact" 
                type="text" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入紧急联系人姓名"
              />
            </div>
            <div class="bg-black border-2 rounded-lg p-4" :class="{'border-red-500': userInfo.emergencyPhone && !isValidPhone(userInfo.emergencyPhone), 'border-white': !userInfo.emergencyPhone || isValidPhone(userInfo.emergencyPhone)}">
              <label class="font-body-sm text-gray-400 block mb-2">紧急联系电话</label>
              <input 
                v-model="userInfo.emergencyPhone" 
                type="tel" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入紧急联系电话"
                maxlength="11"
                @input="filterPhoneInput('emergencyPhone', $event)"
              />
              <p v-if="userInfo.emergencyPhone && !isValidPhone(userInfo.emergencyPhone)" class="text-red-500 text-sm mt-1">请输入11位数字的手机号码</p>
            </div>
          </div>
          <div class="flex gap-4 mt-6">
            <button 
              class="flex-1 bg-zinc-700 border-4 border-white rounded-xl h-16 flex items-center justify-center gap-2 active:scale-95 transition-transform text-white font-button-text"
              @click="showEditForm = false"
            >
              <span>取消</span>
            </button>
            <button 
              class="flex-1 bg-blue-900 border-4 border-white rounded-xl h-16 flex items-center justify-center gap-2 active:scale-95 transition-transform text-white font-button-text"
              @click="savePersonalInfo"
            >
              <span>保存</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 配对码弹窗 -->
      <div v-if="showPairingCodeModal" class="fixed inset-0 bg-black/80 z-50 p-6 overflow-y-auto">
        <div class="min-h-full flex items-center justify-center">
          <div class="bg-black border-4 border-white rounded-xl p-6 w-full max-w-[380px]">
          <div class="flex justify-between items-center mb-6">
            <h3 class="font-headline-md text-white text-2xl">输入配对码</h3>
            <button @click="closePairingCodeModal" class="text-gray-400 hover:text-white">
              <span class="material-symbols-outlined" style="font-size: 32px;">close</span>
            </button>
          </div>
          
          <p class="text-gray-400 text-lg mb-6 text-center">请输入6位数字配对码</p>
          
          <div class="flex justify-center gap-3 mb-6">
            <input 
              v-for="(digit, index) in pairingCode" 
              :key="index"
              :ref="el => pairingCodeInput[index] = el"
              v-model="pairingCode[index]"
              type="text"
              inputmode="numeric"
              maxlength="1"
              class="w-14 h-16 bg-black border-4 border-white rounded-lg text-center text-3xl font-bold text-white focus:outline-none focus:border-blue-500"
              @input="handlePairingCodeInput(index, $event)"
              @keydown="handlePairingCodeKeydown(index, $event)"
              @paste="handlePairingCodePaste(index, $event)"
            />
          </div>
          
          <div class="flex gap-4 mt-6">
            <button 
              class="flex-1 bg-zinc-700 border-4 border-white rounded-xl h-16 flex items-center justify-center gap-2 active:scale-95 transition-transform text-white font-button-text text-xl"
              @click="closePairingCodeModal"
            >
              <span>取消</span>
            </button>
            <button 
              class="flex-1 bg-blue-900 border-4 border-white rounded-xl h-16 flex items-center justify-center gap-2 active:scale-95 transition-transform text-white font-button-text text-xl"
              @click="submitPairingCode"
            >
              <span>确认</span>
            </button>
          </div>
          </div>
        </div>
      </div>

      <!-- 隐藏的文件上传输入 -->
      <input 
        ref="avatarInput"
        type="file" 
        accept="image/*" 
        class="hidden"
        @change="handleAvatarUpload"
      />

      <nav class="fixed bottom-0 left-1/2 -translate-x-1/2 w-[414px] border-t-4 border-white bg-black flex justify-around items-stretch h-28 z-50">
        <router-link class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-900 transition-colors" to="/user01">
          <span class="material-symbols-outlined">home</span>
          <span class="font-lexend font-bold text-lg uppercase">首页</span>
        </router-link>
        <router-link class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-900 transition-colors border-x-4 border-white" to="/camerapage">
          <span class="material-symbols-outlined">visibility</span>
          <span class="font-lexend font-bold text-lg uppercase">相机</span>
        </router-link>
        <router-link class="bg-blue-900 text-white flex flex-col items-center justify-center h-full flex-1" to="/userpersonal">
          <span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1;">person</span>
          <span class="font-lexend font-bold text-lg uppercase">个人</span>
        </router-link>
      </nav>
    </div>
  </div>
</template>

<script>
import voiceInteraction from '../utils/voiceInteraction.js'

export default {
  name: 'userpersonal',
  data() {
    return {
      volume: 80,
      brightness: 70,
      avatarUrl: null,
      showEditForm: false,
      showPairingCodeModal: false,
      pairingCode: ['', '', '', '', '', ''],
      pairingCodeInput: [null, null, null, null, null, null],
      userInfo: {
        name: '用户名称',
        phone: '',
        age: '',
        emergencyContact: '',
        emergencyPhone: '',
        guardianName: '张小华',
        guardianPhone: '138 **** 5678'
      }
    }
  },
  mounted() {
    setTimeout(() => {
      voiceInteraction.announcePageWithFeatures('/userpersonal', [
        '查看个人信息', '上传头像', '调节音量', '调节亮度', '调节语速'
      ], true) // true = 播报完成后自动重新启动语音识别
    }, 500)
    this.applyBrightness()
  },
  methods: {
    uploadAvatar() {
      voiceInteraction.speak('请选择头像图片')
      this.$refs.avatarInput.click()
    },
    handleAvatarUpload(event) {
      const file = event.target.files[0]
      if (file) {
        const reader = new FileReader()
        reader.onload = (e) => {
          this.avatarUrl = e.target.result
          voiceInteraction.speak('头像上传成功')
        }
        reader.readAsDataURL(file)
      }
    },
    filterPhoneInput(field, event) {
      const value = event.target.value
      const filtered = value.replace(/\D/g, '').slice(0, 11)
      this.userInfo[field] = filtered
    },
    isValidPhone(phone) {
      return /^\d{11}$/.test(phone)
    },
    savePersonalInfo() {
      if (this.userInfo.phone && !this.isValidPhone(this.userInfo.phone)) {
        voiceInteraction.speak('手机号码格式不正确')
        return
      }
      if (this.userInfo.emergencyPhone && !this.isValidPhone(this.userInfo.emergencyPhone)) {
        voiceInteraction.speak('紧急联系电话格式不正确')
        return
      }
      if (this.userInfo.emergencyContact) {
        this.userInfo.guardianName = this.userInfo.emergencyContact
      }
      if (this.userInfo.emergencyPhone) {
        this.userInfo.guardianPhone = this.userInfo.emergencyPhone.replace(/(\d{3})\d{4}(\d{4})/, '$1 **** $2')
      }
      this.showEditForm = false
      voiceInteraction.speak('个人信息保存成功')
    },
    onVolumeChange() {
      voiceInteraction.setVolume(this.volume)
      voiceInteraction.speak(`音量已设置为${this.volume}%`)
    },
    onBrightnessChange() {
      this.applyBrightness()
      voiceInteraction.speak(`亮度已设置为${this.brightness}%`)
    },
    applyBrightness() {
      const brightnessValue = 0.5 + (this.brightness / 100)
      document.body.style.filter = `brightness(${brightnessValue})`
    },
    goToStartPage() {
      voiceInteraction.speak('正在返回模式选择页')
      this.$router.push('/')
    },
    closePairingCodeModal() {
      this.showPairingCodeModal = false
      this.pairingCode = ['', '', '', '', '', '']
    },
    handlePairingCodeInput(index, event) {
      const value = event.target.value
      // 只允许数字
      const digit = value.replace(/\D/g, '').slice(-1)
      this.pairingCode[index] = digit
      event.target.value = digit
      
      // 自动跳到下一个输入框
      if (digit && index < 5) {
        this.$nextTick(() => {
          this.pairingCodeInput[index + 1]?.focus()
        })
      }
      
      // 如果6位都填满了，自动提交
      if (this.pairingCode.every(d => d !== '')) {
        setTimeout(() => {
          this.submitPairingCode()
        }, 100)
      }
    },
    handlePairingCodeKeydown(index, event) {
      if (event.key === 'Backspace') {
        if (!this.pairingCode[index] && index > 0) {
          // 如果当前输入框为空且不是第一个，跳到前一个
          this.$nextTick(() => {
            this.pairingCodeInput[index - 1]?.focus()
          })
        }
      } else if (event.key === 'ArrowLeft' && index > 0) {
        this.$nextTick(() => {
          this.pairingCodeInput[index - 1]?.focus()
        })
      } else if (event.key === 'ArrowRight' && index < 5) {
        this.$nextTick(() => {
          this.pairingCodeInput[index + 1]?.focus()
        })
      }
    },
    handlePairingCodePaste(index, event) {
      event.preventDefault()
      const pastedData = (event.clipboardData || window.clipboardData).getData('text')
      const digits = pastedData.replace(/\D/g, '').slice(0, 6)
      
      for (let i = 0; i < 6; i++) {
        this.pairingCode[i] = digits[i] || ''
      }
      
      this.$nextTick(() => {
        const nextEmptyIndex = this.pairingCode.findIndex(d => d === '')
        if (nextEmptyIndex !== -1) {
          this.pairingCodeInput[nextEmptyIndex]?.focus()
        } else {
          this.pairingCodeInput[5]?.focus()
        }
      })
    },
    submitPairingCode() {
      const code = this.pairingCode.join('')
      if (code.length !== 6) {
        voiceInteraction.speak('请输入完整的6位配对码')
        return
      }
      
      voiceInteraction.speak(`配对码${code}已提交，正在验证`)
      // 这里可以添加实际的配对码验证逻辑
      setTimeout(() => {
        this.closePairingCodeModal()
        voiceInteraction.speak('配对成功')
      }, 1500)
    }
  }
}
</script>

<style scoped>
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 700, 'GRAD' 0, 'opsz' 48;
  font-size: 48px;
}

input[type=range] {
  -webkit-appearance: none;
  width: 100%;
  background: transparent;
}

input[type=range]::-webkit-slider-thumb {
  -webkit-appearance: none;
  height: 48px;
  width: 24px;
  background: #ffffff;
  border: 4px solid #00008b;
  border-radius: 8px;
  cursor: pointer;
  margin-top: -16px;
}

input[type=range]::-webkit-slider-runnable-track {
  width: 100%;
  height: 16px;
  cursor: pointer;
  background: #00008b;
  border: 2px solid #ffffff;
  border-radius: 8px;
}
</style>