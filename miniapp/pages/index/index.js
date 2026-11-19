const api = require('../../utils/api.js')
const auth = require('../../utils/auth.js')
const format = require('../../utils/format.js')
const config = require('../../config.js')

function markDoctors(list, selectedId) {
  return (list || []).map(function (item) {
    return {
      id: item.id,
      name: item.name,
      title: item.title,
      dept: item.dept,
      intro: item.intro,
      initial: item.initial,
      selected: selectedId !== null && selectedId !== undefined && String(item.id) === String(selectedId)
    }
  })
}

function markDates(list, selectedDate) {
  return (list || []).map(function (item) {
    return {
      value: item.value,
      week: item.week,
      day: item.day,
      isToday: item.isToday,
      active: item.value === selectedDate
    }
  })
}

function markSlots(list, selectedSlot) {
  return (list || []).map(function (item) {
    return {
      timeSlot: item.timeSlot,
      key: item.key,
      capacity: item.capacity,
      booked: item.booked,
      remaining: item.remaining,
      full: item.full,
      metaText: item.metaText,
      selected: !!selectedSlot && item.timeSlot === selectedSlot
    }
  })
}

Page({
  data: {
    dates: [],
    selectedDate: '',
    selectedDateText: '',
    doctors: [],
    loadingDoctors: false,
    doctorsError: false,
    selectedDoctorId: null,
    selectedDoctorName: '',
    slots: [],
    loadingSlots: false,
    slotsError: false,
    slotsLoaded: false,
    selectedSlot: '',
    complaint: '',
    submitting: false,
    summaryText: '请选择医生和时段',
    summarySub: '提交后请按时到店就诊'
  },

  onLoad: function () {
    const dates = format.buildBookingDates(config.BOOKING_DAYS)
    const first = dates[0]
    this.slotRequestToken = 0
    this.setData({
      dates: markDates(dates, first.value),
      selectedDate: first.value,
      selectedDateText: first.week + ' ' + first.day
    })
  },

  onShow: function () {
    if (!auth.requireLogin()) return
    if (!this.data.doctors.length && !this.data.loadingDoctors) {
      this.loadDoctors(true)
      return
    }
    // 回到本页时静默刷新时段余量（已有选中时段则不打扰）
    if (this.data.selectedDoctorId && this.data.slotsLoaded && !this.data.selectedSlot) {
      this.loadSlots(false)
    }
  },

  onPullDownRefresh: function () {
    if (!auth.requireLogin()) {
      wx.stopPullDownRefresh()
      return
    }
    const self = this
    this.loadDoctors(false).then(function () {
      if (self.data.selectedDoctorId) {
        return self.loadSlots(false)
      }
      return null
    }).then(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadDoctors: function (showLoading) {
    const self = this
    this.setData({ loadingDoctors: true, doctorsError: false })

    return api.doctors({ loading: showLoading, loadingText: '加载医生' }).then(function (list) {
      let selectedId = self.data.selectedDoctorId
      let selectedName = self.data.selectedDoctorName

      if (selectedId !== null && selectedId !== undefined) {
        let found = null
        for (let i = 0; i < list.length; i++) {
          if (String(list[i].id) === String(selectedId)) {
            found = list[i]
            break
          }
        }
        if (!found) {
          selectedId = null
          selectedName = ''
        }
      }

      const patch = {
        doctors: markDoctors(list, selectedId),
        loadingDoctors: false,
        selectedDoctorId: selectedId,
        selectedDoctorName: selectedName
      }
      if (selectedId === null) {
        patch.slots = []
        patch.slotsLoaded = false
        patch.selectedSlot = ''
      }
      self.setData(patch)
      self.refreshSummary()
    }).catch(function () {
      self.setData({
        loadingDoctors: false,
        doctorsError: !self.data.doctors.length
      })
    })
  },

  onRetryDoctors: function () {
    this.loadDoctors(true)
  },

  onSelectDoctor: function (e) {
    const id = e.currentTarget.dataset.id
    let target = null
    for (let i = 0; i < this.data.doctors.length; i++) {
      if (String(this.data.doctors[i].id) === String(id)) {
        target = this.data.doctors[i]
        break
      }
    }
    if (!target) return

    this.setData({
      doctors: markDoctors(this.data.doctors, target.id),
      selectedDoctorId: target.id,
      selectedDoctorName: target.name,
      selectedSlot: ''
    })
    this.refreshSummary()
    this.loadSlots(true)
  },

  onSelectDate: function (e) {
    const value = e.currentTarget.dataset.value
    if (!value || value === this.data.selectedDate) return

    let target = null
    for (let i = 0; i < this.data.dates.length; i++) {
      if (this.data.dates[i].value === value) {
        target = this.data.dates[i]
        break
      }
    }
    if (!target) return

    this.setData({
      dates: markDates(this.data.dates, value),
      selectedDate: value,
      selectedDateText: target.week + ' ' + target.day,
      selectedSlot: ''
    })
    this.refreshSummary()
    this.loadSlots(true)
  },

  loadSlots: function (showLoading) {
    const doctorId = this.data.selectedDoctorId
    const date = this.data.selectedDate

    if (!doctorId || !date) {
      this.setData({ slots: [], slotsLoaded: false, loadingSlots: false, slotsError: false })
      return Promise.resolve()
    }

    const self = this
    this.slotRequestToken = (this.slotRequestToken || 0) + 1
    const token = this.slotRequestToken

    this.setData({
      loadingSlots: true,
      slotsError: false,
      slots: [],
      selectedSlot: ''
    })
    this.refreshSummary()

    return api.slots(doctorId, date, {
      loading: showLoading,
      loadingText: '加载时段'
    }).then(function (list) {
      if (token !== self.slotRequestToken) return
      self.setData({
        slots: markSlots(list, ''),
        loadingSlots: false,
        slotsLoaded: true
      })
    }).catch(function () {
      if (token !== self.slotRequestToken) return
      self.setData({
        slots: [],
        loadingSlots: false,
        slotsLoaded: true,
        slotsError: true
      })
    })
  },

  onRetrySlots: function () {
    this.loadSlots(true)
  },

  onSelectSlot: function (e) {
    const timeSlot = e.currentTarget.dataset.slot
    if (!timeSlot) return

    let target = null
    for (let i = 0; i < this.data.slots.length; i++) {
      if (this.data.slots[i].timeSlot === timeSlot) {
        target = this.data.slots[i]
        break
      }
    }
    if (!target) return
    if (target.full) {
      wx.showToast({ title: '该时段已约满', icon: 'none' })
      return
    }

    const nextSlot = this.data.selectedSlot === timeSlot ? '' : timeSlot
    this.setData({
      slots: markSlots(this.data.slots, nextSlot),
      selectedSlot: nextSlot
    })
    this.refreshSummary()
  },

  refreshSummary: function () {
    const doctorName = this.data.selectedDoctorName
    const slot = this.data.selectedSlot
    const dateText = this.data.selectedDateText

    let main = '请选择医生和时段'
    let sub = '提交后请按时到店就诊'
    if (doctorName && slot) {
      main = doctorName + ' · ' + dateText + ' ' + slot
      sub = '请提前 10 分钟到店前台报到'
    } else if (doctorName) {
      main = doctorName + ' · 待选时段'
      sub = '请选择就诊日期与时段'
    }

    this.setData({ summaryText: main, summarySub: sub })
  },

  onComplaintInput: function (e) {
    this.setData({ complaint: e.detail.value || '' })
  },

  onSubmit: function () {
    if (this.data.submitting) return

    if (!this.data.selectedDoctorId) {
      wx.showToast({ title: '请先选择医生', icon: 'none' })
      return
    }
    if (!this.data.selectedSlot) {
      wx.showToast({ title: '请选择就诊时段', icon: 'none' })
      return
    }

    const self = this
    this.setData({ submitting: true })

    api.createAppointment({
      doctorId: this.data.selectedDoctorId,
      appointDate: this.data.selectedDate,
      timeSlot: this.data.selectedSlot,
      complaint: this.data.complaint
    }, {
      loading: true,
      loadingText: '提交预约'
    }).then(function () {
      self.setData({ submitting: false, selectedSlot: '', complaint: '' })
      self.refreshSummary()
      wx.showToast({ title: '预约成功', icon: 'success', duration: 1500 })
      setTimeout(function () {
        wx.switchTab({ url: '/pages/appointments/appointments' })
      }, 1000)
    }).catch(function () {
      self.setData({ submitting: false })
    })
  }
})
