<template>
  <div class="page-card settings-page">
    <div class="page-title">系统设置</div>
    <div class="text-muted page-sub">收款方式、打印小票与药品库存上下限在此统一维护，修改后立即对收费台、药房与打印生效。</div>

    <el-tabs v-model="activeTab" class="settings-tabs">
      <!-- ================= 收款方式设置 ================= -->
      <el-tab-pane label="收款方式" name="payment">
        <div v-loading="paymentLoading" class="tab-body">
          <div class="section-head">
            <span class="section-title">收款方式</span>
            <span class="text-muted">启用/停用、调整排序；保存后收费台按此顺序展示。</span>
            <div class="flex-spacer" />
            <el-button :loading="paymentLoading" @click="loadPaymentMethods">
              <el-icon><Refresh /></el-icon>
              <span>刷新</span>
            </el-button>
            <el-button type="primary" :loading="paymentSaving" @click="savePaymentMethods">保存设置</el-button>
          </div>

          <el-table :data="paymentList" border stripe class="payment-table" empty-text="暂无收款方式配置">
            <el-table-column label="排序" width="80" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="收款方式" min-width="180">
              <template #default="{ row }">
                <span class="method-name">{{ paymentName(row) }}</span>
                <el-tag v-if="row.method === 'CASH'" size="small" type="success" effect="plain" class="method-tag">必备</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="编码" width="160">
              <template #default="{ row }">
                <span class="text-muted">{{ row.method }}</span>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="100" align="center">
              <template #default="{ row }">
                <el-switch :model-value="row.enabled" @update:model-value="(val) => onPaymentToggle(row, val)" />
              </template>
            </el-table-column>
            <el-table-column label="调整排序" width="160" align="center">
              <template #default="{ $index }">
                <el-button link type="primary" size="small" :disabled="$index === 0" @click="movePayment($index, -1)">
                  上移
                </el-button>
                <el-button link type="primary" size="small" :disabled="$index === paymentList.length - 1"
                  @click="movePayment($index, 1)">
                  下移
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="70" description="暂无收款方式配置" />
            </template>
          </el-table>

          <div class="tip-line">
            <el-icon><InfoFilled /></el-icon>
            <span>现金（CASH）为必备收款方式，不允许停用；保存时至少需要保留一种启用的收款方式。</span>
          </div>
        </div>
      </el-tab-pane>

      <!-- ================= 打印设置 ================= -->
      <el-tab-pane label="打印设置" name="print">
        <div v-loading="printLoading" class="tab-body print-body">
          <el-form ref="printFormRef" :model="printForm" :rules="printRules" label-width="120px" class="print-form">
            <el-form-item label="小票纸宽" prop="receiptWidth">
              <el-radio-group v-model="printForm.receiptWidth">
                <el-radio value="58">58mm（热敏小票）</el-radio>
                <el-radio value="80">80mm（热敏小票）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="小票抬头" prop="header">
              <el-input v-model="printForm.header" maxlength="60" clearable :placeholder="printHeaderPlaceholder" />
              <div class="text-muted form-tip">留空时打印自动使用医馆名称：{{ printForm.clinicName || '（未配置）' }}</div>
            </el-form-item>
            <el-form-item label="页脚文案" prop="footer">
              <el-input v-model="printForm.footer" type="textarea" :rows="2" maxlength="120" show-word-limit
                placeholder="如：谢谢惠顾，祝您早日康复！" />
            </el-form-item>
            <el-form-item label="打印份数" prop="copies">
              <el-input-number v-model="printForm.copies" :min="1" :max="3" :step="1" :precision="0" />
              <span class="text-muted form-tip">支持 1 - 3 份</span>
            </el-form-item>
            <el-form-item label="显示机构名" prop="showClinicName">
              <el-switch v-model="printForm.showClinicName" active-text="打印小票时显示医馆名称" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="printSaving" @click="savePrintSettings">保存打印设置</el-button>
              <el-button :disabled="printLoading" @click="loadPrintSettings">重新读取</el-button>
            </el-form-item>
          </el-form>

          <el-divider />

          <div class="section-head">
            <span class="section-title">打印测试页</span>
            <span class="text-muted">测试页需要具体收费单据，系统不会伪造单据数据。</span>
          </div>
          <div class="print-test">
            <el-button :icon="Printer" @click="onPrintTest">打印测试页</el-button>
            <span class="text-muted">请在「收费台」选择一张收费单后打印，测试会使用该单据的真实数据。</span>
          </div>
          <div class="tip-line">
            <el-icon><InfoFilled /></el-icon>
            <span>小票宽度、抬头、页脚、份数与是否显示机构名，均会应用到后续所有收费小票打印。</span>
          </div>
        </div>
      </el-tab-pane>

      <!-- ================= 药品库存上下限（批量） ================= -->
      <el-tab-pane label="库存上下限" name="drug">
        <div class="tab-body">
          <div class="section-head">
            <span class="section-title">按分类批量设置库存上下限</span>
            <span class="text-muted">一次性覆盖所选分类下全部药品，请确认后操作。</span>
          </div>

          <el-form ref="limitFormRef" :model="limitForm" :rules="limitRules" label-width="120px" class="limit-form"
            inline>
            <el-form-item label="药品分类" prop="category">
              <el-select v-model="limitForm.category" placeholder="请选择分类" class="limit-select"
                @change="onLimitCategoryChange">
                <el-option v-for="(text, key) in DRUG_CATEGORY_TEXT" :key="key" :label="text" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item label="库存下限" prop="stockWarn">
              <el-input-number v-model="limitForm.stockWarn" :min="0" :precision="0" :step="1" />
            </el-form-item>
            <el-form-item label="库存上限" prop="stockMax">
              <el-input-number v-model="limitForm.stockMax" :min="0" :precision="0" :step="1"
                placeholder="留空表示不限制" />
              <span class="text-muted form-tip">上限留空表示不限制</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="limitSaving" @click="onBatchLimits">批量设置</el-button>
            </el-form-item>
          </el-form>

          <el-alert type="info" :closable="false" show-icon class="limit-alert"
            title="库存下限用于低库存预警；库存上限留空即不限制。批量设置会覆盖所选分类下所有药品的现有值。" />

          <div class="section-head list-head">
            <span class="section-title">当前分类药品库存参数</span>
            <span class="text-muted">{{ DRUG_CATEGORY_TEXT[limitForm.category] || limitForm.category }} 共 {{ drugTotal }} 个药品</span>
            <div class="flex-spacer" />
            <el-button :loading="drugLoading" @click="loadDrugList">
              <el-icon><Refresh /></el-icon>
              <span>刷新</span>
            </el-button>
          </div>

          <el-table v-loading="drugLoading" :data="drugList" border stripe class="drug-table" empty-text="该分类下暂无药品">
            <el-table-column prop="code" label="编码" width="120" show-overflow-tooltip />
            <el-table-column prop="name" label="药品名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" min-width="130" show-overflow-tooltip />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column label="库存下限" width="110" align="right">
              <template #default="{ row }">
                <span :class="{ 'warn-value': Number(row.stockWarn || 0) > 0 }">{{ row.stockWarn ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存上限" width="110" align="right">
              <template #default="{ row }">
                <span v-if="row.stockMax === null || row.stockMax === undefined || row.stockMax === ''">不限制</span>
                <span v-else>{{ row.stockMax }}</span>
              </template>
            </el-table-column>
            <el-table-column label="当前库存" width="110" align="right">
              <template #default="{ row }">
                <el-tag v-if="Number(row.stockQty || 0) === 0" size="small" type="danger" effect="dark">0 · 缺货</el-tag>
                <el-tag v-else-if="Number(row.stockQty) < Number(row.stockWarn || 0)" size="small" type="warning" effect="plain">
                  {{ row.stockQty }} · 偏低
                </el-tag>
                <span v-else>{{ row.stockQty ?? 0 }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="70" description="该分类下暂无药品" />
            </template>
          </el-table>

          <div class="pagination-bar">
            <el-pagination v-model:current-page="drugPage" v-model:page-size="drugSize" :total="drugTotal"
              :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background
              @size-change="onDrugSizeChange" @current-change="loadDrugList" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled, Printer, Refresh } from '@element-plus/icons-vue'
import {
  batchDrugLimits,
  getPaymentMethods,
  getPrintSettings,
  pageDrugs,
  updatePaymentMethods,
  updatePrintSettings
} from '@/api'
import { DRUG_CATEGORY_TEXT, PAY_METHOD_TEXT } from '@/utils/format'

/* ================= 通用 ================= */
const activeTab = ref('payment')

function toBool(value, fallback = true) {
  if (value === undefined || value === null || value === '') return fallback
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  const text = String(value).trim().toLowerCase()
  if (['0', 'false', 'no', 'off', 'disabled', 'inactive'].includes(text)) return false
  if (['1', 'true', 'yes', 'on', 'enabled', 'active'].includes(text)) return true
  return fallback
}

/* ================= 收款方式设置 ================= */
const paymentLoading = ref(false)
const paymentSaving = ref(false)
const paymentList = ref([])

function paymentName(row) {
  if (!row) return '-'
  return row.name || PAY_METHOD_TEXT[row.method] || row.method || '-'
}

function normalizePaymentRows(data) {
  const raw = Array.isArray(data)
    ? data
    : Array.isArray(data?.list)
      ? data.list
      : Array.isArray(data?.items)
        ? data.items
        : []
  return raw
    .filter((item) => item && item.method)
    .map((item, index) => ({
      method: String(item.method),
      name: item.name || PAY_METHOD_TEXT[item.method] || String(item.method),
      enabled: toBool(item.enabled, true),
      sort: Number(item.sort ?? index + 1),
      _origin: item
    }))
    .sort((a, b) => a.sort - b.sort)
}

async function loadPaymentMethods() {
  paymentLoading.value = true
  try {
    const data = await getPaymentMethods()
    paymentList.value = normalizePaymentRows(data)
  } catch {
    paymentList.value = []
  } finally {
    paymentLoading.value = false
  }
}

function onPaymentToggle(row, nextValue) {
  if (!nextValue) {
    if (row.method === 'CASH') {
      ElMessage.warning('现金为必备收款方式，不允许停用')
      return
    }
    const enabledCount = paymentList.value.filter((item) => item.enabled && item.method !== row.method).length
    if (enabledCount === 0) {
      ElMessage.warning('至少需要保留一种启用的收款方式')
      return
    }
  }
  row.enabled = nextValue
}

function movePayment(index, offset) {
  const target = index + offset
  if (target < 0 || target >= paymentList.value.length) return
  const rows = paymentList.value.slice()
  const [item] = rows.splice(index, 1)
  rows.splice(target, 0, item)
  paymentList.value = rows
}

async function savePaymentMethods() {
  if (!paymentList.value.length) {
    ElMessage.warning('暂无可保存的收款方式配置')
    return
  }
  if (!paymentList.value.some((item) => item.enabled)) {
    ElMessage.warning('至少需要保留一种启用的收款方式')
    return
  }
  const cash = paymentList.value.find((item) => item.method === 'CASH')
  if (cash && !cash.enabled) {
    ElMessage.warning('现金为必备收款方式，不允许停用')
    return
  }
  paymentSaving.value = true
  try {
    const payload = paymentList.value.map((item, index) => ({
      method: item.method,
      name: item.name || PAY_METHOD_TEXT[item.method] || item.method,
      enabled: Boolean(item.enabled),
      sort: index + 1
    }))
    await updatePaymentMethods(payload)
    ElMessage.success('收款方式设置已保存')
    await loadPaymentMethods()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    paymentSaving.value = false
  }
}

/* ================= 打印设置 ================= */
const printLoading = ref(false)
const printSaving = ref(false)
const printFormRef = ref(null)
const printForm = reactive({
  receiptWidth: '58',
  header: '',
  footer: '',
  copies: 1,
  clinicName: '',
  showClinicName: true
})

const printHeaderPlaceholder = ref('默认使用医馆名称')

const printRules = {
  receiptWidth: [{ required: true, message: '请选择小票纸宽', trigger: 'change' }],
  header: [{ max: 60, message: '抬头不能超过 60 个字符', trigger: 'blur' }],
  footer: [{ max: 120, message: '页脚文案不能超过 120 个字符', trigger: 'blur' }],
  copies: [
    { required: true, type: 'number', message: '请填写打印份数', trigger: 'blur' },
    { type: 'number', min: 1, max: 3, message: '打印份数应为 1 - 3', trigger: 'blur' }
  ]
}

function normalizeCopies(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return 1
  return Math.min(3, Math.max(1, Math.round(num)))
}

async function loadPrintSettings() {
  printLoading.value = true
  try {
    const data = (await getPrintSettings()) || {}
    const clinicName = data.clinicName || ''
    printForm.clinicName = clinicName
    printForm.receiptWidth = String(data.receiptWidth ?? '58') === '80' ? '80' : '58'
    printForm.header = data.header || clinicName || ''
    printForm.footer = data.footer || ''
    printForm.copies = normalizeCopies(data.copies)
    printForm.showClinicName = toBool(data.showClinicName, true)
    refillPrintPlaceholder()
  } catch {
    /* 错误提示由 request 拦截器统一处理，保留默认值 */
    refillPrintPlaceholder()
  } finally {
    printLoading.value = false
  }
}

function refillPrintPlaceholder() {
  printHeaderPlaceholder.value = printForm.clinicName
    ? `默认使用医馆名称：${printForm.clinicName}`
    : '默认使用医馆名称'
}

async function savePrintSettings() {
  const valid = await printFormRef.value?.validate().catch(() => false)
  if (!valid) return
  printSaving.value = true
  try {
    const payload = {
      receiptWidth: printForm.receiptWidth === '80' ? '80' : '58',
      header: printForm.header.trim() || printForm.clinicName || '',
      footer: printForm.footer.trim(),
      copies: normalizeCopies(printForm.copies),
      clinicName: printForm.clinicName,
      showClinicName: Boolean(printForm.showClinicName)
    }
    await updatePrintSettings(payload)
    ElMessage.success('打印设置已保存')
    await loadPrintSettings()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    printSaving.value = false
  }
}

function onPrintTest() {
  ElMessage.warning('打印测试需要具体收费单据，请在「收费台」选择单据后打印')
}

/* ================= 药品库存上下限（批量） ================= */
const limitFormRef = ref(null)
const limitSaving = ref(false)
const limitForm = reactive({
  category: 'HERB',
  stockWarn: 10,
  stockMax: null
})

const limitRules = {
  category: [{ required: true, message: '请选择药品分类', trigger: 'change' }],
  stockWarn: [
    { required: true, type: 'number', message: '请填写库存下限', trigger: 'blur' },
    { type: 'number', min: 0, message: '库存下限不能小于 0', trigger: 'blur' }
  ],
  stockMax: [{ type: 'number', min: 0, message: '库存上限不能小于 0', trigger: 'blur' }]
}

const drugLoading = ref(false)
const drugList = ref([])
const drugPage = ref(1)
const drugSize = ref(10)
const drugTotal = ref(0)
const drugLoaded = ref(false)

async function loadDrugList() {
  if (!limitForm.category) return
  drugLoading.value = true
  try {
    const data = await pageDrugs({
      category: limitForm.category,
      page: drugPage.value,
      size: drugSize.value
    })
    drugList.value = Array.isArray(data?.list) ? data.list : Array.isArray(data) ? data : []
    drugTotal.value = Array.isArray(data) ? data.length : Number(data?.total ?? 0)
    drugLoaded.value = true
  } catch {
    drugList.value = []
    drugTotal.value = 0
  } finally {
    drugLoading.value = false
  }
}

function onDrugSizeChange() {
  drugPage.value = 1
  loadDrugList()
}

function onLimitCategoryChange() {
  drugPage.value = 1
  loadDrugList()
}

function extractAffectedCount(result) {
  if (result === null || result === undefined) return null
  if (typeof result === 'number') return result
  if (typeof result === 'object') {
    const keys = ['updated', 'affected', 'affectedRows', 'count', 'rows', 'total', 'successCount', 'updateCount']
    for (const key of keys) {
      const value = result[key]
      if (value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))) {
        return Number(value)
      }
    }
  }
  return null
}

