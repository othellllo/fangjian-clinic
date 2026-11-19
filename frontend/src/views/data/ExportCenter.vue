<template>
  <div class="export-page">
    <div class="page-card">
      <div class="page-title">数据导出</div>
      <div class="page-sub text-muted">按数据类型导出 CSV（UTF-8 BOM 编码，Excel 可直接打开），用于留存与配合检查。</div>

      <el-alert type="info" :closable="false" show-icon class="export-alert">
        <template #title>导出会写入操作审计（导出人、时间、数据范围）</template>
        <div class="alert-body">
          系统数据只增不删；导出仅用于留存、核对与配合检查，请妥善保管导出的文件。
          不选择日期范围时导出全部数据，选择后按创建时间过滤。
        </div>
      </el-alert>

      <div class="export-grid">
        <div v-for="item in EXPORT_TYPES" :key="item.type" class="export-card">
          <div class="card-head">
            <div class="card-icon">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="card-title-box">
              <div class="card-title">
                <span>{{ item.name }}</span>
                <el-tag v-if="item.type === 'audit'" size="small" type="warning" effect="plain">仅管理员</el-tag>
              </div>
              <div class="card-type text-muted">{{ item.type }}</div>
            </div>
          </div>

          <div class="card-desc">
            <span class="desc-label">导出字段</span>
            <span class="desc-text">{{ item.fields }}</span>
          </div>

          <div class="card-actions">
            <el-date-picker v-model="ranges[item.type]" type="daterange" value-format="YYYY-MM-DD"
              range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" clearable
              size="default" class="range-picker" />
            <el-button type="primary" :loading="!!exporting[item.type]" @click="onExport(item)">
              <el-icon><Download /></el-icon>
              <span>导出 CSV</span>
            </el-button>
          </div>
        </div>
      </div>

      <div class="export-foot text-muted">
        导出文件命名规则：医馆系统-类型-日期.csv。若提示无数据，说明所选范围内暂无可导出记录。
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { downloadExport } from '@/api'
import { todayStr } from '@/utils/format'

/* 7 类可导出数据，字段说明用于告诉使用者导出范围 */
const EXPORT_TYPES = [
  {
    type: 'patients',
    name: '患者档案',
    icon: 'User',
    fields: '病历号、姓名、性别、出生日期、年龄、手机号、身份证号、地址、过敏史、慢病史、建档时间'
  },
  {
    type: 'visits',
    name: '就诊记录',
    icon: 'Tickets',
    fields: '就诊号、排队号、患者、医生、就诊类型、状态、挂号费、挂号/接诊/结束时间、主诉'
  },
  {
    type: 'charges',
    name: '收费单',
    icon: 'Money',
    fields: '收费单号、患者、单据类型、应收/优惠/实收金额、收费状态、支付方式、创建/支付时间'
  },
  {
    type: 'prescriptions',
    name: '处方',
    icon: 'Document',
    fields: '处方号、患者、医生、处方类型、剂数、煎法、金额、状态、创建时间'
  },
  {
    type: 'stock',
    name: '库存流水',
    icon: 'Files',
    fields: '药品、批次、出入库类型、数量、成本价、关联单号、操作人、操作时间'
  },
  {
    type: 'audit',
    name: '操作审计',
    icon: 'List',
    fields: '操作人、角色、模块、动作、对象、结果、IP、操作时间（仅管理员可导出）'
  },
  {
    type: 'traces',
    name: '药品追溯',
    icon: 'Barcode',
    fields: '追溯码、药品、批次、状态、绑定时间、发药时间、患者、处方、操作人'
  }
]

const ranges = reactive({})
const exporting = reactive({})

function buildFileName(item, range) {
  const date = range && range[0] && range[1] ? `${range[0]}_${range[1]}` : todayStr()
  return `医馆系统-${item.name}-${date}.csv`
}

function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.setTimeout(() => URL.revokeObjectURL(url), 1500)
}

/* 后端若以 HTTP 200 + JSON 返回业务错误，blob 里是 JSON，这里解析出来提示 */
async function rejectJsonBlob(blob) {
  if (!blob || typeof blob.text !== 'function') return false
  const isJson = blob.type && blob.type.toLowerCase().includes('json')
  if (!isJson) return false
  try {
    const body = JSON.parse(await blob.text())
    if (body && body.code !== 0) {
      ElMessage.error(body.message || '导出失败')
      return true
    }
    if (body && body.code === 0) {
      ElMessage.warning('导出接口未返回文件内容')
      return true
    }
  } catch {
    return false
  }
  return false
}

async function onExport(item) {
  if (exporting[item.type]) return
  const range = ranges[item.type]
  const start = range?.[0] || ''
  const end = range?.[1] || ''
  if (start && end && start > end) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return
  }
  const params = {}
  if (start) params.start = start
  if (end) params.end = end

  exporting[item.type] = true
  try {
    const result = await downloadExport(item.type, params)
    const blob = result instanceof Blob
      ? result
      : new Blob([result ?? ''], { type: 'text/csv;charset=utf-8' })
    if (!blob.size) {
      ElMessage.warning('所选范围内暂无可导出数据')
      return
    }
    if (await rejectJsonBlob(blob)) return
    saveBlob(blob, buildFileName(item, range))
    ElMessage.success(`${item.name}已开始下载`)
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    exporting[item.type] = false
  }
}
</script>

<style scoped>
.export-page {
  min-height: calc(100vh - 88px);
}

.page-sub {
  margin: -4px 0 12px;
  line-height: 1.6;
}

.export-alert {
  margin-bottom: 16px;
}

.alert-body {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.7;
  color: #606266;
}

.export-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.export-card {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  background: #fafcff;
  box-sizing: border-box;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.export-card:hover {
  border-color: #c6e2ff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.08);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.card-icon {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: #ecf5ff;
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  flex-shrink: 0;
}

.card-title-box {
  min-width: 0;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
}

.card-type {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  margin-top: 1px;
}

.card-desc {
  flex: 1;
  font-size: 12px;
  line-height: 1.65;
  color: #606266;
  margin-bottom: 12px;
}

.desc-label {
  display: inline-block;
  padding: 0 6px;
  margin-right: 6px;
  border-radius: 3px;
  background: #f0f2f5;
  color: #909399;
  font-size: 12px;
}

.desc-text {
  color: #606266;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.range-picker {
  flex: 1;
  min-width: 260px;
  max-width: 340px;
}

.export-foot {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .export-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
