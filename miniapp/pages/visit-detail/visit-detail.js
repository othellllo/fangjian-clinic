const api = require('../../utils/api.js')
const auth = require('../../utils/auth.js')

Page({
  data: {
    id: '',
    loading: true,
    error: false,
    visit: null,
    patient: {},
    recordSections: [],
    recordUpdatedText: '',
    hasRecord: false,
    prescriptions: [],
    hasPrescriptions: false,
    charges: [],
    hasCharges: false,
    chargeTotalText: '0.00'
  },

  onLoad: function (options) {
    if (!auth.requireLogin()) return
    const id = (options && options.id) || ''
    if (!id) {
      this.setData({ loading: false, error: true })
      return
    }
    this.setData({ id: id })
    this.loadDetail(true)
  },

  onPullDownRefresh: function () {
    if (!auth.requireLogin()) {
      wx.stopPullDownRefresh()
      return
    }
    this.loadDetail(false).then(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadDetail: function (showLoading) {
    const self = this

    this.setData({
      loading: !this.data.visit,
      error: false
    })

    return api.visitDetail(this.data.id, {
      loading: showLoading,
      loadingText: '加载详情'
    }).then(function (detail) {
      self.setData({
        visit: detail.visit,
        patient: detail.patient || {},
        recordSections: detail.recordSections,
        recordUpdatedText: detail.recordUpdatedText,
        hasRecord: detail.hasRecord,
        prescriptions: detail.prescriptions,
        hasPrescriptions: detail.hasPrescriptions,
        charges: detail.charges,
        hasCharges: detail.hasCharges,
        chargeTotalText: detail.chargeTotalText,
        loading: false,
        error: false
      })
    }).catch(function () {
      self.setData({
        loading: false,
        error: !self.data.visit
      })
    })
  },

  onRetry: function () {
    if (!this.data.id) {
      wx.showToast({ title: '缺少就诊记录 ID', icon: 'none' })
      return
    }
    this.loadDetail(true)
  }
})