async function onBatchLimits() {
  const valid = await limitFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const stockWarn = Number(limitForm.stockWarn)
  const stockMax = limitForm.stockMax === null || limitForm.stockMax === undefined || limitForm.stockMax === ''
    ? null
    : Number(limitForm.stockMax)
  if (!Number.isFinite(stockWarn) || stockWarn < 0) {
    ElMessage.warning('库存下限不能小于 0')
    return
  }
  if (stockMax !== null && !Number.isFinite(stockMax)) {
    ElMessage.warning('库存上限格式不正确')
    return
  }
  if (stockMax !== null && stockMax < stockWarn) {
    ElMessage.warning('库存上限不能小于库存下限')
    return
  }
  limitSaving.value = true
  try {
    const result = await batchDrugLimits({
      category: limitForm.category,
      stockWarn,
      stockMax
    })
    const affected = extractAffectedCount(result)
    if (affected === null) {
      ElMessage.success('批量设置完成')
    } else {
      ElMessage.success(`批量设置完成，影响 ${affected} 个药品`)
    }
    drugPage.value = 1
    await loadDrugList()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    limitSaving.value = false
  }
}

/* ================= 生命周期 ================= */
watch(activeTab, (tab) => {
  if (tab === 'drug' && !drugLoaded.value) {
    loadDrugList()
  }
})

