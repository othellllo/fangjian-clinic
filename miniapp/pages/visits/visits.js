const api = require('../../utils/api.js')
const auth = require('../../utils/auth.js')
const config = require('../../config.js')

Page({
  data: {
    visits: [],
    page: 0,
    size: config.PAGE_SIZE,
    total: 0,
    hasMore: false,
    loading: false,
    loadingMore: false,
    loaded: false,
    error: false
  },

  onShow: function () {
    if (!auth.requireLogin()) return
    if (!this.data.loaded && !this.data.loading) {
      this.loadList(true, true)
    }
  },

  onPullDownRefresh: function () {
    if (!auth.requireLogin()) {
      wx.stopPullDownRefresh()
      return
    }
    this.loadList(true, false).then(function () {
      wx.stopPullDownRefresh()
    })
  },

  onReachBottom: function () {
    if (!this.data.hasMore || this.data.loadingMore || this.data.loading) return
    this.loadList(false, true)
  },

  loadList: function (reset, showLoading) {
    if (reset) {
      if (this.data.loading) return Promise.resolve()
      this.setData({ loading: true, error: false })
    } else {
      if (this.data.loadingMore) return Promise.resolve()
      this.setData({ loadingMore: true })
    }

    const self = this
    const pageNo = reset ? 1 : this.data.page + 1

    return api.visits(pageNo, this.data.size, {
      loading: showLoading,
      loadingText: '加载中'
    }).then(function (res) {
      const list = reset ? res.list : self.data.visits.concat(res.list)
      self.setData({
        visits: list,
        page: pageNo,
        total: res.total,
        hasMore: list.length < res.total,
        loading: false,
        loadingMore: false,
        loaded: true,
        error: false
      })
    }).catch(function () {
      self.setData({
        loading: false,
        loadingMore: false,
        error: reset && !self.data.loaded
      })
    })
  },

  onRetry: function () {
    this.loadList(true, true)
  },

  goDetail: function (e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/visit-detail/visit-detail?id=' + id })
  },

  goBooking: function () {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
