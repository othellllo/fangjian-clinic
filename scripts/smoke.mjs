#!/usr/bin/env node
/**
 * 方笺 端到端冒烟测试
 *
 * v1 闭环：登录 → 建档 → 挂号 → 接诊 → 病历 → 开方 → 收费 → 发药 → 完成就诊 → 报表
 * v2 新增：代煎流转 → 会员储值 → 微信小程序预约（患者端 token）→ 到店报到 → 操作审计
 *
 * 用法：node scripts/smoke.mjs   （需后端已在 http://localhost:8080 运行）
 */

const BASE = process.env.BASE_URL || 'http://localhost:8080/api'
const ACCOUNT = { username: process.env.SMOKE_USER || 'admin', password: process.env.SMOKE_PASS || '123456' }
// 每次运行用不同手机号，避免历史数据里同名同号患者导致会员余额取到旧档案
const PATIENT_PHONE = process.env.SMOKE_PHONE || ('139' + String(Date.now()).slice(-8))
const PATIENT_CODE = '123456'

let token = ''
const results = []

async function request(method, path, body, authToken) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  const bearer = authToken === undefined ? token : authToken
  if (bearer) headers.Authorization = `Bearer ${bearer}`
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  let payload
  try {
    payload = JSON.parse(text)
  } catch {
    throw new Error(`${method} ${path} 返回非 JSON：${text.slice(0, 200)}`)
  }
  if (payload.code !== 0) {
    throw new Error(`${method} ${path} 失败：${payload.code} ${payload.message}`)
  }
  return payload.data
}

const api = (method, path, body) => request(method, path, body)
const apiAs = (authToken, method, path, body) => request(method, path, body, authToken)

