/**
 * 患者端接口层（唯一数据出口）
 *
 * 全部依 docs/api-contract.md §15 编写；后端返回字段做了一层容错归一化，
 * 便于后端联调期字段命名略有出入时页面不被卡住。
 */
const request = require('./request.js')
const format = require('./format.js')

/* ============================ 工具函数 ============================ */

function toArray(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  return [value]
}

/** 兼容数组 / 分页对象两种返回 */
function toList(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  if (Array.isArray(data.list)) return data.list
  if (Array.isArray(data.records)) return data.records
  if (Array.isArray(data.rows)) return data.rows
  if (Array.isArray(data.items)) return data.items
  if (Array.isArray(data.doctors)) return data.doctors
  if (Array.isArray(data.slots)) return data.slots
  if (Array.isArray(data.appointments)) return data.appointments
  if (Array.isArray(data.visits)) return data.visits
  return []
}

function toPage(data) {
  if (!data) return { list: [], total: 0 }
  if (Array.isArray(data)) return { list: data, total: data.length }
  const list = toList(data)
  let total = Number(data.total)
  if (isNaN(total)) total = list.length
  return { list: list, total: total }
}

/* ============================ 数据归一化 ============================ */

function normalizePatient(raw) {
  if (!raw) return null
  const name = raw.name || raw.realName || ''
  let balanceRaw = null
  if (raw.memberBalance !== undefined && raw.memberBalance !== null) {
    balanceRaw = raw.memberBalance
  } else if (raw.balance !== undefined && raw.balance !== null) {
    balanceRaw = raw.balance
  } else if (raw.member && raw.member.balance !== undefined && raw.member.balance !== null) {
    balanceRaw = raw.member.balance
  }
  const hasBalance = balanceRaw !== null && balanceRaw !== ''
  const hasAge = raw.age === 0 || !!raw.age
  // 后端 PatientDto 暂无余额字段：有 memberId 视为已开通会员卡，提示到前台查询
  let balanceText = '未开通'
  let balanceTip = '可到前台开通会员储值'
  if (hasBalance) {
    balanceText = '¥' + format.formatMoney(balanceRaw)
    balanceTip = '余额可用于医馆内消费'
  } else if (raw.memberId) {
    balanceText = '—'
    balanceTip = '会员余额请到前台查询'
  }
  return {
    id: raw.id,
    name: name,
    phone: raw.phone || '',
    patientNo: raw.patientNo || '',
    gender: raw.gender || '',
    genderText: format.GENDER_TEXT[raw.gender] || '',
    age: hasAge ? raw.age : '',
    ageText: hasAge ? String(raw.age) : '',
    birthDate: raw.birthDate ? format.formatDate(raw.birthDate) : '',
    memberId: raw.memberId || '',
    allergy: raw.allergy || '',
    chronicHistory: raw.chronicHistory || '',
    hasBalance: hasBalance,
    memberBalanceText: hasBalance ? format.formatMoney(balanceRaw) : '',
    balanceText: balanceText,
    balanceTip: balanceTip,
    initial: name ? name.slice(0, 1) : '患'
  }
}

function normalizeDoctor(raw) {
  const data = raw || {}
  const name = data.realName || data.name || data.doctorName || ''
  return {
    id: data.id !== undefined && data.id !== null ? data.id : data.doctorId,
    name: name,
    title: data.title || '',
    dept: data.dept || data.department || '',
    intro: data.intro || data.specialty || '',
    initial: name ? name.slice(0, 1) : '医'
  }
}

function normalizeSlot(raw, index) {
  const data = raw || {}
  const timeSlot = data.timeSlot || data.slot || data.time || data.name || ''
  const capacity = Number(data.capacity)
  const booked = Number(data.booked)
  const remainingRaw = data.remaining === undefined ? NaN : Number(data.remaining)

  let full = false
  if (data.full === true || data.available === false) full = true
  if (String(data.status || '').toUpperCase() === 'FULL') full = true

  let remaining = NaN
  if (!isNaN(capacity) && capacity > 0) {
    const used = isNaN(booked) ? 0 : booked
    remaining = Math.max(capacity - used, 0)
    if (remaining <= 0) full = true
  } else if (!isNaN(remainingRaw)) {
    remaining = Math.max(remainingRaw, 0)
    if (remaining <= 0) full = true
  }

  let metaText = '可预约'
  if (full) {
    metaText = '已满'
  } else if (remaining === remaining && capacity > 0) {
    metaText = '余 ' + remaining + ' 个'
  }

  return {
    timeSlot: timeSlot,
    key: timeSlot + '_' + index,
    capacity: isNaN(capacity) ? 0 : capacity,
    booked: isNaN(booked) ? 0 : booked,
    remaining: remaining === remaining ? remaining : null,
    full: full,
    metaText: metaText
  }
}

