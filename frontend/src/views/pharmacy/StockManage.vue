<template>
  <div class="stock-page">
    <!-- 顶部统计卡片 -->
    <el-row v-loading="alertsLoading" :gutter="12" class="stat-row">
      <el-col :span="8">
        <el-card shadow="never" class="stat-card stat-danger">
          <div class="stat-icon"><el-icon><Warning /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">低库存药品</div>
            <div class="stat-value">{{ alerts.lowStock.length }}</div>
            <div class="stat-sub">库存低于预警值</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card stat-warning">
          <div class="stat-icon"><el-icon><AlarmClock /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">临期批次</div>
            <div class="stat-value">{{ alerts.expiring.length }}</div>
            <div class="stat-sub">90 天内到期</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card stat-primary">
          <div class="stat-icon"><el-icon><Files /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">当前页药品数</div>
            <div class="stat-value">{{ list.length }}</div>
            <div class="stat-sub">共 {{ total }} 个药品</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 库存主表 -->
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-title">药品库存</div>
        <el-button :loading="loading" @click="refreshAll">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <span class="text-muted">入库、盘点操作会自动写入库存流水</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe class="stock-table" empty-text="暂无药品库存数据">
        <el-table-column prop="name" label="药品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" min-width="130" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="当前库存" width="120" align="right">
          <template #default="{ row }">
            <el-tag v-if="Number(row.stockQty || 0) === 0" size="small" type="danger" effect="dark">0 · 缺货</el-tag>
            <el-tag v-else-if="Number(row.stockQty) < Number(row.stockWarn || 0)" size="small" type="warning" effect="plain">
              {{ row.stockQty }} · 偏低
            </el-tag>
            <span v-else>{{ row.stockQty ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预警值" width="100" align="right">
          <template #default="{ row }">{{ row.stockWarn ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openStockIn(row)">入库</el-button>
            <el-button type="warning" link size="small" @click="openAdjust(row)">盘点</el-button>
            <el-button type="info" link size="small" @click="openStockDetail(row)">批次详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background
          @size-change="onSizeChange" @current-change="load" />
      </div>
    </div>

    <!-- 库存流水 -->
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-title">库存流水</div>
        <el-select v-model="ledgerDrugId" class="ledger-drug-select" filterable remote clearable
          placeholder="按药品筛选" :remote-method="searchLedgerDrugs" :loading="ledgerDrugSearchLoading"
          @change="onLedgerDrugChange" @visible-change="onLedgerSelectVisible">
          <el-option v-for="d in ledgerDrugOptions" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-button :loading="ledgerLoading" @click="loadLedger">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
      </div>

      <el-table v-loading="ledgerLoading" :data="ledgerList" border stripe class="ledger-table"
        empty-text="暂无库存流水">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(pickField(row, ['createdAt', 'createTime', 'time', 'operatedAt'])) }}</template>
        </el-table-column>
        <el-table-column label="药品" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ ledgerDrugName(row) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="ledgerTagType(row.type)">
              {{ ledgerTypeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ pickField(row, ['quantity', 'changeQty', 'changeQuantity', 'qty']) ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="变动前" width="90" align="right">
          <template #default="{ row }">{{ pickField(row, ['beforeQty', 'beforeQuantity', 'stockBefore', 'beforeStock']) ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="变动后" width="90" align="right">
          <template #default="{ row }">{{ pickField(row, ['afterQty', 'afterQuantity', 'stockAfter', 'afterStock']) ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ pickField(row, ['operatorName', 'operator', 'createdBy', 'userName']) || '-' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ pickField(row, ['remark', 'note']) || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination v-model:current-page="ledgerPage" v-model:page-size="ledgerSize" :total="ledgerTotal"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background
          @size-change="onLedgerSizeChange" @current-change="loadLedger" />
      </div>
    </div>

    <!-- 入库对话框 -->
    <el-dialog v-model="stockInVisible" :title="`入库 - ${stockInDrug?.name || ''}`" width="580px"
      :close-on-click-modal="false">
      <el-alert class="dialog-tip" type="info" :closable="false" show-icon
        :title="`当前库存：${stockInDrug?.stockQty ?? 0} ${stockInDrug?.unit || ''}，入库后库存将自动增加并写入流水。`" />
      <el-form ref="stockInFormRef" :model="stockInForm" :rules="stockInRules" label-width="90px">
        <el-form-item label="批号" prop="batchNo">
          <el-input v-model="stockInForm.batchNo" placeholder="如 20261101-A" maxlength="50" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="生产日期" prop="productionDate">
              <el-date-picker v-model="stockInForm.productionDate" type="date" value-format="YYYY-MM-DD"
                format="YYYY-MM-DD" placeholder="选择生产日期" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期" prop="expiryDate">
              <el-date-picker v-model="stockInForm.expiryDate" type="date" value-format="YYYY-MM-DD"
                format="YYYY-MM-DD" placeholder="选择有效期" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="stockInForm.quantity" :min="1" :precision="0" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进价" prop="costPrice">
              <el-input-number v-model="stockInForm.costPrice" :min="0" :precision="2" :step="0.5"
                class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="stockInForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
            placeholder="选填，如：供应商、采购单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockInVisible = false">取消</el-button>
        <el-button type="primary" :loading="stockInSubmitting" @click="submitStockIn">确认入库</el-button>
      </template>
    </el-dialog>

    <!-- 盘点对话框 -->
    <el-dialog v-model="adjustVisible" :title="`库存盘点 - ${adjustDrug?.name || ''}`" width="540px"
      :close-on-click-modal="false">
      <div v-loading="adjustLoading">
        <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="100px">
          <el-form-item label="选择批次" prop="batchNo">
            <el-select v-model="adjustForm.batchNo" class="full-width" placeholder="请选择要盘点的批次"
              :disabled="!adjustBatches.length" @change="onAdjustBatchChange">
              <el-option v-for="batch in adjustBatches" :key="batch.batchNo"
                :label="`${batch.batchNo}（现存 ${batch.quantity}）`" :value="batch.batchNo" />
            </el-select>
          </el-form-item>
          <el-form-item label="调整后数量" prop="quantity">
            <el-input-number v-model="adjustForm.quantity" :min="0" :precision="0" class="full-width" />
          </el-form-item>
          <el-form-item label="备注" prop="remark">
            <el-input v-model="adjustForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
              placeholder="选填，如：盘点差异说明" />
          </el-form-item>
        </el-form>
        <el-alert v-if="!adjustBatches.length && !adjustLoading" type="warning" :closable="false" show-icon
          title="该药品暂无批次库存，请先入库后再盘点。" />
      </div>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustSubmitting" :disabled="!adjustBatches.length"
          @click="submitAdjust">确认盘点</el-button>
      </template>
    </el-dialog>

    <!-- 批次详情抽屉 -->
    <el-drawer v-model="stockDrawerVisible" :title="`批次详情 - ${stockDetail?.drug?.name || ''}`" size="620px">
      <div v-loading="stockDetailLoading" class="drawer-body">
        <template v-if="stockDetail">
          <el-descriptions :column="2" border size="small" class="detail-desc">
            <el-descriptions-item label="药品">{{ stockDetail.drug?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ stockDetail.drug?.spec || '-' }}</el-descriptions-item>
            <el-descriptions-item label="总库存">
              <span class="money">{{ stockDetail.totalQty ?? 0 }}</span>
              {{ stockDetail.drug?.unit || '' }}
            </el-descriptions-item>
            <el-descriptions-item label="库存预警值">{{ stockDetail.drug?.stockWarn ?? 0 }}</el-descriptions-item>
          </el-descriptions>

          <el-table :data="stockDetail.batches || []" border size="small" class="batch-table"
            :row-class-name="batchRowClass" empty-text="暂无批次库存">
            <el-table-column prop="batchNo" label="批号" min-width="130" show-overflow-tooltip />
            <el-table-column label="生产日期" width="110">
              <template #default="{ row }">{{ formatDate(row.productionDate) }}</template>
            </el-table-column>
            <el-table-column label="有效期" width="110">
              <template #default="{ row }">{{ formatDate(row.expiryDate) }}</template>
            </el-table-column>
            <el-table-column label="数量" width="80" align="right">
              <template #default="{ row }">{{ row.quantity ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="有效期状态" width="120">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="expiryState(row.expiryDate).type">
                  {{ expiryState(row.expiryDate).text }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div class="batch-legend text-muted">黄色行为 90 天内临期批次，请优先出库。</div>
        </template>
        <el-empty v-else description="暂无批次信息" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDrugStock, pageDrugs, pageLedger, stockAdjust, stockAlerts, stockIn } from '@/api'
import { formatDate, formatDateTime } from '@/utils/format'

/* ---------------- 顶部统计 ---------------- */
const alerts = ref({ lowStock: [], expiring: [] })
const alertsLoading = ref(false)

/* ---------------- 库存主表 ---------------- */
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref([])
const loading = ref(false)

/* ---------------- 入库 ---------------- */
const stockInVisible = ref(false)
const stockInDrug = ref(null)
const stockInSubmitting = ref(false)
const stockInFormRef = ref(null)
const stockInForm = reactive({
  batchNo: '',
  productionDate: '',
  expiryDate: '',
  quantity: 1,
  costPrice: 0,
  remark: ''
})

const stockInRules = {
  batchNo: [{ required: true, message: '请输入批号', trigger: 'blur' }],
  productionDate: [{ required: true, message: '请选择生产日期', trigger: 'change' }],
  expiryDate: [
    { required: true, message: '请选择有效期', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (!value) return callback()
        if (stockInForm.productionDate && value <= stockInForm.productionDate) {
          return callback(new Error('有效期必须晚于生产日期'))
        }
        callback()
      },
      trigger: 'change'
    }
  ],
  quantity: [{ required: true, type: 'number', message: '请输入入库数量', trigger: 'blur' }],
  costPrice: [{ type: 'number', min: 0, message: '进价不能小于 0', trigger: 'blur' }]
}

/* ---------------- 盘点 ---------------- */
const adjustVisible = ref(false)
const adjustDrug = ref(null)
const adjustLoading = ref(false)
const adjustSubmitting = ref(false)
const adjustBatches = ref([])
const adjustFormRef = ref(null)
const adjustForm = reactive({ batchNo: '', quantity: 0, remark: '' })

const adjustRules = {
  batchNo: [{ required: true, message: '请选择批次', trigger: 'change' }],
  quantity: [{ required: true, type: 'number', message: '请输入调整后数量', trigger: 'blur' }]
}

/* ---------------- 批次详情 ---------------- */
const stockDrawerVisible = ref(false)
const stockDetailLoading = ref(false)
const stockDetail = ref(null)

/* ---------------- 库存流水 ---------------- */
const ledgerLoading = ref(false)
const ledgerList = ref([])
const ledgerPage = ref(1)
const ledgerSize = ref(10)
const ledgerTotal = ref(0)
const ledgerDrugId = ref(null)
const ledgerDrugOptions = ref([])
const ledgerDrugSearchLoading = ref(false)

const LEDGER_TYPE_TEXT = {
  IN: '入库',
  STOCK_IN: '入库',
  INBOUND: '入库',
  PURCHASE_IN: '入库',
  OUT: '出库',
  STOCK_OUT: '出库',
  OUTBOUND: '出库',
  SALE_OUT: '出库',
  ADJUST: '盘点',
  ADJUSTMENT: '盘点',
  CHECK: '盘点',
  STOCK_CHECK: '盘点',
  STOCKTAKE: '盘点',
  RETURN: '退货',
  RETURN_IN: '退货',
  SALES_RETURN: '退货'
}

const LEDGER_TYPE_TAG = {
  IN: 'success',
  STOCK_IN: 'success',
  INBOUND: 'success',
  PURCHASE_IN: 'success',
  OUT: 'warning',
  STOCK_OUT: 'warning',
  OUTBOUND: 'warning',
  SALE_OUT: 'warning',
  ADJUST: 'primary',
  ADJUSTMENT: 'primary',
  CHECK: 'primary',
  STOCK_CHECK: 'primary',
  STOCKTAKE: 'primary',
  RETURN: 'info',
  RETURN_IN: 'info',
  SALES_RETURN: 'info'
}

function pickField(row, keys) {
  for (const key of keys) {
    const value = row?.[key]
    if (value !== undefined && value !== null && value !== '') return value
  }
  return null
}

function ledgerTypeText(type) {
  if (!type) return '-'
  return LEDGER_TYPE_TEXT[type] || String(type)
}

function ledgerTagType(type) {
  const key = String(type || '')
  if (LEDGER_TYPE_TAG[key]) return LEDGER_TYPE_TAG[key]
  if (key.includes('入')) return 'success'
  if (key.includes('出')) return 'warning'
  if (key.includes('盘')) return 'primary'
  if (key.includes('退')) return 'info'
  return 'info'
}

function ledgerDrugName(row) {
  const direct = pickField(row, ['drugName', 'name'])
  if (direct) return direct
  const drugId = row?.drugId
  const fromList = list.value.find((d) => d.id === drugId)
  if (fromList) return fromList.name
  const fromOptions = ledgerDrugOptions.value.find((d) => d.id === drugId)
  return fromOptions ? fromOptions.name : '-'
}

function daysUntil(dateStr) {
  if (!dateStr) return null
  const target = new Date(`${String(dateStr).slice(0, 10)}T00:00:00`)
  if (Number.isNaN(target.getTime())) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.ceil((target.getTime() - today.getTime()) / 86400000)
}

function expiryState(dateStr) {
  const days = daysUntil(dateStr)
  if (days === null) return { text: '-', type: 'info' }
  if (days < 0) return { text: '已过期', type: 'danger' }
  if (days <= 90) return { text: `临期 ${days} 天`, type: 'warning' }
  return { text: '正常', type: 'success' }
}

function batchRowClass({ row }) {
  const days = daysUntil(row.expiryDate)
  if (days !== null && days < 0) return 'expired-row'
  if (days !== null && days <= 90) return 'expiring-row'
  return ''
}

async function loadAlerts() {
  alertsLoading.value = true
  try {
    const data = await stockAlerts()
    alerts.value = {
      lowStock: data?.lowStock || [],
      expiring: data?.expiring || []
    }
  } catch {
    alerts.value = { lowStock: [], expiring: [] }
  } finally {
    alertsLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pageDrugs({ page: page.value, size: size.value })
    list.value = data?.list || []
    total.value = Number(data?.total ?? 0)
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSizeChange() {
  page.value = 1
  load()
}

function refreshAll() {
  return Promise.all([loadAlerts(), load()])
}

async function loadLedger() {
  ledgerLoading.value = true
  try {
    const data = await pageLedger({
      drugId: ledgerDrugId.value || undefined,
      page: ledgerPage.value,
      size: ledgerSize.value
    })
    ledgerList.value = data?.list || []
    ledgerTotal.value = Number(data?.total ?? 0)
  } catch {
    ledgerList.value = []
    ledgerTotal.value = 0
  } finally {
    ledgerLoading.value = false
  }
}

async function searchLedgerDrugs(keyword) {
  ledgerDrugSearchLoading.value = true
  try {
    const data = await pageDrugs({ keyword: (keyword || '').trim() || undefined, page: 1, size: 20 })
    ledgerDrugOptions.value = data?.list || []
  } catch {
    ledgerDrugOptions.value = []
  } finally {
    ledgerDrugSearchLoading.value = false
  }
}

function onLedgerSelectVisible(visible) {
  if (visible && !ledgerDrugOptions.value.length) searchLedgerDrugs('')
}

function onLedgerDrugChange() {
  ledgerPage.value = 1
  loadLedger()
}

function onLedgerSizeChange() {
  ledgerPage.value = 1
  loadLedger()
}

/* ---------------- 入库操作 ---------------- */
function openStockIn(row) {
  stockInDrug.value = row
  Object.assign(stockInForm, {
    batchNo: '',
    productionDate: '',
    expiryDate: '',
    quantity: 1,
    costPrice: Number(row.costPrice ?? 0),
    remark: ''
  })
  stockInVisible.value = true
  stockInFormRef.value?.clearValidate()
}

async function submitStockIn() {
  const valid = await stockInFormRef.value?.validate().catch(() => false)
  if (!valid || !stockInDrug.value) return
  stockInSubmitting.value = true
  try {
    await stockIn(stockInDrug.value.id, {
      batchNo: stockInForm.batchNo.trim(),
      productionDate: stockInForm.productionDate || null,
      expiryDate: stockInForm.expiryDate,
      quantity: Number(stockInForm.quantity),
      costPrice: Number(stockInForm.costPrice ?? 0),
      remark: stockInForm.remark.trim() || null
    })
    ElMessage.success('入库成功')
    stockInVisible.value = false
    await Promise.all([load(), loadAlerts(), loadLedger()])
    if (stockDrawerVisible.value && stockDetail.value?.drug?.id === stockInDrug.value.id) {
      await loadStockDetail(stockInDrug.value.id)
    }
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    stockInSubmitting.value = false
  }
}

/* ---------------- 盘点操作 ---------------- */
async function openAdjust(row) {
  adjustDrug.value = row
  adjustBatches.value = []
  Object.assign(adjustForm, { batchNo: '', quantity: 0, remark: '' })
  adjustVisible.value = true
  adjustLoading.value = true
  try {
    const data = await getDrugStock(row.id)
    adjustBatches.value = data?.batches || []
    if (adjustBatches.value.length === 1) {
      adjustForm.batchNo = adjustBatches.value[0].batchNo
      adjustForm.quantity = Number(adjustBatches.value[0].quantity ?? 0)
    }
  } catch {
    adjustBatches.value = []
  } finally {
    adjustLoading.value = false
  }
  adjustFormRef.value?.clearValidate()
}

function onAdjustBatchChange(batchNo) {
  const batch = adjustBatches.value.find((b) => b.batchNo === batchNo)
  adjustForm.quantity = Number(batch?.quantity ?? 0)
}

async function submitAdjust() {
  const valid = await adjustFormRef.value?.validate().catch(() => false)
  if (!valid || !adjustDrug.value) return
  adjustSubmitting.value = true
  try {
    await stockAdjust(adjustDrug.value.id, {
      batchNo: adjustForm.batchNo,
      quantity: Number(adjustForm.quantity),
      remark: adjustForm.remark.trim() || null
    })
    ElMessage.success('盘点完成')
    adjustVisible.value = false
    await Promise.all([load(), loadAlerts(), loadLedger()])
    if (stockDrawerVisible.value && stockDetail.value?.drug?.id === adjustDrug.value.id) {
      await loadStockDetail(adjustDrug.value.id)
    }
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    adjustSubmitting.value = false
  }
}

/* ---------------- 批次详情 ---------------- */
async function loadStockDetail(drugId) {
  stockDetailLoading.value = true
  try {
    stockDetail.value = await getDrugStock(drugId)
  } catch {
    stockDetail.value = null
  } finally {
    stockDetailLoading.value = false
  }
}

async function openStockDetail(row) {
  stockDrawerVisible.value = true
  stockDetail.value = null
  await loadStockDetail(row.id)
}

onMounted(() => {
  refreshAll()
  loadLedger()
})
</script>

<style scoped>
.stock-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-row {
  flex-shrink: 0;
}

.stat-card {
  border: 1px solid #ebeef5;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-danger .stat-icon {
  background: #fef0f0;
  color: #f56c6c;
}

.stat-warning .stat-icon {
  background: #fdf6ec;
  color: #e6a23c;
}

.stat-primary .stat-icon {
  background: #ecf5ff;
  color: #409eff;
}

.stat-body {
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: #606266;
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-sub {
  font-size: 12px;
  color: #909399;
}

.page-title {
  margin: 0;
  flex: 0 0 auto;
}

.stock-table,
.ledger-table {
  width: 100%;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.ledger-drug-select {
  width: 220px;
}

.dialog-tip {
  margin-bottom: 14px;
}

.full-width {
  width: 100%;
}

.drawer-body {
  min-height: 200px;
}

.detail-desc {
  margin-bottom: 14px;
}

.batch-table {
  width: 100%;
}

.batch-legend {
  padding-top: 8px;
}

:deep(.expiring-row td) {
  background: #fdf6ec !important;
}

:deep(.expired-row td) {
  background: #fef0f0 !important;
}
</style>