function step(name, detail) {
  results.push({ name, detail })
  console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`)
}

function assert(condition, message) {
  if (!condition) throw new Error('断言失败：' + message)
}

function dayString(offsetDays) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function main() {
  console.log(`\n== 方笺 冒烟测试 @ ${BASE} ==\n`)

  // 1. 登录
  const login = await api('POST', '/auth/login', ACCOUNT)
  token = login.token
  assert(token, '登录应返回 token')
  step('登录', `${login.user.realName} / ${login.user.role}`)

  const me = await api('GET', '/auth/me')
  assert(me.username === ACCOUNT.username, '当前用户应与登录用户一致')
  step('获取当前用户', me.realName)

  // 2. 患者建档
  const patient = await api('POST', '/patients', {
    name: '冒烟测试患者',
    gender: 'MALE',
    birthDate: '1990-05-01',
    phone: PATIENT_PHONE,
    address: '测试地址',
    allergy: '青霉素过敏',
    chronicHistory: '高血压 3 年'
  })
  assert(patient.id, '建档应返回患者 id')
  step('患者建档', `${patient.patientNo} ${patient.name}`)

  const patientPage = await api('GET', '/patients?keyword=冒烟&page=1&size=10')
  assert(patientPage.total >= 1, '患者搜索应能命中新建患者')
  step('患者检索', `命中 ${patientPage.total} 条`)

  // 3. 挂号
  const doctors = await api('GET', '/users/doctors')
  assert(doctors.length > 0, '应有可用医生')
  const doctor = doctors[0]
  step('医生列表', `${doctor.realName}（${doctor.title}）`)

  const visit = await api('POST', '/visits/register', {
    patientId: patient.id,
    doctorId: doctor.id,
    type: 'FIRST',
    complaint: '乏力纳差 2 周'
  })
  assert(visit.queueNo >= 1, '挂号应生成排队号')
  assert(visit.chargeOrderId, '挂号应生成挂号费收费单')
  step('挂号', `${visit.visitNo} 排队号 ${visit.queueNo} 挂号费 ${visit.registerFee}`)

  const queue = await api('GET', `/visits/queue?doctorId=${doctor.id}`)
  assert(queue.some((item) => item.id === visit.id), '候诊队列应包含新挂号')
  step('候诊队列', `${queue.length} 人`)

  // 4. 接诊
  const started = await api('POST', `/visits/${visit.id}/start`)
  assert(started.status === 'IN_SERVICE', '接诊后状态应为 IN_SERVICE')
  step('接诊', started.status)

  // 5. 中医病历
  const record = await api('PUT', `/visits/${visit.id}/record`, {
    recordType: 'TCM',
    tcmDisease: '虚劳',
    chiefComplaint: '乏力纳差 2 周',
    presentIllness: '患者 2 周前无明显诱因出现乏力，食欲减退，餐后腹胀。',
    pastHistory: '高血压病史 3 年，规律服药。',
    inspection: '面色萎黄，神疲乏力',
    auscultation: '语声低微，无异常气味',
    inquiry: '纳差，眠可，二便调',
    palpation: '腹软无压痛',
    tongue: '舌淡红，苔薄白',
    pulse: '脉细弱',
    syndrome: '脾胃气虚证',
    treatmentPrinciple: '健脾益气，和胃消食',
    advice: '忌生冷油腻，规律饮食'
  })
  assert(record.version >= 1, '病历应记录版本号')
  step('保存病历', `证型 ${record.syndrome} / 版本 ${record.version}`)

  // 6. 开方（代煎）
  const drugs = await api('GET', '/drugs?keyword=黄芪&page=1&size=10')
  assert(drugs.list.length > 0, '药品字典应有黄芪')
  const drug = drugs.list[0]
  const stockBefore = Number(drug.stockQty)
  step('选药', `${drug.name} 库存 ${stockBefore}${drug.unit}`)

  const prescription = await api('POST', '/prescriptions', {
    visitId: visit.id,
    type: 'HERB',
    decoctionMethod: '代煎',
    doses: 7,
    remark: '饭后温服',
    items: [{ drugId: drug.id, dose: 10, quantity: 70, usageText: '水煎服，日一剂' }]
  })
  assert(prescription.chargeOrderId, '开方应生成收费单')
  assert(Number(prescription.totalAmount) > 0, '处方金额应大于 0')
  step('开方', `${prescription.prescriptionNo} 金额 ${prescription.totalAmount}`)

  // 7. 收费（挂号费 + 处方）
  const unpaid = await api('GET', '/charges?status=UNPAID&page=1&size=50')
  const visitOrders = unpaid.list.filter((order) => order.visitId === visit.id)
  assert(visitOrders.length >= 2, `该就诊应有 2 张待收费单，实际 ${visitOrders.length}`)
  let paidTotal = 0
  for (const order of visitOrders) {
    const paid = await api('POST', `/charges/${order.id}/pay`, { method: 'CASH', remark: '冒烟测试' })
    assert(paid.status === 'PAID', '收费后状态应为 PAID')
    paidTotal += Number(paid.paidAmount)
  }
  step('收费', `${visitOrders.length} 张单，合计 ${paidTotal.toFixed(2)} 元`)

  // 8. 发药（扣库存）
  const dispensable = await api('GET', '/prescriptions/pending-dispense')
  assert(dispensable.some((item) => item.id === prescription.id), '已收费处方应出现在待发药列表')
  const dispensed = await api('POST', `/prescriptions/${prescription.id}/dispense`)
  assert(dispensed.status === 'DISPENSED', '发药后状态应为 DISPENSED')
  step('发药', dispensed.prescriptionNo)

  const stockAfter = await api('GET', `/drugs/${drug.id}/stock`)
  assert(Number(stockAfter.totalQty) === stockBefore - 70,
    `发药后库存应为 ${stockBefore - 70}，实际 ${stockAfter.totalQty}`)
  step('库存扣减', `${stockBefore} → ${stockAfter.totalQty}`)

  const ledger = await api('GET', `/stock/ledger?drugId=${drug.id}&page=1&size=10`)
  assert(ledger.list.some((row) => row.changeType === 'OUT'), '库存流水应有出库记录')
  step('库存流水', `最新 ${ledger.list[0].changeType} ${ledger.list[0].quantity}`)

  // 9. 代煎流转（发药后自动建单）
  const pendingDecoctions = await api('GET', '/decoctions?status=PENDING&page=1&size=50')
  const decoction = pendingDecoctions.list.find((item) => item.prescriptionId === prescription.id)
  assert(decoction, '代煎处方发药后应自动生成代煎单')
  step('代煎建单', `${decoction.decoctionNo} 剂数 ${decoction.doses}`)

  const cooking = await api('POST', `/decoctions/${decoction.id}/cook`)
  assert(cooking.status === 'COOKING', '开始煎制后状态应为 COOKING')
  const ready = await api('POST', `/decoctions/${decoction.id}/ready`)
  assert(ready.status === 'READY', '煎制完成后状态应为 READY')
  const delivered = await api('POST', `/decoctions/${decoction.id}/deliver`,
    { deliverType: 'SELF', remark: '患者自取' })
  assert(delivered.status === 'DELIVERED', '交付后状态应为 DELIVERED')
  step('代煎流转', `PENDING → COOKING → READY → DELIVERED`)

  const decoctionCounts = await api('GET', '/decoctions/counts')
  assert(typeof decoctionCounts.DELIVERED === 'number', '代煎状态统计应返回各状态数量')
  step('代煎统计', `已交付 ${decoctionCounts.DELIVERED} 单`)

  // 10. 完成就诊
  const finished = await api('POST', `/visits/${visit.id}/finish`)
  assert(finished.status === 'FINISHED', '完成就诊后状态应为 FINISHED')
  step('完成就诊', finished.status)

  // 11. 会员储值
  const member = await api('POST', '/members', { patientId: patient.id, level: 2 })
  assert(member.memberNo, '开卡应返回会员号')
  const recharged = await api('POST', `/members/${member.id}/recharge`,
    { amount: 500, method: 'WECHAT', remark: '冒烟测试充值' })
  assert(Number(recharged.balance) === 500, '充值后余额应为 500')
  step('会员储值', `${member.memberNo} 余额 ${recharged.balance}`)

  // 12. 患者端（微信小程序）登录与预约
  const patientLogin = await apiAs('', 'POST', '/patient/auth/login',
    { phone: PATIENT_PHONE, code: PATIENT_CODE })
  const patientToken = patientLogin.token
  assert(patientToken, '患者端登录应返回 token')
  step('患者端登录', `${patientLogin.patient.name} / ${patientLogin.patient.patientNo}`)

  const patientDoctors = await apiAs(patientToken, 'GET', '/patient/doctors')
  assert(patientDoctors.length > 0, '患者端应能拿到医生列表')

  const patientProfile = await apiAs(patientToken, 'GET', '/patient/profile')
  assert(patientProfile.patientNo, '患者端资料应返回病历号')
  assert(Number(patientProfile.memberBalance) === 500,
    `患者端资料应带会员余额 500，实际 ${patientProfile.memberBalance}`)
  step('患者端资料', `${patientProfile.name} / 会员余额 ${patientProfile.memberBalance}`)

  const tomorrow = dayString(1)
  const slots = await apiAs(patientToken, 'GET', `/patient/slots?doctorId=${doctor.id}&date=${tomorrow}`)
  const freeSlot = slots.find((slot) => slot.booked < slot.capacity)
  assert(freeSlot, '次日应有可约时段')
  step('患者端选号', `${tomorrow} ${freeSlot.timeSlot}（${freeSlot.booked}/${freeSlot.capacity}）`)

  const appointment = await apiAs(patientToken, 'POST', '/patient/appointments', {
    doctorId: doctor.id,
    appointDate: tomorrow,
    timeSlot: freeSlot.timeSlot,
    complaint: '复诊调方'
  })
  assert(appointment.status === 'BOOKED', '预约后状态应为 BOOKED')
  step('小程序预约', `${appointment.appointNo} ${appointment.appointDate} ${appointment.timeSlot}`)

  const myAppointments = await apiAs(patientToken, 'GET', '/patient/appointments')
  assert(myAppointments.some((item) => item.id === appointment.id), '我的预约应包含新预约')
  step('我的预约', `${myAppointments.length} 条`)

  // 13. 到店报到（员工侧）→ 生成就诊记录
  const staffAppointments = await api('GET', `/appointments?date=${tomorrow}&page=1&size=20`)
  assert(staffAppointments.list.some((item) => item.id === appointment.id), '员工侧应能看到该预约')
  const arrived = await api('POST', `/appointments/${appointment.id}/arrive`)
  assert(arrived.status === 'ARRIVED' && arrived.visitId, '报到后应生成就诊记录')
  step('到店报到', `预约 ${arrived.appointNo} → 就诊 ${arrived.visitId}`)

  // 14. 患者端查看就诊记录与详情
  const patientVisits = await apiAs(patientToken, 'GET', '/patient/visits?page=1&size=10')
  assert(patientVisits.total >= 2, `患者应有至少 2 次就诊记录，实际 ${patientVisits.total}`)
  const finishedDetail = await apiAs(patientToken, 'GET', `/patient/visits/${visit.id}`)
  assert(finishedDetail.record && finishedDetail.record.syndrome === '脾胃气虚证', '患者端应能看到病历')
  step('患者端就诊记录', `${patientVisits.total} 次，病历与处方可见`)

  // 15. 越权校验：A 患者不能看 B 的就诊
  const otherPatient = await api('POST', '/patients', { name: '越权测试患者', gender: 'FEMALE', phone: '13900002222' })
  const otherVisit = await api('POST', '/visits/register',
    { patientId: otherPatient.id, doctorId: doctor.id, type: 'FIRST', complaint: '测试' })
  let forbidden = false
  try {
    await apiAs(patientToken, 'GET', `/patient/visits/${otherVisit.id}`)
  } catch (error) {
    forbidden = /无权|403/.test(error.message)
  }
  assert(forbidden, '患者端访问他人病历应被拒绝')
  step('越权校验', '他人病历访问被拒绝')

  // 16. 报表
  const daily = await api('GET', '/reports/daily')
  assert(Number(daily.revenue) > 0, '日报营收应大于 0')
  assert(daily.registerCount >= 1, '日报挂号数应大于 0')
  step('经营日报', `营收 ${daily.revenue} / 挂号 ${daily.registerCount} / 处方 ${daily.prescriptionCount}`)

  const range = await api('GET', '/reports/range')
  assert(Array.isArray(range) && range.length >= 1, '区间报表应返回每日数据')
  step('区间报表', `${range.length} 天`)

  const performance = await api('GET', '/reports/doctor-performance')
  assert(performance.length >= 1, '医生绩效应有数据')
  step('医生绩效', `${performance[0].doctorName} 收入 ${performance[0].revenue}`)

  // 17. 操作审计
  const auditPage = await api('GET', '/audit-logs?page=1&size=20')
  assert(auditPage.total > 0, '审计日志应有记录')
  const auditModules = await api('GET', '/audit-logs/modules')
  assert(auditModules.length > 0, '审计模块列表不应为空')
  assert(auditPage.list.some((row) => row.result === 'SUCCESS' && row.username), '审计记录应包含操作人')
  step('操作审计', `${auditPage.total} 条，模块 ${auditModules.length} 个`)

  // 18. 医保扩展点（默认未开通）
  const providers = await api('GET', '/insurance/providers')
  assert(providers.length >= 1, '应至少注册一个医保实现')
  const settlement = await api('POST', '/insurance/settle',
    { chargeOrderId: visitOrders[0].id, providerCode: 'noop' })
  assert(settlement.status === 'UNSUPPORTED', '未接入地区实现时应返回 UNSUPPORTED')
  step('医保扩展点', `${providers.length} 个实现 / 结算状态 ${settlement.status}`)

  // 19. 鉴权校验
  const denied = await fetch(`${BASE}/users`, { headers: { Authorization: 'Bearer invalid' } })
  assert(denied.status === 401, '无效 token 应返回 401')
  const patientDenied = await fetch(`${BASE}/patients`, { headers: { Authorization: `Bearer ${patientToken}` } })
  assert(patientDenied.status === 403, '患者 token 不能访问员工接口')
  step('鉴权校验', '无效 token 401 / 患者 token 访问员工接口 403')

  console.log(`\n全部通过：${results.length} 项检查 ✅\n`)
}

main().catch((error) => {
  console.error(`\n❌ ${error.message}\n`)
  process.exit(1)
})
