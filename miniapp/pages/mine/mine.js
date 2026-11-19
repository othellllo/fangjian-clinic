const api = require('../../utils/api.js')
const auth = require('../../utils/auth.js')
const config = require('../../config.js')

Page({
  data: {
    patient: null,
    version: config.APP_VERSION,
    appName: config.APP_NAME,
    baseURL: config.BASE_URL
  },

  onShow: function () {
    if (!auth.requireLogin()) return

    const cached = auth.getPatient()
    if (cached) {
      this.setData({ patient: cached })
    }
    this.loadProfile(!cached)
  },

  loadProfile: function (showLoading) {
    const self = this
    return api.profile({
      loading: showLoading,
      loadingText: '加载资料'
    }).then(function (patient) {
      if (patient) {
        self.setData({ patient: patient })
        auth.savePatient(patient)
      }
    }).catch(function () {
      // 失败时保留缓存资料，错误提示已由 request.js 统一处理
    })
  },

  goVisits: function () {
    wx.navigateTo({ url: '/pages/visits/visits' })
  },

  goAbout: function () {
    wx.showModal({
      title: config.APP_NAME + ' 患者端',
      content: '版本 v' + config.APP_VERSION +
        '\n预约挂号 / 我的预约 / 就诊记录 / 处方费用' +
        '\n接口地址：' + config.BASE_URL +
        '\n\n演示环境，仅供内部联调使用。',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  onLogout: function () {
    wx.showModal({
      title: '退出登录',
      content: '退出后需要重新使用手机号验证码登录，确定退出吗？',
      confirmText: '退出登录',
      confirmColor: '#c0453c',
      cancelText: '取消',
      success: function (res) {
        if (res.confirm) {
          auth.logout()
        }
      }
    })
  }
})