function normalizeAppointment(raw) {
  const data = raw || {}
  const status = data.status || ''
  const appointDate = data.appointDate ? format.formatDate(data.appointDate) : '-'
  const timeSlot = data.timeSlot || ''
  return {
    id: data.id,
    appointNo: data.appointNo || '',
    doctorId: data.doctorId,
    doctorName: data.doctorName || '',
    doctorTitle: data.doctorTitle || data.title || '',
    appointDate: appointDate,
    timeSlot: timeSlot,
    dateTimeText: (appointDate + ' ' + timeSlot).replace(/\s+$/, ''),
    complaint: data.complaint || '',
    status: status,
    statusText: format.APPOINTMENT_STATUS_TEXT[status] || status || '未知状态',
    statusClass: format.APPOINTMENT_STATUS_CLASS[status] || 'tag-gray',
    visitId: data.visitId || null,
    cancelable: status === 'BOOKED',
    createdAtText: data.createdAt ? format.formatDateTime(data.createdAt) : ''
  }
}

/** 我的预约分组：即将就诊（含今日到店）/ 历史 */
function splitAppointments(list) {
  const today = format.todayStr()
  const upcoming = []
  const history = []
  const items = list || []
  items.forEach(function (item) {
    const isUpcoming = item.status !== 'CANCELLED' && item.appointDate && item.appointDate >= today
    if (isUpcoming) upcoming.push(item)
    else history.push(item)
  })

  function keyOf(item) {
    return item.appointDate + ' ' + item.timeSlot
  }

  upcoming.sort(function (a, b) {
    const ka = keyOf(a)
    const kb = keyOf(b)
    return ka < kb ? -1 : (ka > kb ? 1 : 0)
  })
  history.sort(function (a, b) {
    const ka = keyOf(a)
    const kb = keyOf(b)
    return ka > kb ? -1 : (ka < kb ? 1 : 0)
  })

  return { upcoming: upcoming, history: history }
}

function normalizeVisit(raw) {
  const data = raw || {}
  const status = data.status || ''
  const registerTime = data.registerTime || data.createdAt || ''
  return {
    id: data.id,
    visitNo: data.visitNo || '',
    doctorId: data.doctorId,
    doctorName: data.doctorName || '',
    registerTime: registerTime,
    dateText: registerTime ? format.formatDate(registerTime) : '-',
    timeText: registerTime ? format.formatDateTime(registerTime) : '',
    status: status,
    statusText: format.VISIT_STATUS_TEXT[status] || status || '未知状态',
    statusClass: format.VISIT_STATUS_CLASS[status] || 'tag-gray',
    type: data.type || '',
    typeText: format.VISIT_TYPE_TEXT[data.type] || '',
    complaint: data.complaint || '',
    registerFeeText: format.formatMoney(data.registerFee)
  }
}

const RECORD_FIELDS = [
  { key: 'chiefComplaint', label: '主诉' },
  { key: 'presentIllness', label: '现病史' },
  { key: 'pastHistory', label: '既往史' },
  { key: 'inspection', label: '望诊' },
  { key: 'auscultation', label: '闻诊' },
  { key: 'inquiry', label: '问诊' },
  { key: 'palpation', label: '切诊' },
  { key: 'tongue', label: '舌象' },
  { key: 'pulse', label: '脉象' },
  { key: 'syndrome', label: '辨证' },
  { key: 'treatmentPrinciple', label: '治则' },
  { key: 'advice', label: '医嘱' }
]

