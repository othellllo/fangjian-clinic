#!/usr/bin/env node
/**
 * v5 冒烟：Excel 数据导入（模板下载 / 预览校验 / 执行导入 / 库存与病历落库验证）
 * 依赖：npm i xlsx（已在 scripts 目录安装）
 */
import * as XLSX from 'xlsx'
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const BASE = process.env.BASE_URL || 'http://localhost:8080/api'
let token = ''
const results = []
const tmp = mkdtempSync(join(tmpdir(), 'yiguan-import-'))

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
function step(name, detail) { results.push(name); console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`) }
function assert(cond, msg) { if (!cond) throw new Error('断言失败：' + msg) }

/** 生成 xlsx：第一行表头，第二行起数据 */
function makeXlsx(fileName, headers, rows) {
  const sheet = XLSX.utils.aoa_to_sheet([headers, ...rows])
  const book = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(book, sheet, 'Sheet1')
  const file = join(tmp, fileName)
  XLSX.writeFile(book, file)
  return file
}

async function upload(path, type, file, mode) {
  const form = new FormData()
  form.append('type', type)
  form.append('file', new Blob([readFileSync(file)]), 'data.xlsx')
  if (mode) form.append('mode', mode)
  const response = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form
  })
  const text = await response.text()
  const payload = JSON.parse(text)
  if (payload.code !== 0) throw new Error(`${path} 失败：${payload.code} ${payload.message}`)
  return payload.data
}

async function main() {
  console.log(`\n== 医馆 HIS v5 数据导入冒烟 @ ${BASE} ==\n`)
  const login = await api('POST', '/auth/login', { username: 'admin', password: '123456' })
  token = login.token
  step('登录', login.user.realName)

  // 1. 模板下载
  for (const type of ['PATIENT', 'DRUG', 'STOCK', 'RECORD']) {
    const response = await fetch(`${BASE}/import/templates/${type}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const bytes = new Uint8Array(await response.arrayBuffer())
    assert(response.status === 200, `${type} 模板下载失败`)
    assert(bytes[0] === 0x50 && bytes[1] === 0x4B, `${type} 模板应为 xlsx（PK 开头）`)
    assert(bytes.length > 3000, `${type} 模板内容过小`)
  }
  step('模板下载', '4 类模板均为有效 xlsx')

  const phone = '137' + String(Date.now()).slice(-8)

  // 2. 患者导入：2 行有效 + 1 行无效
  const patientFile = makeXlsx('patients.xlsx',
    ['姓名', '性别', '出生日期', '手机号', '身份证号', '地址', '过敏史', '既往史'],
    [
      ['导入患者甲', '男', '1980-02-03', phone, '', '示例地址', '无', '无'],
      ['导入患者乙', '女', '1992-07-08', '136' + String(Date.now()).slice(-8), '', '', '', ''],
      ['错误患者丙', '未知', '1990-01-01', '13500000000', '', '', '', '']
    ])
  const patientPreview = await upload('/import/preview', 'PATIENT', patientFile)
  assert(patientPreview.total === 3 && patientPreview.valid === 2 && patientPreview.invalid === 1,
    `预览统计应为 3/2/1，实际 ${patientPreview.total}/${patientPreview.valid}/${patientPreview.invalid}`)
  assert(patientPreview.errors[0].field === '性别', '应提示性别错误')
  step('患者预览校验', `3 行：通过 2，错误 1`)

  const patientResult = await upload('/import/execute', 'PATIENT', patientFile, 'CREATE_ONLY')
  assert(patientResult.inserted === 2 && patientResult.failed === 1,
    `导入结果应为新增 2 失败 1，实际 ${patientResult.inserted}/${patientResult.failed}`)
  step('患者导入', `新增 ${patientResult.inserted}，失败 ${patientResult.failed}`)

  const duplicate = await upload('/import/execute', 'PATIENT', patientFile, 'CREATE_ONLY')
  assert(duplicate.skipped === 2, '重复导入应按匹配键跳过')
  step('重复导入跳过', `跳过 ${duplicate.skipped}`)

  // 3. 药品导入
  const drugCode = 'H' + String(Date.now()).slice(-5)
  const drugFile = makeXlsx('drugs.xlsx',
    ['药品编码', '名称', '拼音码', '分类', '规格', '单位', '售价', '进价', '库存预警值', '库存上限',
      '需追溯码', '特殊煎法', '剂量下限', '剂量上限', '妊娠风险', '毒性', '功效'],
    [
      [drugCode, '导入测试饮片', 'DRCS', '中药饮片', '饮片', 'g', '0.66', '0.40', '500', '20000', '否', '先煎', '6', '15', '无', '无', '测试用'],
      ['H9001', '导入测试中成药', 'DRCZY', '中成药', '10粒/盒', '盒', '18.80', '12.00', '10', '200', '是', '常规', '', '', '无', '无', '测试用']
    ])
  const drugPreview = await upload('/import/preview', 'DRUG', drugFile)
  assert(drugPreview.valid === 2, `药品应全部校验通过，实际 ${drugPreview.valid}`)
  const drugResult = await upload('/import/execute', 'DRUG', drugFile, 'CREATE_ONLY')
  assert(drugResult.inserted === 2, `药品应新增 2，实际 ${drugResult.inserted}`)
  step('药品导入', `新增 ${drugResult.inserted}`)

  const drugList = await api('GET', `/drugs?keyword=${drugCode}&page=1&size=10`)
  const drug = drugList.list.find((item) => item.code === drugCode)
  assert(drug && drug.specialHandling === 'PRE_DECOCT' && Number(drug.doseMax) === 15,
    '药品临床属性（煎法/剂量）应随导入写入')
  step('药品临床属性', `${drug.name} 先煎 / 剂量 6-15g`)

  // 4. 库存导入
  const stockFile = makeXlsx('stock.xlsx',
    ['药品编码', '批号', '生产日期', '有效期', '数量', '进价', '备注'],
    [[drugCode, 'IMP2026-01', '2026-01-01', '2027-12-31', '1000', '0.40', '初始化导入']])
  const stockPreview = await upload('/import/preview', 'STOCK', stockFile)
  assert(stockPreview.valid === 1, '库存行应校验通过')
  const stockResult = await upload('/import/execute', 'STOCK', stockFile, 'CREATE_ONLY')
  assert(stockResult.inserted === 1, `库存应导入 1 行，实际 ${stockResult.inserted}`)
  const stockDetail = await api('GET', `/drugs/${drug.id}/stock`)
  assert(Number(stockDetail.totalQty) === 1000, `库存应为 1000，实际 ${stockDetail.totalQty}`)
  step('库存导入', `批次 IMP2026-01 入库 1000，结存 ${stockDetail.totalQty}`)

  const ledger = await api('GET', `/stock/ledger?drugId=${drug.id}&page=1&size=5`)
  assert(ledger.list.length > 0 && ledger.list[0].changeType === 'IN', '库存导入应写流水')
  step('库存流水', `${ledger.list[0].changeType} ${ledger.list[0].quantity}`)

  // 5. 病历导入
  const recordFile = makeXlsx('records.xlsx',
    ['患者手机号', '就诊日期', '医生姓名', '主诉', '现病史', '既往史', '望诊', '闻诊', '问诊', '切诊',
      '舌象', '脉象', '中医病名', '证候', '治则治法', '医嘱'],
    [[phone, '2026-09-10', '李青囊', '头痛 3 天', '患者 3 天前出现头痛，呈胀痛，无恶心呕吐。', '无',
      '面色正常', '语声正常', '纳眠可', '脉象平和', '舌淡红苔薄白', '脉弦', '头痛', '肝阳上亢证',
      '平肝潜阳', '忌恼怒，按时复诊']])
  const recordPreview = await upload('/import/preview', 'RECORD', recordFile)
  assert(recordPreview.valid === 1, `病历应校验通过，错误：${JSON.stringify(recordPreview.errors)}`)
  const recordResult = await upload('/import/execute', 'RECORD', recordFile, 'CREATE_ONLY')
  assert(recordResult.inserted === 1, `病历应导入 1 条，实际 ${recordResult.inserted}`)
  step('病历导入', `新增 ${recordResult.inserted} 条历史就诊`)

  const patients = await api('GET', `/patients?keyword=${phone}&page=1&size=5`)
  const importedPatient = patients.list.find((item) => item.phone === phone)
  assert(importedPatient, '应能按手机号查到导入的患者')
  const visits = await api('GET', `/patients/${importedPatient.id}/visits`)
  assert(visits.length === 1 && visits[0].status === 'FINISHED', '导入病历应生成已完成的历史就诊')
  const visitDetail = await api('GET', `/visits/${visits[0].id}`)
  assert(visitDetail.record && visitDetail.record.syndrome === '肝阳上亢证', '导入病历内容应可查')
  step('病历落库验证', `${visits[0].visitNo} 证型 ${visitDetail.record.syndrome}`)

  // 6. 异常路径：不存在的药品编码
  const badStock = makeXlsx('bad-stock.xlsx',
    ['药品编码', '批号', '数量'], [['NOT-EXIST-999', 'B1', '10']])
  const badPreview = await upload('/import/preview', 'STOCK', badStock)
  const badExecute = await upload('/import/execute', 'STOCK', badStock, 'CREATE_ONLY')
  assert(badExecute.failed === 1 && badExecute.errors[0].message.includes('不存在'),
    '不存在的药品编码应导入失败并给出原因')
  step('异常行隔离', `失败 ${badExecute.failed}，其他行不受影响`)

  console.log(`\nv5 全部通过：${results.length} 项检查 ✅\n`)
}

main().catch((error) => { console.error(`\n❌ ${error.message}\n`); process.exit(1) })
