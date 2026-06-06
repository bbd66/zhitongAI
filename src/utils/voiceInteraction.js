/**
 * 语音交互服务 - 完全使用端侧原生实现（离线可用）
 * 模式：按住说话（Push-to-Talk），松开识别
 */

import { NativeASR } from '../capacitor/plugins/NativeASR/NativeASR.js'
import { NativeTTS } from '../capacitor/plugins/NativeTTS/NativeTTS.js'

class VoiceInteractionService {
  constructor() {
    this.isSpeaking = false
    this.isListening = false
    this.isModelLoaded = false

    this.onCommand = null
    this.onNavigate = null

    this.volume = 1.0
    this.rate = 1.0
    this.pitch = 1.0
    this.currentPage = ''

    this.pageInfo = {
      '/': '起始页',
      '/selectmodle': '模式选择页',
      '/user01': '用户主页',
      '/guardian01': '监护人主页',
      '/camerapage': '相机拍摄页',
      '/userpersonal': '用户个人中心',
      '/guardian_personal': '监护人个人中心',
      '/emergencepage': '紧急处理页',
      '/sospage': '紧急求助页'
    }

    this.commandHistory = []
    this.lastCommandTime = 0
    this.commandDebounceMs = 1500

    this.nativeASRAvailable = false
    this.nativeTTSAvailable = false

    this.errorMessages = {
      'network': '网络连接异常，请检查网络设置',
      'not-allowed': '麦克风权限被拒绝',
      'service-not-allowed': '语音服务不可用',
      'bad-grammar': '语音识别语法错误',
      'language-not-supported': '不支持的语言',
      'no-speech': '未检测到语音',
      'aborted': '语音识别已取消',
      'audio-capture': '无法访问麦克风设备',
      'unknown': '未知错误'
    }
  }

  getErrorMessage(errorCode) {
    const code = errorCode.toLowerCase()
    return this.errorMessages[code] || this.errorMessages['unknown']
  }

  setVolume(volume) {
    this.volume = Math.max(0, Math.min(100, volume)) / 100
  }

  getVolume() {
    return Math.round(this.volume * 100)
  }

  setRate(rate) {
    this.rate = Math.max(0.5, Math.min(2.0, rate))
  }

  getRate() {
    return this.rate
  }

  // ==================== TTS 播报 ====================

  /**
   * 播报文本（按住说话模式下，播报期间不影响麦克风按钮）
   */
  async speak(text, lang = 'zh-CN') {
    if (!this.nativeTTSAvailable) {
      console.warn('语音合成不可用，请安装原生应用')
      return
    }

    try {
      this.isSpeaking = true
      await NativeTTS.speak({
        text: text,
        lang: lang,
        rate: this.rate,
        pitch: this.pitch
      })
      this.isSpeaking = false
    } catch (error) {
      console.error('原生语音合成失败:', error)
      this.isSpeaking = false
    }
  }

  /** 立即停止播报 */
  stop() {
    if (this.nativeTTSAvailable) {
      NativeTTS.stop()
    }
    this.isSpeaking = false
  }

  async announcePage(pagePath, extraInfo = '') {
    const pageName = this.pageInfo[pagePath] || '未知页面'
    this.currentPage = pagePath
    let text = `已进入${pageName}`
    if (extraInfo) {
      text += `，${extraInfo}`
    }
    await this.speak(text)
  }

  async announcePageWithFeatures(pagePath, features = []) {
    const pageName = this.pageInfo[pagePath] || '未知页面'
    this.currentPage = pagePath
    let text = `已进入${pageName}。`
    if (features.length > 0) {
      text += `主要功能：${features.join('、')}。`
    }
    await this.speak(text)
  }

  setCurrentPage(pagePath) {
    this.currentPage = pagePath
  }

  getCurrentPage() {
    return this.currentPage
  }

  getCurrentPageName() {
    return this.pageInfo[this.currentPage] || '未知页面'
  }

  // ==================== 初始化 & 模型加载 ====================

