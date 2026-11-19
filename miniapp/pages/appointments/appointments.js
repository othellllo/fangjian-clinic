const api = require('../../utils/api.js')
const auth = require('../../utils/auth.js')

Page({
  data: {
    loading: true,
    loaded: false,
    error: false,
    upcoming: [],
    history: []
  },

  onShow: function () {
    if (!auth.requireLogin()) return
    this.loadAppointments(!this.data.loaded)
  },

  onPullDownRefresh: function () {
    if (!auth.requireLogin()) {
      wx.stopPullDownRefresh()
      return
    }
    this.loadAppointments(false).then(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadAppointments: function (showLoading) {
    const self = this
    this.setData({ loading: !this.data.loaded, error: false })

    return api.appointments({
      loading: showLoading,
      loadingText: '加载预约'
    }).then(function (list) {
      const groups = api.splitAppointments(list)
      self.setData({
        upcoming: groups.upcoming,
        history: groups.history,
        loading: false,
        loaded: true,
        error: false
      })
    }).catch(function () {
      self.setData({
        loading: false,
        error: !self.data.loaded
      })
    })
  },

  onRetry: function () {
    this.loadAppointments(true)
  },

  goBooking: function () {
    wx.switchTab({ url: '/pages/index/index' })
  },

  goVisitDetail: function (e) {
    const visitId = e.currentTarget.dataset.visitId
    if (!visitId) return
    wx.navigateTo({ url: '/pages/visit-detail/visit-detail?id=' + visitId })
  },

  onCancel: function (e) {
    const id = e.currentTarget.dataset.id
    const self = this

    wx.showModal({
      title: '取消预约',
      content: '取消后如需就诊请重新预约，确定取消吗？',
      confirmText: '确定取消',
      confirmColor: '#c0453c',
      cancelText: '暂不取消',
      success: function (res) {
        if (!res.confirm) return
        api.cancelAppointment(id, {
          loading: true,
          loadingText: '取消中'
        }).then(function () {
          wx.showToast({ title: '已取消预约', icon: 'success' })
          self.loadAppointments(false)
        }).catch(function () {
          // 失败提示已由 request.js 统一处理
        })
      }
    })
  }
})
