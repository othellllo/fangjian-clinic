/**
 * 医馆 HIS 患者端小程序入口
 */
const config = require('./config.js')
const auth = require('./utils/auth.js')

App({
  globalData: {
    appName: config.APP_NAME,
    version: config.APP_VERSION,
    baseURL: config.BASE_URL,
    loggedIn: false,
    patient: null
  },

  onLaunch: function () {
    // 预读登录态，页面里仍会各自做一次校验
    this.globalData.loggedIn = auth.isLoggedIn()
    this.globalData.patient = auth.getPatient()
    console.log('[医馆 HIS] 患者端启动，接口地址：' + config.BASE_URL)
  },

  onShow: function () {
    this.globalData.loggedIn = auth.isLoggedIn()
  },

  onError: function (err) {
    console.error('[医馆 HIS] 未捕获异常：', err)
  }
})
