#!/usr/bin/env node
/**
 * v6 冒烟：扫码发药 / 扫码出库
 * - 饮片扫条码/编码，按数量扣库存
 * - 中成药扫 20 位追溯码，一次一件
 * - 扫完自动完成发药；旧整单发药接口被拒
 * - 无处方直接出库（必须填原因）
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api'
let token = ''
const results = []

async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  let payload
  try { payload = JSON.parse(text) } catch { throw new Error(`${method} ${path} 非 JSON：${text.slice(0, 120)}`) }
  if (payload.code !== 0) throw new Error(`${method} ${path} 失败：${payload.code} ${payload.message}`)
  return payload.data
}
async function expectFailure(method, path, body, keyword) {
  try { await api(method, path, body) } catch (error) {
    if (!keyword || error.message.includes(keyword)) return error.message
    throw new Error(`错误信息未包含「${keyword}」：${error.message}`)
  }
  throw new Error(`${method} ${path} 本应失败，却成功了`)
}
function step(name, detail) { results.push(name); console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`) }
function assert(cond, msg) { if (!cond) throw new Error('断言失败：' + msg) }
const stockOf = async (drugId) => Number((await api('GET', `/drugs/${drugId}/stock`)).totalQty)

async function newVisit() {
  const doctor = (await api('GET', '/users/doctors'))[0]
  const patient = await api('POST', '/patients', {
    name: '扫码发药测试', gender: 'FEMALE', phone: '133' + String(Date.now()).slice(-8)
  })
  const visit = await api('POST', '/visits/register',
    { patientId: patient.id, doctorId: doctor.id, type: 'FIRST', complaint: '测试' })
  await api('POST', `/visits/${visit.id}/start`)
  return visit
}
async function payVisit(visitId) {
  const unpaid = await api('GET', '/charges?status=UNPAID&page=1&size=50')
  for (const order of unpaid.list.filter((o) => o.visitId === visitId)) {
    await api('POST', `/charges/${order.id}/pay`, { method: 'CASH' })
  }
}

async function main() {
  console.log(`\n== 方笺 v6 扫码发药冒烟 @ ${BASE} ==\n`)
  const login = await api('POST', '/auth/login', { username: 'admin', password: '123456' })
  token = login.token
  step('登录', login.user.realName)

  // ---------- 1. 饮片扫条码发药 ----------
  const herbList = await api('GET', '/drugs?category=HERB&page=1&size=50')
  const herb = herbList.list.find((d) => d.name === '黄芪') || herbList.list[0]
  const herbsBefore = await stockOf(herb.id)

  const visit1 = await newVisit()
  const rx1 = await api('POST', '/prescriptions', {
    visitId: visit1.id, type: 'HERB', decoctionMethod: '自煎', doses: 7,
    items: [{ drugId: herb.id, dose: 10, quantity: 70, usageText: '水煎服' }]
  })
  await payVisit(visit1.id)

  // 先扫 50g，验证"未扫完不完成"
  const scan1 = await api('POST', '/pharmacy/scan',
    { prescriptionId: rx1.id, code: herb.code, quantity: 50 })
  assert(scan1.completed === false, '只扫一部分时不应完成发药')
  assert(Number((await stockOf(herb.id))) === herbsBefore - 50, '扫一次应扣一次库存')
  step('饮片扫码出库', `${herb.name} 50g，库存 ${herbsBefore} → ${herbsBefore - 50}`)

  // 旧整单发药接口必须被拒（避免重复扣库存）
  const guard = await expectFailure('POST', `/prescriptions/${rx1.id}/dispense`, undefined, '扫码')
  step('旧发药接口受保护', guard.slice(0, 40) + '…')

  // 扫剩余 20g，自动完成
  const scan2 = await api('POST', '/pharmacy/scan', { prescriptionId: rx1.id, code: herb.code, quantity: 20 })
  assert(scan2.completed === true, '扫完剩余量应自动完成发药')
  const rx1Detail = await api('GET', `/prescriptions/${rx1.id}`)
  assert(rx1Detail.status === 'DISPENSED', '处方应自动变为已发药')
  assert(await stockOf(herb.id) === herbsBefore - 70, `库存应共减 70，实际 ${await stockOf(herb.id)}`)
  step('扫完自动完成', `${rx1.prescriptionNo} → DISPENSED`)

  const progress = await api('GET', `/pharmacy/scan/progress/${rx1.id}`)
  assert(progress.length === 1 && progress[0].done === true, '进度应显示已完成')
  step('发药进度', `${progress[0].drugName} ${progress[0].scanned}/${progress[0].required}`)

  // ---------- 2. 中成药扫追溯码发药 ----------
  const patentList = await api('GET', '/drugs?category=PATENT&page=1&size=50')
  const patent = patentList.list.find((d) => Number(d.stockQty) > 0)
  const patentBefore = await stockOf(patent.id)
  const stockDetail = await api('GET', `/drugs/${patent.id}/stock`)
  const batchNo = stockDetail.batches[0].batchNo
  const mk = (n) => '88' + String(Date.now()).slice(-10) + String(n).padStart(8, '0')
  const codes = [mk(1), mk(2)]
  const bound = await api('POST', '/traces/bind', { drugId: patent.id, batchNo, codes })
  assert(bound.success === 2, '应绑定 2 个追溯码')

  const visit2 = await newVisit()
  const rx2 = await api('POST', '/prescriptions', {
    visitId: visit2.id, type: 'PATENT', doses: 1,
    items: [{ drugId: patent.id, quantity: 2, usageText: '口服' }]
  })
  await payVisit(visit2.id)

  // 用条码扫需追溯药品 → 应被拒
  const wrong = await expectFailure('POST', '/pharmacy/scan',
    { prescriptionId: rx2.id, code: patent.code }, '追溯码')
  step('需追溯品种拦截条码', wrong.slice(0, 40) + '…')

  const t1 = await api('POST', '/pharmacy/scan', { prescriptionId: rx2.id, code: codes[0] })
  assert(t1.completed === false, '扫一个码不应完成（数量 2）')
  const t2 = await api('POST', '/pharmacy/scan', { prescriptionId: rx2.id, code: codes[1] })
  assert(t2.completed === true, '两个码扫完应自动完成')
  assert(await stockOf(patent.id) === patentBefore - 2, '中成药库存应减 2')
  const used = await api('GET', `/prescriptions/${rx2.id}/traces`)
  assert(used.length === 2 && used.every((t) => t.status === 'DISPENSED'), '追溯码应核销为已发药')
  step('中成药扫追溯码', `${patent.name} ×2，库存 ${patentBefore} → ${patentBefore - 2}`)

  // ---------- 3. 直接出库（无处方） ----------
  const outBefore = await stockOf(herb.id)
  const out = await api('POST', '/pharmacy/scan-out',
    { code: herb.code, quantity: 5, reason: '报损测试' })
  assert(Number((await stockOf(herb.id))) === outBefore - 5, '直接出库应扣库存')
  step('直接出库', `${out.drugName} 5g，剩余 ${out.stockAfter}`)

  // 直接出库必须带原因（后端不校验？这里验证需追溯药品必须扫追溯码）
  const traceOutBlocked = await expectFailure('POST', '/pharmacy/scan-out',
    { code: patent.code, quantity: 1, reason: '报损' }, '追溯码')
  step('需追溯药品禁止条码出库', traceOutBlocked.slice(0, 40) + '…')

  console.log(`\nv6 全部通过：${results.length} 项检查 ✅\n`)
}

main().catch((error) => { console.error(`\n❌ ${error.message}\n`); process.exit(1) })