  async init(onCommand, onNavigate) {
    this.onCommand = onCommand
    this.onNavigate = onNavigate

    try {
      const info = await NativeASR.isInitialized()

      if (info.initializing) {
        console.log('模型正在初始化中，等待完成...')
        await this.waitForInitialization()
      }

      if (info.value) {
        this.nativeASRAvailable = true
        console.log('模型已初始化')
      } else {
        console.log('开始初始化模型...')
        const result = await NativeASR.initialize()
        if (result.success) {
          this.nativeASRAvailable = true
          console.log('模型初始化成功')
        } else {
          console.error('模型初始化失败:', result.error)
        }
      }
    } catch (e) {
      console.error('Native ASR not available:', e)
      this.nativeASRAvailable = false
    }

    try {
      const ttsResult = await NativeTTS.isSupported()
      if (ttsResult.value) {
        this.nativeTTSAvailable = true
      }
    } catch (e) {
      console.log('Native TTS not available')
      this.nativeTTSAvailable = false
    }

    return {
      ttsAvailable: this.nativeTTSAvailable,
      sttAvailable: this.nativeASRAvailable,
      modelLoaded: this.isModelLoaded || this.nativeASRAvailable
    }
  }

  async waitForInitialization() {
    return new Promise((resolve) => {
      let count = 0
      const maxCount = 50
      const check = async () => {
        const info = await NativeASR.isInitialized()
        if (info.value || count >= maxCount) {
          resolve()
          return
        }
        count++
        setTimeout(check, 100)
      }
      check()
    })
  }

  async loadModel() {
    if (!this.nativeASRAvailable) {
      await this.speak('请安装原生应用以使用语音功能')
      return false
    }

    try {
      const info = await NativeASR.isInitialized()

      if (info.initializing) {
        await this.waitForInitialization()
      }

      const checkResult = await NativeASR.isInitialized()
      if (checkResult.value) {
        this.isModelLoaded = true
        await this.speak('语音识别已就绪')
        return true
      } else {
        const result = await NativeASR.initialize()
        if (result.success) {
          this.isModelLoaded = true
          await this.speak('语音识别已就绪')
          return true
        } else {
          await this.speak('模型加载失败')
          return false
        }
      }
    } catch (error) {
      console.error('加载模型失败:', error)
      await this.speak('模型加载失败')
      return false
    }
  }

  // ==================== 按住说话（Push-to-Talk） ====================

  /**
   * 按住麦克风按钮时调用：停止播报 + 开始监听
   */
  async startPushToTalk() {
    console.log('startPushToTalk - isSpeaking:', this.isSpeaking, 'isListening:', this.isListening)
    
    // 强制停止播报（不依赖 isSpeaking 状态）
    if (this.nativeTTSAvailable) {
      try {
        await NativeTTS.stop()
        console.log('已强制停止播报')
      } catch (e) {
        console.log('停止播报时出错:', e)
      }
    }
    this.isSpeaking = false

    if (!this.nativeASRAvailable) {
      console.warn('语音识别不可用')
      return
    }

    if (this.isListening) {
      console.log('已经在监听中')
      return
    }

    try {
      const result = await NativeASR.startListening()
      if (result.success) {
        this.isListening = true
        console.log('开始语音监听（按住说话）')
      } else {
        console.error('启动语音识别失败:', result)
      }
    } catch (error) {
      console.error('启动语音识别失败:', error)
    }
  }

  /**
   * 松开麦克风按钮时调用：停止监听 → 获取识别结果 → 处理命令
   * @returns {string|null} 识别到的文本
   */
  async stopPushToTalk() {
    if (!this.isListening) return null

    this.isListening = false

    try {
      const result = await NativeASR.stopListening()
      const text = (result && result.text) || ''

      if (text.trim()) {
        console.log('识别结果:', text)
        await this.handleCommand(text)
        return text
      } else {
        console.log('未检测到语音')
        return null
      }
    } catch (error) {
      console.error('停止语音识别失败:', error)
      return null
    }
  }

  // ==================== 命令处理 ====================

  async handleCommand(transcript) {
    // 防抖
    const now = Date.now()
    if (now - this.lastCommandTime < this.commandDebounceMs) {
      return
    }
    this.lastCommandTime = now

    const lowerTranscript = transcript.toLowerCase()
    console.log('处理命令:', lowerTranscript)

    if (this.isQueryPageCommand(lowerTranscript)) {
      await this.handleQueryPage()
    } else if (this.isNavigationCommand(lowerTranscript)) {
      const route = this.extractNavigationRoute(lowerTranscript)
      if (route) {
        await this.handleNavigation(route)
      }
    } else if (this.isHelpCommand(lowerTranscript)) {
      await this.handleHelp()
    } else if (this.isStopCommand(lowerTranscript)) {
      // 忽略（按住说话模式下没有"关闭语音"的概念）
      console.log('忽略关闭语音指令（按住说话模式）')
    } else if (this.isVolumeCommand(lowerTranscript)) {
      await this.handleVolumeCommand(lowerTranscript)
    } else if (this.isBrightnessCommand(lowerTranscript)) {
      await this.handleBrightnessCommand(lowerTranscript)
    }
  }

