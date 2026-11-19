#!/usr/bin/env node
/**
 * v3 模块冒烟测试：打印 / 追溯码 / 设置 / 导出 / 政策库 / 医保统计
 * 前置：后端已在 http://localhost:8080 运行，且已跑过 smoke.mjs（需要其产生的数据）
 * 用法：node scripts/smoke-v3.mjs
 */

const BASE = process.env.BASE_URL || 'http://localhost:8080/api'
let token = ''
const results = []

async function api(method, path, body, asText = false) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  if (asText || path.includes('/print/')) {
    if (response.status !== 200) throw new Error(`${method} ${path} HTTP ${response.status}`)
    return text
  }
  let payload
  try {
    payload = JSON.parse(text)
  } catch {
    throw new Error(`${method} ${path} 返回非 JSON：${text.slice(0, 120)}`)
  }
  if (payload.code !== 0) throw new Error(`${method} ${path} 失败：${payload.code} ${payload.message}`)
  return payload.data
}

async function expectFailure(method, path, body, keyword) {
  try {
    await api(method, path, body)
  } catch (error) {
    if (!keyword || error.message.includes(keyword)) return error.message
    throw new Error(`错误信息未包含「${keyword}」：${error.message}`)
  }
  throw new Error(`${method} ${path} 本应失败，但成功了`)
}

