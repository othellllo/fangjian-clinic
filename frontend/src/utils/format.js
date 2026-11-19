export function formatMoney(value) {
  const num = Number(value ?? 0)
  if (Number.isNaN(num)) return '0.00'
  return num.toFixed(2)
}

export function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

export function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

export function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export const VISIT_STATUS_TEXT = {
  WAITING: '候诊',
  IN_SERVICE: '就诊中',
  FINISHED: '已完成',
  CANCELLED: '已作废'
}

export const VISIT_STATUS_TAG = {
  WAITING: 'warning',
  IN_SERVICE: 'primary',
  FINISHED: 'success',
  CANCELLED: 'info'
}

export const CHARGE_STATUS_TEXT = {
  UNPAID: '待收费',
  PAID: '已收费',
  REFUNDED: '已退费'
}

export const CHARGE_STATUS_TAG = {
  UNPAID: 'danger',
  PAID: 'success',
  REFUNDED: 'info'
}

export const PAY_METHOD_TEXT = {
  CASH: '现金',
  WECHAT: '微信',
  ALIPAY: '支付宝',
  BANK_CARD: '银行卡',
  MEMBER_BALANCE: '会员余额',
  INSURANCE: '医保'
}

export const DRUG_CATEGORY_TEXT = {
  HERB: '中药饮片',
  PATENT: '中成药',
  WESTERN: '西药',
  MATERIAL: '耗材'
}

export const PRESCRIPTION_TYPE_TEXT = {
  HERB: '中药处方',
  PATENT: '中成药处方',
  WESTERN: '西药处方'
}

export const PRESCRIPTION_STATUS_TEXT = {
  DRAFT: '草稿',
  PENDING: '待收费',
  DISPENSED: '已发药',
  CANCELLED: '已作废'
}

export const ROLE_TEXT = {
  ADMIN: '管理员',
  DOCTOR: '医生',
  PHARMACIST: '药师',
  CASHIER: '收费员'
}

export const GENDER_TEXT = {
  MALE: '男',
  FEMALE: '女'
}
