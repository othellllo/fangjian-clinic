#!/usr/bin/env node
/**
 * v8 冒烟：结构化用法（给药途径 + 频次）
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api'
let token = ''
const results = []
async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  const text = await response.text()
  const payload = JSON.parse(text)
  if (payload.code !== 0) throw new Error(`${method} ${path} 失败：${payload.code} ${payload.message}`)
  return payload.data
}
function step(n, d) { results.push(n); console.log(`  ✓ ${n}${d ? ' — ' + d : ''}`) }
function assert(c, m) { if (!c) throw new Error('断言失败：' + m) }

async function newVisit() {
  const doctor = (await api('GET', '/users/doctors'))[0]
  const patient = await api('POST', '/patients', { name: '用法测试', gender: 'MALE', phone: '130' + String(Date.now()).slice(-8) })
  const visit = await api('POST', '/visits/register', { patientId: patient.id, doctorId: doctor.id, type: 'FIRST', complaint: '测试' })
  await api('POST', `/visits/${visit.id}/start`)
  return visit
}

async function main() {
  console.log(`\n== 方笺 v8 结构化用法冒烟 @ ${BASE} ==\n`)
  const login = await api('POST', '/auth/login', { username: 'admin', password: '123456' })
  token = login.token
  step('登录', login.user.realName)

  // 1. 字典
  const dict = await api('GET', '/dict/dosage')
  assert(dict.routes.length >= 10, '给药途径应有足够选项')
  assert(dict.frequencies.some((f) => f.code === 'tid' && f.label === '一日三次'), '频次应包含 tid 一日三次')
  assert(dict.routes.some((r) => r.code === 'jian'), '途径应包含煎服')
  step('用法字典', `${dict.routes.length} 种途径 / ${dict.frequencies.length} 种频次`)

  // 2. 结构化用法 → 自动拼中文
  const visit = await newVisit()
  const herbs = await api('GET', '/drugs?category=HERB&page=1&size=20')
  const herb = herbs.list.find((d) => d.name === '黄芪') || herbs.list[0]
  const rx = await api('POST', '/prescriptions', {
    visitId: visit.id, type: 'HERB', decoctionMethod: '自煎', doses: 7,
    items: [{ drugId: herb.id, dose: 10, quantity: 70, usageRouteCode: 'jian', usageFrequencyCode: 'bid' }]
  })
  const item = rx.items[0]
  assert(item.usageRoute === '煎服（水煎服）', `途径应转中文，实际 ${item.usageRoute}`)
  assert(item.usageFrequency === '一日两次', `频次应转中文，实际 ${item.usageFrequency}`)
  assert(item.usageText.includes('煎服') && item.usageText.includes('一日两次'), `用法文本应拼装，实际 ${item.usageText}`)
  step('结构化用法拼装', item.usageText)

  // 3. 自定义用法优先（不被覆盖）
  const visit2 = await newVisit()
  const rx2 = await api('POST', '/prescriptions', {
    visitId: visit2.id, type: 'PATENT', doses: 1,
    items: [{ drugId: herb.id, dose: 10, quantity: 10, usageText: '饭后温服，忌辛辣' }]
  })
  assert(rx2.items[0].usageText.startsWith('饭后温服'), '自定义用法应保留')
  step('自定义用法优先', rx2.items[0].usageText)

  // 4. 特殊煎法仍会追加
  const visit3 = await newVisit()
  const all = await api('GET', '/drugs?page=1&size=200')
  const fuzi = all.list.find((d) => d.name === '附子')
  assert(fuzi, '应存在附子（种子数据）')
  const rx3 = await api('POST', '/prescriptions', {
    visitId: visit3.id, type: 'HERB', doses: 7,
    items: [{ drugId: fuzi.id, dose: 9, quantity: 63, usageRouteCode: 'jian', usageFrequencyCode: 'qd' }]
  })
  assert(rx3.items[0].usageText.includes('先煎'), '先煎仍应自动追加')
  step('用法 + 煎法叠加', rx3.items[0].usageText)

  // 5. 未知代码忽略
  const visit4 = await newVisit()
  const rx4 = await api('POST', '/prescriptions', {
    visitId: visit4.id, type: 'PATENT', doses: 1,
    items: [{ drugId: herb.id, quantity: 5, usageRouteCode: 'not_exist', usageFrequencyCode: 'nope' }]
  })
  assert(!rx4.items[0].usageRoute && !rx4.items[0].usageFrequency, '未知代码应忽略')
  step('未知代码容错', '未写入用法')

  console.log(`\nv8 全部通过：${results.length} 项检查 ✅\n`)
}
main().catch((e) => { console.error(`\n❌ ${e.message}\n`); process.exit(1) })
