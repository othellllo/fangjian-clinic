#!/usr/bin/env node
/**
 * v7 冒烟：开方效率相关接口
 * - 药品联想支持名称 / 编码 / 拼音首字母模糊
 * - 常见项（光标进入空输入框时展示）返回历史高频 + 标准术语
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api'
let token = ''
const results = []
async function api(method, path) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { method, headers })
  const payload = JSON.parse(await response.text())
  if (payload.code !== 0) throw new Error(`${path} 失败：${payload.code} ${payload.message}`)
  return payload.data
}
function step(name, detail) { results.push(name); console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`) }
function assert(cond, msg) { if (!cond) throw new Error('断言失败：' + msg) }

async function main() {
  console.log(`\n== 方笺 v7 开方效率冒烟 @ ${BASE} ==\n`)
  const login = await fetch(`${BASE}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' })
  }).then((r) => r.json())
  token = login.data.token
  step('登录', login.data.user.realName)

  // 1. 拼音首字母模糊搜索
  const byPinyin = await api('GET', '/drugs/suggest?keyword=hq&limit=15')
  assert(byPinyin.length > 0, '拼音首字母 hq 应能搜到药品')
  assert(byPinyin.every((d) => (d.pinyin || '').toUpperCase().includes('HQ') || d.code.toUpperCase().includes('HQ') || d.name.includes('hq')),
    '结果应与拼音码/编码匹配')
  step('拼音首字母搜索', byPinyin.map((d) => d.name).slice(0, 4).join('、'))

  // 2. 中文与编码搜索
  const byChinese = await api('GET', '/drugs/suggest?keyword=' + encodeURIComponent('黄') + '&limit=10')
  assert(byChinese.length > 0 && byChinese.every((d) => d.name.includes('黄') || (d.pinyin || '').includes('黄')), '中文关键字应匹配名称')
  step('中文搜索', byChinese.map((d) => d.name).slice(0, 3).join('、'))

  const byCode = await api('GET', '/drugs/suggest?keyword=H001&limit=5')
  assert(byCode.length >= 1 && byCode[0].code.includes('H001'), '编码应可精确/模糊匹配')
  step('编码搜索', `${byCode[0].code} ${byCode[0].name}`)

  // 3. 结果字段（开方下拉需要价格/库存/规格）
  const one = byPinyin[0]
  assert(one.price !== undefined && one.unit && one.stockQty !== undefined, '联想结果应含价格/单位/库存')
  step('联想字段完整', `${one.name} ${one.spec || ''} ${one.price}元/${one.unit} 库存${one.stockQty}`)

  // 4. limit 生效
  const limited = await api('GET', '/drugs/suggest?keyword=&limit=3')
  assert(limited.length <= 3, 'limit 应生效')
  step('limit 生效', `${limited.length} 条`)

  // 5. 常见证型（光标进入即展示）
  const syndromes = await api('GET', '/tcm/common?category=SYNDROME&limit=10')
  assert(syndromes.length > 0, '常见证型不应为空')
  assert(syndromes[0].source === 'SYSTEM' || syndromes[0].source === 'HISTORY', '来源标记应存在')
  step('常见证型', `${syndromes.length} 条，首条「${syndromes[0].term}」(${syndromes[0].source})`)

  // 6. 常见主诉 / 西医诊断
  const symptoms = await api('GET', '/tcm/common?category=SYMPTOM&limit=10')
  const diagnoses = await api('GET', '/tcm/common?category=DIAGNOSIS&limit=10')
  assert(symptoms.length > 0, '常见主诉不应为空')
  assert(diagnoses.length > 0, '常见西医诊断不应为空')
  step('常见主诉 / 诊断', `${symptoms.length} 条 / ${diagnoses.length} 条`)

  // 7. 历史高频优先（先写入一条病历再查）
  const doctor = (await api('GET', '/users/doctors'))[0]
  const patient = await (await fetch(`${BASE}/patients`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ name: '高频项测试', gender: 'MALE', phone: '132' + String(Date.now()).slice(-8) })
  })).json()
  const visit = await (await fetch(`${BASE}/visits/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ patientId: patient.data.id, doctorId: doctor.id, type: 'FIRST', complaint: '测试' })
  })).json()
  await api('POST', `/visits/${visit.data.id}/start`)
  await fetch(`${BASE}/visits/${visit.data.id}/record`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      recordType: 'TCM', chiefComplaint: '高频主诉测试 3 天', presentIllness: '用于验证历史高频联想，内容足够长。',
      pastHistory: '无', inspection: '面色正常', auscultation: '未见异常', inquiry: '纳眠可',
      palpation: '腹软', tongue: '舌淡红', pulse: '脉弦', tcmDisease: '测试病名', syndrome: '测试证型',
      treatmentPrinciple: '测试治则', advice: '测试医嘱'
    })
  })
  const after = await api('GET', '/tcm/common?category=SYNDROME&limit=10')
  assert(after.some((s) => s.term === '测试证型' && s.source === 'HISTORY'), '刚保存的证型应出现在历史高频里')
  step('历史高频优先', `含「测试证型」(HISTORY)`)

  console.log(`\nv7 全部通过：${results.length} 项检查 ✅\n`)
}
main().catch((e) => { console.error(`\n❌ ${e.message}\n`); process.exit(1) })