function buildRecordSections(record) {
  if (!record) return []
  const sections = []
  RECORD_FIELDS.forEach(function (field) {
    const value = record[field.key]
    if (value === null || value === undefined) return
    if (String(value).trim() === '') return
    sections.push({
      key: field.key,
      label: field.label,
      value: String(value)
    })
  })
  return sections
}

function normalizePrescriptionItem(raw) {
  const data = raw || {}
  return {
    id: data.id,
    drugName: data.drugName || data.name || '',
    spec: data.spec || '',
    unit: data.unit || '',
    dose: format.formatQty(data.dose),
    quantity: format.formatQty(data.quantity),
    priceText: format.formatMoney(data.price),
    amountText: format.formatMoney(data.amount),
    usageText: data.usageText || ''
  }
}

function normalizePrescription(raw) {
  const data = raw || {}
  const status = data.status || ''
  const type = data.type || ''
  return {
    id: data.id,
    prescriptionNo: data.prescriptionNo || '',
    typeText: format.PRESCRIPTION_TYPE_TEXT[type] || '处方',
    decoctionMethod: data.decoctionMethod || '',
    doses: data.doses === 0 || data.doses ? data.doses : '',
    totalAmountText: format.formatMoney(data.totalAmount),
    statusText: format.PRESCRIPTION_STATUS_TEXT[status] || status || '',
    statusClass: format.PRESCRIPTION_STATUS_CLASS[status] || 'tag-gray',
    createdAtText: data.createdAt ? format.formatDateTime(data.createdAt) : '',
    items: toArray(data.items).map(normalizePrescriptionItem)
  }
}

function normalizeChargeItem(raw) {
  const data = raw || {}
  return {
    id: data.id,
    itemName: data.itemName || '',
    quantity: format.formatQty(data.quantity),
    unitPriceText: format.formatMoney(data.unitPrice),
    amountText: format.formatMoney(data.amount)
  }
}

function normalizeCharge(raw) {
  const data = raw || {}
  const status = data.status || ''
  const type = data.type || ''
  const payableAmount = Number(data.payableAmount)
  const discountAmount = Number(data.discountAmount)
  return {
    id: data.id,
    orderNo: data.orderNo || '',
    type: type,
    typeText: format.CHARGE_TYPE_TEXT[type] || '费用',
    status: status,
    statusText: format.CHARGE_STATUS_TEXT[status] || status || '',
    statusClass: format.CHARGE_STATUS_CLASS[status] || 'tag-gray',
    totalAmountText: format.formatMoney(data.totalAmount),
    discountAmountText: format.formatMoney(data.discountAmount),
    hasDiscount: !isNaN(discountAmount) && discountAmount > 0,
    payableAmount: isNaN(payableAmount) ? 0 : payableAmount,
    payableAmountText: format.formatMoney(payableAmount),
    paidAmountText: format.formatMoney(data.paidAmount),
    payMethodText: data.payMethod ? (format.PAY_METHOD_TEXT[data.payMethod] || data.payMethod) : '',
    createdAtText: data.createdAt ? format.formatDateTime(data.createdAt) : '',
    paidAtText: data.paidAt ? format.formatDateTime(data.paidAt) : '',
    items: toArray(data.items).map(normalizeChargeItem)
  }
}

function fallbackPatient(visitRaw) {
  if (!visitRaw) return null
  const raw = {
    name: visitRaw.patientName || '',
    gender: visitRaw.patientGender || '',
    age: visitRaw.patientAge,
    phone: visitRaw.patientPhone || '',
    patientNo: visitRaw.patientNo || ''
  }
  if (!raw.name && !raw.gender && !raw.phone && !raw.patientNo && raw.age === undefined) return null
  return normalizePatient(raw)
}

/**
 * 就诊详情：兼容 { visit, patient, record, prescriptions, charges } 聚合结构，
 * 也兼容直接返回 VisitDto 的简版结构。
 */
