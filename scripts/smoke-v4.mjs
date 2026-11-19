#!/usr/bin/env node
/**
 * v4 冒烟：病历类型与质控、联想、AI 助写、处方安全审查、特殊煎法标注
 * 用法：node scripts/smoke-v4.mjs
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

const completeTcm = {
  recordType: 'TCM',
  chiefComplaint: '乏力纳差 2 周',
  presentIllness: '患者 2 周前无明显诱因出现乏力，食欲减退，餐后腹胀，未予系统诊治。',
  pastHistory: '高血压病史 3 年，规律服药。',
  inspection: '面色萎黄，神疲乏力',
  auscultation: '语声低微，未闻及异常气味',
  inquiry: '纳差，眠可，二便调',
  palpation: '腹软无压痛',
  tongue: '舌淡红，苔薄白',
  pulse: '脉细弱',
  tcmDisease: '虚劳',
  syndrome: '脾胃气虚证',
  treatmentPrinciple: '健脾益气，和胃消食',
  advice: '忌生冷油腻，规律饮食'
}

async function main() {
  console.log(`\n== 方笺 v4 冒烟 @ ${BASE} ==\n`)
  const login = await api('POST', '/auth/login', { username: 'admin', password: '123456' })
  token = login.token
  step('登录', login.user.realName)

  // 1. 联想
  const bySyndrome = await api('GET', '/tcm/suggest?category=SYNDROME&keyword=' + encodeURIComponent('气虚'))
  assert(bySyndrome.length > 0 && bySyndrome.some((s) => s.term.includes('气虚')), '按关键字应联想出证型')
  step('联想（关键字）', bySyndrome[0].term)
  const byPinyin = await api('GET', '/tcm/suggest?category=SYNDROME&keyword=pwqx')
  assert(byPinyin.some((s) => s.pinyin === 'PWQXZ'), '拼音首字母应能联想')
  step('联想（拼音）', byPinyin[0].term)

  // 2. 质控
  const badQc = await api('POST', '/tcm/qc', { recordType: 'TCM', chiefComplaint: '乏力' })
  assert(!badQc.passed, '缺项病历应不通过')
  const blockIssues = badQc.issues.filter((i) => i.level === 'BLOCK')
  assert(blockIssues.length >= 5, `应有多项必填缺失，实际 ${blockIssues.length}`)
  step('质控拦截', `${blockIssues.length} 项必填缺失`)
  const goodQc = await api('POST', '/tcm/qc', completeTcm)
  assert(goodQc.passed, '完整中医病历应通过质控')
  step('质控通过', `issues ${goodQc.issues.length} 条（非阻断）`)
  const badWestern = await api('POST', '/tcm/qc', { recordType: 'WESTERN', chiefComplaint: '头痛 3 天', presentIllness: '头痛 3 天，呈胀痛，无恶心呕吐。', pastHistory: '无' })
  assert(!badWestern.passed && badWestern.issues.some((i) => i.field === 'physicalExam'), '西医病历缺体格检查应拦截')
  step('西医必填校验', '缺体格检查被拦截')

  // 3. AI 助写（默认本地模板）
  const aiSettings = await api('GET', '/ai/settings')
  assert(aiSettings.provider === 'LOCAL', '默认应为本地模板')
  const draft = await api('POST', '/ai/assist', {
    field: 'presentIllness', recordType: 'TCM',
    context: { chiefComplaint: '乏力纳差', duration: '2周', tongue: '淡红', pulse: '细弱' }
  })
  assert(draft.text && draft.text.length > 10, '应生成草稿')
  assert(draft.source === 'LOCAL', '未开启模型时应为本地模板')
  step('AI 助写（本地）', draft.text.slice(0, 24) + '…')

  // 4. 处方安全预检
  const drugs = await api('GET', '/drugs?page=1&size=200')
  const find = (name) => drugs.list.find((d) => d.name === name)
  const fuzi = find('附子')
  const banxia = find('法半夏')
  assert(fuzi && banxia, '应有附子与法半夏（V5 种子数据）')
  const conflict = await api('POST', '/prescriptions/check', {
    items: [{ drugId: fuzi.id, dose: 9, quantity: 63 }, { drugId: banxia.id, dose: 9, quantity: 63 }],
    pregnant: false
  })
  assert(!conflict.passed, '附子+半夏应判定十八反')
  assert(conflict.issues.some((i) => i.type === 'EIGHTEEN_ANTAGONISM'), '应命中十八反规则')
  step('十八反拦截', conflict.issues[0].message)

  const pregnantCheck = await api('POST', '/prescriptions/check', {
    items: [{ drugId: fuzi.id, dose: 9, quantity: 63 }], pregnant: true
  })
  assert(!pregnantCheck.passed, '妊娠状态用附子应拦截')
  assert(pregnantCheck.issues.some((i) => i.type === 'PREGNANCY_FORBIDDEN'), '应命中妊娠禁用')
  step('妊娠禁用拦截', pregnantCheck.issues[0].message)

  const annotated = await api('POST', '/prescriptions/check', {
    items: [{ drugId: fuzi.id, dose: 9, quantity: 63 }], pregnant: false
  })
  assert(annotated.annotations.some((a) => a.drugName === '附子' && a.specialHandling === 'PRE_DECOCT'),
    '附子应自动标注先煎')
  step('特殊煎法标注', annotated.annotations[0].note)

  const doseWarn = await api('POST', '/prescriptions/check', {
    items: [{ drugId: fuzi.id, dose: 30, quantity: 30 }], pregnant: false
  })
  assert(doseWarn.issues.some((i) => i.type === 'DOSE_RANGE'), '超剂量应提示')
  step('剂量范围提示', doseWarn.issues.find((i) => i.type === 'DOSE_RANGE').message)

  // 5. 真实开方：自动写煎法 + 阻断配伍禁忌
  const doctor = (await api('GET', '/users/doctors'))[0]
  const patient = await api('POST', '/patients', { name: '病历测试患者', gender: 'FEMALE', phone: '13900004444' })
  const visit = await api('POST', '/visits/register', { patientId: patient.id, doctorId: doctor.id, type: 'FIRST', complaint: '乏力纳差 2 周' })
  await api('POST', `/visits/${visit.id}/start`)

  const rx = await api('POST', '/prescriptions', {
    visitId: visit.id, type: 'HERB', decoctionMethod: '自煎', doses: 7,
    items: [{ drugId: fuzi.id, dose: 9, quantity: 63, usageText: '水煎服' }]
  })
  const fuziItem = rx.items.find((i) => i.drugId === fuzi.id)
  assert(fuziItem.usageText.includes('先煎'), `附子用法应自动追加先煎，实际：${fuziItem.usageText}`)
  step('开方自动标注煎法', fuziItem.usageText)

  const blocked = await expectFailure('POST', '/prescriptions', {
    visitId: visit.id, type: 'HERB', doses: 7,
    items: [{ drugId: fuzi.id, dose: 9, quantity: 63 }, { drugId: banxia.id, dose: 9, quantity: 63 }]
  }, '禁忌')
  step('开方阻断配伍禁忌', blocked.slice(0, 40) + '…')

  // 6. 病历保存：缺项拒绝、完整通过、类型正确
  const reject = await expectFailure('PUT', `/visits/${visit.id}/record`, { recordType: 'TCM', chiefComplaint: '乏力' }, '必填')
  step('保存缺项病历被拒', reject.slice(0, 30) + '…')
  const saved = await api('PUT', `/visits/${visit.id}/record`, completeTcm)
  assert(saved.recordType === 'TCM' && saved.tcmDisease === '虚劳', '中医病历应保存病名与类型')
  step('保存中医病历', `证型 ${saved.syndrome} / 版本 ${saved.version}`)

  const westernSaved = await api('PUT', `/visits/${visit.id}/record`, {
    recordType: 'WESTERN', chiefComplaint: '头痛 3 天',
    presentIllness: '患者 3 天前出现头痛，呈胀痛，无恶心呕吐，未服药。',
    pastHistory: '无', physicalExam: 'T 36.6℃，BP 120/80mmHg，神清，心肺腹未见异常。',
    auxiliaryExam: '暂缺', westernDiagnosis: '上呼吸道感染', icdCode: 'J06.9', advice: '多饮水，必要时复诊'
  })
  assert(westernSaved.recordType === 'WESTERN' && westernSaved.icdCode === 'J06.9', '西医病历应保存类型与 ICD')
  step('保存西医病历', `${westernSaved.westernDiagnosis} ${westernSaved.icdCode}`)

  // 7. 药品临床属性维护
  const updated = await api('PUT', `/drugs/${banxia.id}/clinical`, {
    specialHandling: 'NORMAL', doseMin: 3, doseMax: 9, pregnancyLevel: 'CAUTION',
    toxicityLevel: 'MILD', functions: '燥湿化痰，降逆止呕'
  })
  assert(updated.pregnancyLevel === 'CAUTION' && updated.functions.includes('化痰'), '临床属性应保存')
  step('药品临床属性', `${updated.name} ${updated.pregnancyLevel}/${updated.toxicityLevel}`)

  console.log(`\nv4 全部通过：${results.length} 项检查 ✅\n`)
}

main().catch((error) => { console.error(`\n❌ ${error.message}\n`); process.exit(1) })
