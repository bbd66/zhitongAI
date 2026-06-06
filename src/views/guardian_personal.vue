<template>
  <div class="min-h-screen bg-zinc-900 flex items-center justify-center">
    <div class="w-[414px] bg-black text-white min-h-screen relative">
      <header class="fixed top-0 left-1/2 -translate-x-1/2 w-[414px] border-b-4 border-white bg-black z-40 flex justify-between items-center px-6 h-24">
        <div class="flex items-center">
          <h1 class="font-lexend font-black uppercase text-3xl text-white">智瞳</h1>
        </div>
        <div class="flex items-center"><span class="material-symbols-outlined text-white" style="font-size: 32px;">star</span></div>
      </header>

      <main class="pt-24 pb-28 px-6 w-full overflow-y-auto space-y-6">
        <section class="border-4 border-white bg-black p-6 flex items-center justify-between">
          <div class="flex items-center gap-6">
            <div class="w-24 h-24 bg-primary-container border-4 border-white flex items-center justify-center">
              <span class="material-symbols-outlined text-white" style="font-size: 80px;">person</span>
            </div>
            <div class="flex flex-col gap-2">
              <h2 class="font-headline-md text-white">{{ guardianInfo.name }}</h2>
              <span class="bg-primary-container text-white font-label-lg px-4 py-1 border-2 border-white inline-block w-fit">已实名认证</span>
            </div>
          </div>
          <button 
            class="bg-blue-900 border-2 border-white rounded-lg px-4 py-2 flex items-center gap-2 active:scale-95 transition-transform"
            @click="showEditForm = true"
          >
            <span class="material-symbols-outlined" style="font-size: 24px;">edit</span>
            <span class="font-body-sm">编辑</span>
          </button>
        </section>

        <section class="border-4 border-white bg-surface-container rounded-lg p-6">
          <button 
            class="w-full bg-black border-4 border-white rounded-xl h-20 flex items-center justify-center gap-3 active:scale-95 transition-transform text-white font-button-text"
            @click="goToStartPage"
          >
            <span class="material-symbols-outlined">grid_view</span>
            <span>切换模式</span>
          </button>
        </section>

        <section class="space-y-4">
          <h3 class="font-headline-md text-white uppercase tracking-widest border-l-8 border-primary-container pl-4">我的亲属</h3>
          <div class="border-4 border-white bg-black">
            <div class="flex items-center gap-6 p-6">
              <div class="w-24 h-24 bg-primary-container border-4 border-white flex items-center justify-center">
                <span class="material-symbols-outlined text-white" style="font-size: 80px;">family_history</span>
              </div>
              <div class="flex flex-col gap-2">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary-container" style="font-size: 32px;">person</span>
                  <h2 class="font-body-lg text-white uppercase">{{ guardianInfo.relativeName }}</h2>
                </div>
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary-container" style="font-size: 32px;">call</span>
                  <span class="bg-primary-container text-white font-label-lg px-4 py-1 border-2 border-white inline-block w-fit">{{ guardianInfo.relativePhone }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="grid grid-cols-2 gap-6">
          <button class="flex items-center justify-start gap-6 border-4 border-white bg-black p-6 min-h-[100px] active:scale-95 transition-transform hover:border-primary-container group" @click="showPairingCodeModal = true">
            <span class="material-symbols-outlined text-white group-hover:text-primary-container" style="font-size: 48px;">link</span>
            <span class="font-button-text text-white">配对码</span>
          </button>

          <button class="flex items-center justify-start gap-6 border-4 border-white bg-black p-6 min-h-[100px] active:scale-95 transition-transform hover:border-primary-container group">
            <span class="material-symbols-outlined text-white group-hover:text-primary-container" style="font-size: 48px;">menu_book</span>
            <span class="font-button-text text-white">使用指南</span>
          </button>

          <button class="flex items-center justify-start gap-6 border-4 border-white bg-black p-6 min-h-[100px] active:scale-95 transition-transform hover:border-primary-container group">
            <span class="material-symbols-outlined text-white group-hover:text-primary-container" style="font-size: 48px;">chat_bubble</span>
            <span class="font-button-text text-white">意见反馈</span>
          </button>

          <button class="flex items-center justify-start gap-6 border-4 border-white bg-black p-6 min-h-[100px] active:scale-95 transition-transform hover:border-primary-container group">
            <span class="material-symbols-outlined text-white group-hover:text-primary-container" style="font-size: 48px;">settings</span>
            <span class="font-button-text text-white">系统设置</span>
          </button>

          <router-link to="/" class="flex items-center justify-start gap-6 border-4 border-white bg-black p-6 min-h-[100px] active:scale-95 transition-transform hover:border-error-container group col-span-2">
            <span class="material-symbols-outlined text-white group-hover:text-error" style="font-size: 48px;">logout</span>
            <span class="font-button-text text-white">退出登录</span>
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
              <label class="font-body-sm text-gray-400 block mb-2">监护人名称</label>
              <input 
                v-model="guardianInfo.name" 
                type="text" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入监护人名称"
              />
            </div>
            <div class="bg-black border-2 rounded-lg p-4" :class="{'border-red-500': guardianInfo.phone && !isValidPhone(guardianInfo.phone), 'border-white': !guardianInfo.phone || isValidPhone(guardianInfo.phone)}">
              <label class="font-body-sm text-gray-400 block mb-2">联系电话</label>
              <input 
                v-model="guardianInfo.phone" 
                type="tel" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入联系电话"
                maxlength="11"
                @input="filterPhoneInput('phone', $event)"
              />
              <p v-if="guardianInfo.phone && !isValidPhone(guardianInfo.phone)" class="text-red-500 text-sm mt-1">请输入11位数字的手机号码</p>
            </div>
            <div class="bg-black border-2 border-white rounded-lg p-4">
              <label class="font-body-sm text-gray-400 block mb-2">亲属名称</label>
              <input 
                v-model="guardianInfo.relativeName" 
                type="text" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入亲属名称"
              />
            </div>
            <div class="bg-black border-2 rounded-lg p-4" :class="{'border-red-500': guardianInfo.relativePhone && !isValidPhone(guardianInfo.relativePhone), 'border-white': !guardianInfo.relativePhone || isValidPhone(guardianInfo.relativePhone)}">
              <label class="font-body-sm text-gray-400 block mb-2">亲属联系电话</label>
              <input 
                v-model="guardianInfo.relativePhone" 
                type="tel" 
                class="bg-transparent text-white font-body-lg w-full focus:outline-none"
                placeholder="请输入亲属联系电话"
                maxlength="11"
                @input="filterPhoneInput('relativePhone', $event)"
              />
              <p v-if="guardianInfo.relativePhone && !isValidPhone(guardianInfo.relativePhone)" class="text-red-500 text-sm mt-1">请输入11位数字的手机号码</p>
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
              class="w-14 h-16 bg-black border-4 border-white rounded-lg text-center text-3xl font-bold text-white focus:outline-none focus:border-primary-container"
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

      <nav class="fixed bottom-0 left-1/2 -translate-x-1/2 w-[414px] border-t-4 border-white bg-black flex justify-around items-stretch h-28 z-50">
        <router-link class="text-white flex flex-col items-center justify-center h-full flex-1 hover:bg-zinc-800 transition-colors" to="/guardian01">
          <span class="material-symbols-outlined">home</span>
          <span class="font-lexend font-bold text-lg uppercase">首页</span>
        </router-link>
        <router-link class="bg-[#00008B] text-white flex flex-col items-center justify-center h-full flex-1 border-x-4 border-white active:opacity-80" to="/guardian_personal">
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
  name: 'guardian_personal',
  data() {
    return {
      showEditForm: false,
      showPairingCodeModal: false,
      pairingCode: ['', '', '', '', '', ''],
      pairingCodeInput: [null, null, null, null, null, null],
      guardianInfo: {
        name: '监护人名称',
        phone: '',
        relativeName: '亲属姓名',
        relativePhone: '138 **** 5678'
      }
    }
  },
  mounted() {
    setTimeout(() => {
      voiceInteraction.announcePageWithFeatures('/guardian_personal', [
        '查看亲属信息', '系统设置', '使用指南', '意见反馈'
      ], true) // true = 播报完成后自动重新启动语音识别
    }, 500)
  },
  methods: {
    filterPhoneInput(field, event) {
      const value = event.target.value
      const filtered = value.replace(/\D/g, '').slice(0, 11)
      this.guardianInfo[field] = filtered
    },
    isValidPhone(phone) {
      return /^\d{11}$/.test(phone)
    },
    savePersonalInfo() {
      if (this.guardianInfo.phone && !this.isValidPhone(this.guardianInfo.phone)) {
        voiceInteraction.speak('联系电话格式不正确')
        return
      }
      if (this.guardianInfo.relativePhone && !this.isValidPhone(this.guardianInfo.relativePhone)) {
        voiceInteraction.speak('亲属联系电话格式不正确')
        return
      }
      if (this.guardianInfo.relativePhone && this.isValidPhone(this.guardianInfo.relativePhone)) {
        this.guardianInfo.relativePhone = this.guardianInfo.relativePhone.replace(/(\d{3})\d{4}(\d{4})/, '$1 **** $2')
      }
      this.showEditForm = false
      voiceInteraction.speak('个人信息保存成功')
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
}
</style>