function normalizeVisitDetail(payload) {
  const data = payload || {}
  const visitRaw = data.visit || data
  const visit = normalizeVisit(visitRaw)
  visit.queueNo = visitRaw.queueNo || ''
  visit.endTimeText = visitRaw.endTime ? format.formatDateTime(visitRaw.endTime) : ''
  const registerFee = Number(visitRaw.registerFee)
  visit.hasRegisterFee = !isNaN(registerFee) && registerFee > 0
  visit.registerFeeText = format.formatMoney(visitRaw.registerFee)

  const patientRaw = data.patient || null
  const patient = patientRaw ? normalizePatient(patientRaw) : fallbackPatient(visitRaw)

  const recordRaw = data.record || data.medicalRecord || null
  const recordSections = buildRecordSections(recordRaw)
  // 医生尚未书写病历时，用挂号主诉兜底展示，避免详情页整块留白
  if (!recordSections.length && visitRaw.complaint) {
    recordSections.push({ key: 'complaint', label: '主诉', value: String(visitRaw.complaint) })
  }

  const prescriptions = toArray(data.prescriptions || data.prescriptionList).map(normalizePrescription)
  const charges = toArray(data.charges || data.chargeOrders || data.chargeOrder).map(normalizeCharge)

  let chargeTotal = 0
  charges.forEach(function (charge) {
    chargeTotal += charge.payableAmount || 0
  })

  return {
    visit: visit,
    patient: patient,
    recordSections: recordSections,
    recordUpdatedText: recordRaw && recordRaw.updatedAt ? format.formatDateTime(recordRaw.updatedAt) : '',
    hasRecord: recordSections.length > 0,
    prescriptions: prescriptions,
    hasPrescriptions: prescriptions.length > 0,
    charges: charges,
    hasCharges: charges.length > 0,
    chargeTotalText: format.formatMoney(chargeTotal)
  }
}

/* ============================ 接口定义 ============================ */

const api = {
  /** 手机号 + 验证码登录 → { token, patient } */
  login: function (phone, code) {
    return request.post(
      '/patient/auth/login',
      { phone: phone, code: code },
      { loading: true, loadingText: '登录中' }
    ).then(function (data) {
      const result = data || {}
      return {
        token: result.token || result.accessToken || '',
        patient: normalizePatient(result.patient || null),
        raw: result
      }
    })
  },

  /** 我的资料 */
  profile: function (options) {
    return request.get('/patient/profile', {}, options).then(function (data) {
      // 兼容 { patient, member } / 直接返回 PatientDto 两种结构
      if (data && data.patient) return normalizePatient(data.patient)
      return normalizePatient(data)
    })
  },

  /** 可预约医生列表 */
  doctors: function (options) {
    return request.get('/patient/doctors', {}, options).then(function (data) {
      return toList(data).map(normalizeDoctor)
    })
  },

  /** 某医生某日可约时段 */
  slots: function (doctorId, date, options) {
    return request.get('/patient/slots', { doctorId: doctorId, date: date }, options).then(function (data) {
      return toList(data).map(normalizeSlot)
    })
  },

  /** 我的预约列表 */
  appointments: function (options) {
    return request.get('/patient/appointments', {}, options).then(function (data) {
      return toList(data).map(normalizeAppointment)
    })
  },

  /** 提交预约 { doctorId, appointDate, timeSlot, complaint } */
  createAppointment: function (payload, options) {
    return request.post('/patient/appointments', payload, options)
  },

  /** 取消预约 */
  cancelAppointment: function (id, options) {
    return request.post('/patient/appointments/' + id + '/cancel', {}, options)
  },

  /** 我的就诊记录（分页）→ { list, total } */
  visits: function (page, size, options) {
    return request.get('/patient/visits', { page: page, size: size }, options).then(function (data) {
      const pageData = toPage(data)
      return {
        list: pageData.list.map(normalizeVisit),
        total: pageData.total
      }
    })
  },

  /** 就诊详情（病历 + 处方 + 费用） */
  visitDetail: function (id, options) {
    return request.get('/patient/visits/' + id, {}, options).then(normalizeVisitDetail)
  }
}

module.exports = {
  login: api.login,
  profile: api.profile,
  doctors: api.doctors,
  slots: api.slots,
  appointments: api.appointments,
  createAppointment: api.createAppointment,
  cancelAppointment: api.cancelAppointment,
  visits: api.visits,
  visitDetail: api.visitDetail,
  // 工具方法，页面也可直接使用
  splitAppointments: splitAppointments,
  normalizePatient: normalizePatient,
  normalizeAppointment: normalizeAppointment,
  normalizeVisit: normalizeVisit
}