onMounted(() => {
  loadPaymentMethods()
  loadPrintSettings()
})
</script>

<style scoped>
.settings-page {
  min-height: calc(100vh - 88px);
  box-sizing: border-box;
}

.page-sub {
  margin: -4px 0 12px;
  line-height: 1.6;
}

.settings-tabs {
  margin-top: 4px;
}

.tab-body {
  padding: 4px 2px 8px;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-left: 8px;
  border-left: 3px solid #1f8a6d;
  line-height: 1.15;
}

.flex-spacer {
  flex: 1;
}

.method-name {
  font-weight: 600;
  margin-right: 6px;
}

.method-tag {
  margin-left: 2px;
}

.payment-table,
.drug-table {
  width: 100%;
}

.tip-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 12px;
  background: #f4f8ff;
  border-radius: 4px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}

.tip-line .el-icon {
  margin-top: 2px;
  color: #409eff;
  flex-shrink: 0;
}

.print-body {
  max-width: 780px;
}

.print-form {
  margin-top: 4px;
}

.form-tip {
  margin-left: 10px;
  line-height: 1.6;
}

.print-test {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 14px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  background: #fafafa;
}

.limit-form {
  margin-bottom: 4px;
}

.limit-select {
  width: 180px;
}

.limit-alert {
  margin: 4px 0 16px;
}

.list-head {
  margin-top: 8px;
}

.warn-value {
  color: #e6a23c;
  font-weight: 600;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