  isQueryPageCommand(text) {
    const patterns = [
      '现在是什么页面',
      '当前页面',
      '这是什么页面',
      '我在哪里',
      '当前在哪个页面'
    ]
    return patterns.some(p => text.includes(p))
  }

  isNavigationCommand(text) {
    const navPatterns = ['进入', '打开', '去', '跳转到', '导航到', '前往']
    return navPatterns.some(p => text.includes(p))
  }

  isHelpCommand(text) {
    return text.includes('帮助') || text.includes('指令') ||
           text.includes('怎么说') || text.includes('有什么命令')
  }

  isStopCommand(text) {
    return text.includes('关闭语音') || text.includes('停止语音') ||
           text.includes('关闭助手')
  }

  isVolumeCommand(text) {
    return text.includes('音量') || text.includes('声音') ||
           text.includes('大声') || text.includes('小声')
  }

  isBrightnessCommand(text) {
    return text.includes('亮度') || text.includes('屏幕亮度') ||
           text.includes('屏幕') || text.includes('亮一点') ||
           text.includes('暗一点')
  }

  async handleQueryPage() {
    const pageName = this.getCurrentPageName()
    await this.speak(`当前页面是${pageName}`)
  }

  extractNavigationRoute(text) {
    const routeMap = {
      '起始页': '/',
      '首页': '/user01',
      '主页': '/user01',
      '用户主页': '/user01',
      '用户首页': '/user01',
      '模式选择': '/selectmodle',
      '选择模式': '/selectmodle',
      '相机': '/camerapage',
      '拍摄': '/camerapage',
      '拍照': '/camerapage',
      '个人中心': '/userpersonal',
      '个人': '/userpersonal',
      '用户个人': '/userpersonal',
      '紧急': '/emergencepage',
      '紧急处理': '/emergencepage',
      '求助': '/emergencepage',
      '监护人': '/guardian01',
      '监护人主页': '/guardian01',
      '监护人个人': '/guardian_personal',
      '求救': '/sospage',
      '紧急求助': '/sospage'
    }

    for (const [keyword, route] of Object.entries(routeMap)) {
      if (text.includes(keyword)) {
        return route
      }
    }
    return null
  }

  async handleNavigation(route) {
    const pageName = this.pageInfo[route] || '目标页面'
    await this.speak(`正在进入${pageName}`)

    if (this.onNavigate) {
      this.onNavigate(route)
    }

    if (this.onCommand) {
      this.onCommand({
        type: 'navigate',
        route: route,
        pageName: pageName
      })
    }
  }

  async handleHelp() {
    const helpText = `可用语音指令包括：现在是什么页面，进入首页，进入相机，进入个人中心，关闭语音，音量调大，音量调小，亮度调亮，亮度调暗，说帮助。`
    await this.speak(helpText)
  }

  async handleVolumeCommand(text) {
    if (text.includes('大') || text.includes('增')) {
      this.setVolume(this.getVolume() + 20)
      await this.speak(`音量已增大，当前音量${this.getVolume()}%`)
    } else if (text.includes('小') || text.includes('减')) {
      this.setVolume(this.getVolume() - 20)
      await this.speak(`音量已减小，当前音量${this.getVolume()}%`)
    } else {
      await this.speak(`当前音量${this.getVolume()}%`)
    }
  }

  async handleBrightnessCommand(text) {
    await this.speak('亮度调节需要在系统设置中操作，请下拉通知栏，点击亮度设置进行调整')
  }

  // ==================== 查询状态 ====================

  isSupported() {
    return this.nativeASRAvailable && this.nativeTTSAvailable
  }

  isListeningState() {
    return this.isListening
  }

  isSpeakingState() {
    return this.isSpeaking
  }

  isModelLoadedState() {
    return this.isModelLoaded || this.nativeASRAvailable
  }

  destroy() {
    this.stop()
  }
}

export default new VoiceInteractionService()