function step(name, detail) {
  results.push(name)
  console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`)
}
function assert(cond, msg) {
  if (!cond) throw new Error('断言失败：' + msg)
}

async function main() {
  console.log(`\n== 医馆 HIS v3 冒烟测试 @ ${BASE} ==\n`)

  const login = await api('POST', '/auth/login', { username: 'admin', password: '123456' })
  token = login.token
  step('登录', login.user.realName)

  /* ---------- 1. 打印 ---------- */
  const charges = await api('GET', '/charges?page=1&size=10')
  assert(charges.list.length > 0, '需要有收费单才能打印（请先跑 smoke.mjs）')
  const order = charges.list[0]
  const receipt = await api('GET', `/print/receipt/${order.id}`)
  assert(receipt.includes('<!DOCTYPE html>') && receipt.includes('收费小票'), '小票 HTML 结构不正确')
  assert(receipt.includes('window.print'), '小票应自动调起打印')
  step('打印小票', `${order.orderNo}（${receipt.length} 字节）`)

  // 找一张有处方的就诊（收费单本身可能只是挂号费）
  const visits = await api('GET', '/visits?page=1&size=20')
  let rxList = []
  for (const visit of visits.list) {
    const list = await api('GET', `/prescriptions?visitId=${visit.id}`)
    if (list.length > 0) {
      rxList = list
      break
    }
  }
  assert(rxList.length > 0, '需要有处方才能打印处方笺（请先跑 smoke.mjs）')
  const rxHtml = await api('GET', `/print/prescription/${rxList[0].id}`)
  assert(rxHtml.includes('处 方 笺'), '处方笺 HTML 结构不正确')
  step('打印处方笺', `${rxList[0].prescriptionNo}`)

  /* ---------- 2. 追溯码 ---------- */
  const patentDrugs = await api('GET', '/drugs?category=PATENT&page=1&size=10')
  assert(patentDrugs.list.length > 0, '药品字典应有中成药')
  // 选一个有库存批次的中成药（导入的数据可能尚无库存）
  const patent = patentDrugs.list.find((d) => Number(d.stockQty) > 0) || patentDrugs.list[0]
  assert(patent.needTrace === 1, '中成药应标记需要追溯码')
  const stock = await api('GET', `/drugs/${patent.id}/stock`)
  const batchNo = stock.batches[0].batchNo

  const makeCode = (n) => '99' + String(Date.now()).slice(-10) + String(n).padStart(8, '0')
  const codes = [makeCode(1), makeCode(2), makeCode(3)]
  const bound = await api('POST', '/traces/bind', { drugId: patent.id, batchNo, codes })
  assert(bound.success === 3, `应绑定 3 个追溯码，实际 ${bound.success}`)
  step('入库扫码绑定', `${patent.name} × 3`)

  const dup = await api('POST', '/traces/bind', { drugId: patent.id, batchNo, codes: [codes[0]] })
  assert(dup.failed === 1, '重复扫码应被拦截')
  step('重复码拦截', dup.errors[0] || '已拦截')

  const tracePage = await api('GET', `/traces?drugId=${patent.id}&page=1&size=10`)
  assert(tracePage.list.length >= 3, '追溯码台账应有记录')
  const one = await api('GET', `/traces/code/${codes[0]}`)
  assert(one.status === 'IN_STOCK', '新绑定追溯码状态应为 IN_STOCK')
  step('台账与按码反查', `${one.code} → ${one.status}`)

  // 开一张中成药处方，验证"未扫码不能发药"
  const doctor = (await api('GET', '/users/doctors'))[0]
  const patient = (await api('POST', '/patients', { name: '追溯码测试患者', gender: 'MALE', phone: '13900003333' }))
  const visit = await api('POST', '/visits/register',
    { patientId: patient.id, doctorId: doctor.id, type: 'FIRST', complaint: '感冒' })
  await api('POST', `/visits/${visit.id}/start`)
  const rx = await api('POST', '/prescriptions', {
    visitId: visit.id,
    type: 'PATENT',
    doses: 1,
    items: [{ drugId: patent.id, quantity: 1, usageText: '口服' }]
  })
  for (const item of (await api('GET', `/charges?status=UNPAID&page=1&size=50`)).list
    .filter((o) => o.visitId === visit.id)) {
    await api('POST', `/charges/${item.id}/pay`, { method: 'CASH' })
  }
  const blocked = await expectFailure('POST', `/prescriptions/${rx.id}/dispense`, undefined, '追溯码')
  step('未扫码禁止发药', blocked)

  const dispensed = await api('POST', `/prescriptions/${rx.id}/dispense`,
    { traces: [{ drugId: patent.id, codes: [codes[0]] }] })
  assert(dispensed.status === 'DISPENSED', '扫码后应发药成功')
  const used = await api('GET', `/prescriptions/${rx.id}/traces`)
  assert(used.length === 1 && used[0].status === 'DISPENSED', '追溯码应核销为已发药')
  step('扫码发药', `${rx.prescriptionNo} 核销 1 码`)

  const returned = await api('POST', '/traces/return', { ids: [used[0].id] })
  assert(returned.changed === 1, '退药回退应成功')
  step('退药回退', '状态回 IN_STOCK')

  const upload = await api('POST', '/traces/upload', { ids: [used[0].id] })
  assert(upload.status === 'UNSUPPORTED', '未接入平台时应返回 UNSUPPORTED')
  step('追溯码上传桩', upload.message.slice(0, 30))

  /* ---------- 3. 设置 ---------- */
  const printSettings = await api('GET', '/settings/print')
  assert(printSettings.receiptWidth, '打印设置应返回纸宽')
  const updated = await api('PUT', '/settings/print', { receiptWidth: '80', footer: '冒烟测试页脚' })
  assert(updated.receiptWidth === '80' && updated.footer === '冒烟测试页脚', '打印设置更新未生效')
  step('打印设置', `纸宽 ${updated.receiptWidth}mm`)

  const methods = await api('GET', '/settings/payment-methods')
  assert(methods.length >= 4, '应有默认收款方式')
  const toggled = methods.map((m) => m.method === 'ALIPAY' ? { ...m, enabled: false } : m)
  const savedMethods = await api('PUT', '/settings/payment-methods', toggled)
  assert(savedMethods.find((m) => m.method === 'ALIPAY').enabled === false, '收款方式开关未生效')
  await api('PUT', '/settings/payment-methods', methods)
  step('收款方式设置', `${methods.length} 种，开关生效`)

  const limits = await api('PUT', '/settings/drug-limits/batch',
    { category: 'HERB', stockWarn: 800, stockMax: 20000 })
  assert(limits.updated > 0, '批量设置库存下限应影响药品')
  step('药品库存上下限批量设置', `${limits.updated} 个饮片`)

  /* ---------- 4. 数据导出 ---------- */
  // 注意：fetch 的 response.text() 会吞掉 BOM，因此直接校验原始字节
  const rawBytes = async (path) => {
    const response = await fetch(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } })
    if (response.status !== 200) throw new Error(`GET ${path} HTTP ${response.status}`)
    return new Uint8Array(await response.arrayBuffer())
  }
  const patientBytes = await rawBytes('/export/patients.csv')
  assert(patientBytes[0] === 0xEF && patientBytes[1] === 0xBB && patientBytes[2] === 0xBF,
    'CSV 应带 UTF-8 BOM（EF BB BF）')
  const patientCsv = new TextDecoder('utf-8').decode(patientBytes)
  assert(patientCsv.includes('病历号') && patientCsv.includes('姓名'), 'CSV 表头不正确')
  step('导出患者 CSV', `${patientCsv.split('\r\n').length - 1} 行，带 BOM`)

  const traceBytes = await rawBytes('/export/traces.csv')
  const traceCsv = new TextDecoder('utf-8').decode(traceBytes)
  assert(traceCsv.includes('追溯码'), '追溯码 CSV 应含表头')
  step('导出追溯码 CSV', 'OK')

  /* ---------- 5. 医保政策库 ---------- */
  const policy = await api('POST', '/policies', {
    title: '医疗保障基金使用监督管理条例实施细则',
    issuer: '国家医疗保障局',
    docNo: '国家医疗保障局令第7号',
    category: 'TRACE',
    publishDate: '2026-02-12',
    effectiveDate: '2026-04-01',
    summary: '要求定点医药机构应用药品耗材追溯码并上传',
    content: '第九条……第二十四条……',
    sourceUrl: 'http://www.nhsa.gov.cn/art/2026/2/13/art_173_19681.html'
  })
  assert(policy.id && policy.status === 'EFFECTIVE', '政策应创建并自动判定为生效')
  step('新增政策', `${policy.title}（${policy.status}）`)

  const policyPage = await api('GET', '/policies?keyword=追溯码')
  assert(policyPage.total >= 1, '政策检索应命中')
  const reminders = await api('GET', '/policies/reminders')
  assert(Array.isArray(reminders.hints), '提醒接口应返回 hints')
  step('政策检索与提醒', `命中 ${policyPage.total} 条`)

  await api('PUT', `/policies/${policy.id}`, { ...policy, summary: '已更新摘要' })
  const updatedPolicy = await api('GET', `/policies/${policy.id}`)
  assert(updatedPolicy.summary === '已更新摘要', '政策更新未生效')
  step('修改政策', updatedPolicy.summary)

  /* ---------- 6. 医保统计 ---------- */
  const stats = await api('GET', '/reports/insurance')
  assert(Array.isArray(stats), '医保统计应返回数组')
  const daily = await api('GET', '/reports/insurance/daily')
  assert(daily.date, '医保日统计应返回日期')
  step('医保结算统计', `渠道 ${stats.length} 个，当日笔数 ${daily.count}`)

  console.log(`\nv3 全部通过：${results.length} 项检查 ✅\n`)
}

main().catch((error) => {
  console.error(`\n❌ ${error.message}\n`)
  process.exit(1)
})
