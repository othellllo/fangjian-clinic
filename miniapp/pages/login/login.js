const api = require('../../utils/api.js')
const auth = require('../../utils/auth.js')
const config = require('../../config.js')

const PHONE_RE = /^1\d{10}$/

Page({
  data: {
    phone: '',
    code: '',
    countdown: 0,
    canSendCode: false,
    canLogin: false,
    submitting: false,
    devCode: config.DEV_SMS_CODE
  },

  onLoad: function () {
    // 已有登录态（例如开发者工具热启动）直接进预约首页
    if (auth.isLoggedIn()) {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  onUnload: function () {
    this.clearTimer()
  },

  onHide: function () {
    this.clearTimer()
  },

  clearTimer: function () {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  refreshButtonState: function () {
    const phoneOk = PHONE_RE.test(this.data.phone)
    const codeOk = this.data.code.length >= 4
    this.setData({
      canSendCode: phoneOk && this.data.countdown === 0,
      canLogin: phoneOk && codeOk && !this.data.submitting
    })
  },

  onPhoneInput: function (e) {
    const value = String(e.detail.value || '').replace(/\D/g, '').slice(0, 11)
    this.setData({ phone: value })
    this.refreshButtonState()
  },

  onCodeInput: function (e) {
    const value = String(e.detail.value || '').replace(/\D/g, '').slice(0, 6)
    this.setData({ code: value })
    this.refreshButtonState()
  },

  startCountdown: function () {
    const self = this
    this.clearTimer()
    this.timer = setInterval(function () {
      const next = self.data.countdown - 1
      if (next <= 0) {
        self.clearTimer()
        self.setData({ countdown: 0 })
        self.refreshButtonState()
      } else {
        self.setData({ countdown: next })
      }
    }, 1000)
  },

  onSendCode: function () {
    if (this.data.countdown > 0) return

    if (!PHONE_RE.test(this.data.phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }

    // 演示环境不真正发短信，后端固定校验 123456
    this.setData({ countdown: 60 })
    this.refreshButtonState()
    this.startCountdown()
    wx.showToast({ title: '演示验证码 ' + config.DEV_SMS_CODE, icon: 'none', duration: 2600 })
  },

  onLogin: function () {
    if (this.data.submitting) return

    const phone = this.data.phone
    const code = this.data.code

    if (!PHONE_RE.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    if (code.length < 4) {
      wx.showToast({ title: '请输入验证码', icon: 'none' })
      return
    }

    const self = this
    this.setData({ submitting: true })
    this.refreshButtonState()

    api.login(phone, code).then(function (res) {
      if (!res || !res.token) {
        const err = new Error('登录失败，请稍后重试')
        err.synthetic = true
        throw err
      }
      auth.saveAuth(res.token, res.patient)
      self.clearTimer()
      wx.showToast({ title: '登录成功', icon: 'success', duration: 1200 })
      setTimeout(function () {
        wx.switchTab({ url: '/pages/index/index' })
      }, 800)
    }).catch(function (err) {
      self.setData({ submitting: false })
      self.refreshButtonState()
      // 业务错误已由 request.js 统一 toast；这里只补提示本地异常
      if (err && err.synthetic) {
        wx.showToast({ title: err.message, icon: 'none' })
      }
    })
  }
})
