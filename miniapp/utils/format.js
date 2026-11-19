/**
 * 格式化与状态文案映射（页面与 api 层共用）
 * 金额按契约使用字符串十进制，这里统一转成两位小数字符串展示。
 */

const WEEK_TEXT = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const APPOINTMENT_STATUS_TEXT = {
  BOOKED: '已预约',
  ARRIVED: '已到店',
  CANCELLED: '已取消'
}

// 已预约-蓝、已到店-绿、已取消-灰
const APPOINTMENT_STATUS_CLASS = {
  BOOKED: 'tag-blue',
  ARRIVED: 'tag-green',
  CANCELLED: 'tag-gray'
}

const VISIT_STATUS_TEXT = {
  WAITING: '候诊中',
  IN_SERVICE: '就诊中',
  FINISHED: '已完成',
  CANCELLED: '已作废'
}

const VISIT_STATUS_CLASS = {
  WAITING: 'tag-orange',
  IN_SERVICE: 'tag-blue',
  FINISHED: 'tag-green',
  CANCELLED: 'tag-gray'
}

const VISIT_TYPE_TEXT = {
  FIRST: '初诊',
  FOLLOWUP: '复诊'
}

const PRESCRIPTION_TYPE_TEXT = {
  HERB: '中药饮片',
  PATENT: '中成药',
  WESTERN: '西药'
}

const PRESCRIPTION_STATUS_TEXT = {
  DRAFT: '草稿',
  PENDING: '待缴费',
  DISPENSED: '已发药',
  CANCELLED: '已作废'
}

const PRESCRIPTION_STATUS_CLASS = {
  DRAFT: 'tag-gray',
  PENDING: 'tag-orange',
  DISPENSED: 'tag-green',
  CANCELLED: 'tag-gray'
}

const CHARGE_TYPE_TEXT = {
  REGISTER: '挂号费',
  PRESCRIPTION: '药费',
  OTHER: '其他费用'
}

const CHARGE_STATUS_TEXT = {
  UNPAID: '待支付',
  PAID: '已支付',
  REFUNDED: '已退费'
}

const CHARGE_STATUS_CLASS = {
  UNPAID: 'tag-orange',
  PAID: 'tag-green',
  REFUNDED: 'tag-gray'
}

const PAY_METHOD_TEXT = {
  CASH: '现金',
  WECHAT: '微信',
  ALIPAY: '支付宝',
  BANK_CARD: '银行卡',
  MEMBER_BALANCE: '会员余额',
  INSURANCE: '医保'
}

const GENDER_TEXT = {
  MALE: '男',
  FEMALE: '女'
}

function pad2(num) {
  return num < 10 ? '0' + num : String(num)
}

/** 金额：'128.5' / 128.5 / null → '128.50' / '0.00' */
function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = Number(value)
  if (isNaN(num)) return '0.00'
  return num.toFixed(2)
}

/** 数量 / 剂量：3.0 → '3'，3.5 → '3.5' */
function formatQty(value) {
  if (value === null || value === undefined || value === '') return ''
  const num = Number(value)
  if (isNaN(num)) return String(value)
  if (num === Math.floor(num)) return String(Math.floor(num))
  return String(num)
}

/** 日期：'2026-11-18 09:30:00' → '2026-11-18' */
function formatDate(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length >= 10 ? text.slice(0, 10) : text
}

/** 时间：'2026-11-18T09:30:00' → '2026-11-18 09:30' */
function formatDateTime(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

function parseDate(dateText) {
  const parts = String(dateText).split('-')
  return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
}

function toDateText(date) {
  return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate())
}

function todayStr() {
  return toDateText(new Date())
}

function addDays(dateText, days) {
  const date = parseDate(dateText)
  date.setDate(date.getDate() + days)
  return toDateText(date)
}

/** 生成「今天起 N 天」的日期选择数据 */
function buildBookingDates(days) {
  const today = todayStr()
  const list = []
  for (let i = 0; i < days; i++) {
    const value = addDays(today, i)
    const date = parseDate(value)
    let week = WEEK_TEXT[date.getDay()]
    if (i === 0) week = '今天'
    else if (i === 1) week = '明天'
    list.push({
      value: value,
      week: week,
      day: pad2(date.getMonth() + 1) + '/' + pad2(date.getDate()),
      isToday: i === 0
    })
  }
  return list
}

/** 安全取映射文案 */
function statusText(map, key) {
  if (!key) return ''
  return map[key] || String(key)
}

module.exports = {
  WEEK_TEXT: WEEK_TEXT,
  APPOINTMENT_STATUS_TEXT: APPOINTMENT_STATUS_TEXT,
  APPOINTMENT_STATUS_CLASS: APPOINTMENT_STATUS_CLASS,
  VISIT_STATUS_TEXT: VISIT_STATUS_TEXT,
  VISIT_STATUS_CLASS: VISIT_STATUS_CLASS,
  VISIT_TYPE_TEXT: VISIT_TYPE_TEXT,
  PRESCRIPTION_TYPE_TEXT: PRESCRIPTION_TYPE_TEXT,
  PRESCRIPTION_STATUS_TEXT: PRESCRIPTION_STATUS_TEXT,
  PRESCRIPTION_STATUS_CLASS: PRESCRIPTION_STATUS_CLASS,
  CHARGE_TYPE_TEXT: CHARGE_TYPE_TEXT,
  CHARGE_STATUS_TEXT: CHARGE_STATUS_TEXT,
  CHARGE_STATUS_CLASS: CHARGE_STATUS_CLASS,
  PAY_METHOD_TEXT: PAY_METHOD_TEXT,
  GENDER_TEXT: GENDER_TEXT,
  pad2: pad2,
  formatMoney: formatMoney,
  formatQty: formatQty,
  formatDate: formatDate,
  formatDateTime: formatDateTime,
  todayStr: todayStr,
  addDays: addDays,
  buildBookingDates: buildBookingDates,
  statusText: statusText
}
