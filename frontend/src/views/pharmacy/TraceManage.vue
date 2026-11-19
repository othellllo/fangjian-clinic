<template>
  <div class="trace-page">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-title">药品追溯码</div>
        <el-tag type="info" effect="plain">中药饮片、院内制剂暂不强制采集</el-tag>
        <span class="text-muted toolbar-tip">政策依据：国家医保局办公室 2024-09-30 公告</span>
      </div>

      <el-tabs v-model="activeTab" class="trace-tabs" @tab-change="onTabChange">
        <!-- ============ 1. 入库绑定 ============ -->
        <el-tab-pane label="入库绑定" name="bind">
          <div class="bind-layout">
            <el-alert class="policy-alert" type="info" :closable="false" show-icon
              title="入库时把追溯码与药品、批次绑定。扫码枪扫描后回车即可连续录入，重复码会被拦截，提交后展示服务端返回的成功 / 失败明细。" />

            <el-form :model="bindForm" label-width="88px" class="bind-form" @submit.prevent>
              <el-form-item label="药品" required>
                <el-select v-model="bindForm.drugId" class="full-width" filterable remote clearable
                  placeholder="搜索药品名称 / 拼音码（仅显示需追溯药品）" :remote-method="searchBindDrugs"
                  :loading="bindDrugLoading" @change="onBindDrugChange" @visible-change="onBindDrugVisible">
                  <el-option v-for="drug in bindDrugOptions" :key="drug.id" :label="drugOptionLabel(drug)"
                    :value="drug.id" />
                </el-select>
                <div v-if="bindHerbHint && !bindForm.drugId" class="form-hint herb-hint">{{ bindHerbHint }}</div>
                <div v-else-if="bindDrugLoaded && !bindDrugOptions.length" class="form-hint text-muted">
                  没有匹配的需追溯药品；中药饮片按政策无需采集追溯码。
                </div>
              </el-form-item>

              <el-form-item label="批号" required>
                <el-select v-model="bindForm.batchNo" class="full-width" filterable allow-create
                  default-first-option clearable :loading="batchLoading" :disabled="!bindForm.drugId"
                  placeholder="选择已有批次，或直接输入新批号后回车">
                  <el-option v-for="batch in bindBatches" :key="batch.batchNo" :label="batchOptionLabel(batch)"
                    :value="batch.batchNo" />
                </el-select>
                <div v-if="bindForm.drugId && !batchLoading && !bindBatches.length" class="form-hint text-muted">
                  该药品暂无入库批次，可直接输入批号（如 20261101-A）。
                </div>
              </el-form-item>

              <el-form-item label="扫码录入">
                <div class="scan-box">
                  <el-input ref="scanInputRef" v-model="scanCode" clearable
                    placeholder="使用扫码枪扫描 20 位追溯码后回车，可连续录入" @keyup.enter="addBindCode">
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                    <template #append>
                      <el-button @click="addBindCode">加入列表</el-button>
                    </template>
                  </el-input>
                  <div class="scan-actions">
                    <span class="text-muted">本次已录入 {{ bindCodes.length }} 个追溯码</span>
                    <el-button v-if="bindCodes.length" link type="danger" size="small" @click="clearBindCodes">
                      清空列表
                    </el-button>
                  </div>
                </div>
              </el-form-item>

              <el-form-item label="提交绑定">
                <div class="submit-row">
                  <el-button type="primary" :loading="bindSubmitting" :disabled="!canSubmitBind" @click="submitBind">
                    提交绑定{{ bindCodes.length ? `（${bindCodes.length} 个）` : '' }}
                  </el-button>
                  <span class="text-muted">重复码、格式错误的码由服务端返回失败明细</span>
                </div>
              </el-form-item>
            </el-form>

            <el-table :data="bindCodes" border size="small" class="code-table" empty-text="暂无扫描记录">
              <el-table-column type="index" label="#" width="52" align="center" />
              <el-table-column prop="code" label="追溯码" min-width="200" />
              <el-table-column label="状态" width="90" align="center">
                <template #default>
                  <el-tag size="small" type="info" effect="plain">待提交</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="72" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="removeBindCode($index)">删除</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty :image-size="50" description="暂无扫描记录，请扫码录入" />
              </template>
            </el-table>

            <el-card v-if="bindResult" class="bind-result" shadow="never">
              <div class="result-head">
                <span class="result-title">绑定结果</span>
                <el-tag size="small" type="success" effect="plain">成功 {{ bindResult.success }}</el-tag>
                <el-tag size="small" :type="bindResult.failed ? 'danger' : 'info'" effect="plain">
                  失败 {{ bindResult.failed }}
                </el-tag>
              </div>
              <el-table v-if="bindResult.errors.length" :data="bindResult.errors" border size="small"
                class="error-table" empty-text="无失败明细">
                <el-table-column type="index" label="#" width="52" align="center" />
                <el-table-column prop="message" label="失败原因" min-width="240" show-overflow-tooltip />
              </el-table>
              <div v-else class="text-muted">全部追溯码绑定成功。</div>
              <div class="text-muted result-tip">失败明细通常为重复码（已被绑定）或追溯码格式错误；未成功的码可修正后重新提交。</div>
            </el-card>
          </div>
        </el-tab-pane>

        <!-- ============ 2. 追溯台账 ============ -->
        <el-tab-pane label="追溯台账" name="ledger">
          <div class="page-toolbar ledger-toolbar">
            <el-select v-model="filters.drugId" class="filter-drug" filterable remote clearable
              placeholder="按药品筛选" :remote-method="searchFilterDrugs" :loading="filterDrugLoading"
              @change="applyFilters" @visible-change="onFilterDrugVisible">
              <el-option v-for="drug in filterDrugOptions" :key="drug.id" :label="drugOptionLabel(drug)"
                :value="drug.id" />
            </el-select>
            <el-input v-model="filters.batchNo" class="filter-batch" clearable placeholder="批号"
              @keyup.enter="applyFilters" />
            <el-select v-model="filters.status" class="filter-status" placeholder="全部状态" @change="applyFilters">
              <el-option label="全部状态" value="" />
              <el-option label="在库" value="IN_STOCK" />
              <el-option label="已发药" value="DISPENSED" />
              <el-option label="已退药" value="RETURNED" />
              <el-option label="作废" value="VOID" />
            </el-select>
            <el-input v-model="filters.keyword" class="filter-keyword" clearable
              placeholder="关键字：追溯码 / 患者 / 药品" @keyup.enter="applyFilters">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" :loading="traceLoading" @click="applyFilters">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
            <el-button :loading="traceLoading" @click="loadTraces">
              <el-icon><Refresh /></el-icon>
              <span>刷新</span>
            </el-button>
          </div>

          <div class="ledger-actions">
            <el-button type="primary" plain :loading="uploading" :disabled="!selectedTraces.length"
              @click="onUploadTraces">
              <el-icon><Upload /></el-icon>
              <span>上传医保平台{{ selectedTraces.length ? `（${selectedTraces.length}）` : '' }}</span>
            </el-button>
            <el-button type="warning" plain :loading="returning" :disabled="!selectedTraces.length"
              @click="onReturnTraces">
              <el-icon><RefreshLeft /></el-icon>
              <span>退药回退{{ selectedTraces.length ? `（${selectedTraces.length}）` : '' }}</span>
            </el-button>
            <span class="text-muted ledger-count">共 {{ traceTotal }} 条追溯码</span>
          </div>

          <el-table ref="traceTableRef" v-loading="traceLoading" :data="traceList" row-key="id" border stripe
            class="trace-table" empty-text="暂无追溯码记录" @selection-change="onSelectionChange">
            <el-table-column type="selection" width="46" align="center" />
            <el-table-column prop="code" label="追溯码" min-width="190" show-overflow-tooltip />
            <el-table-column label="药品" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.drugName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="batchNo" label="批号" min-width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="94" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="statusTagType(row.status)">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="绑定时间" width="164">
              <template #default="{ row }">{{ formatDateTime(row.bindTime) }}</template>
            </el-table-column>
            <el-table-column label="患者" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.patientName || '-' }}</template>
            </el-table-column>
            <el-table-column label="处方号" width="96">
              <template #default="{ row }">{{ row.prescriptionId ? `#${row.prescriptionId}` : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作人" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.operatorName || '-' }}</template>
            </el-table-column>
            <el-table-column label="上传状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="uploadTagType(row.uploadStatus)">
                  {{ uploadText(row.uploadStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="70" description="暂无追溯码记录" />
            </template>
          </el-table>

          <div class="pager">
            <el-pagination v-model:current-page="tracePage" v-model:page-size="traceSize" :total="traceTotal"
              :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
              @size-change="onTraceSizeChange" @current-change="loadTraces" />
          </div>
        </el-tab-pane>

        <!-- ============ 3. 按码反查 ============ -->
        <el-tab-pane label="按码反查" name="query">
          <div class="query-box">
            <el-input v-model="queryCode" class="query-input" clearable maxlength="20" show-word-limit
              placeholder="输入 20 位药品追溯码，回车查询" @keyup.enter="queryTrace">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" :loading="queryLoading" @click="queryTrace">查询</el-button>
          </div>

          <div v-loading="queryLoading" class="query-result">
            <template v-if="queryResult">
              <el-descriptions title="追溯码全链路" :column="2" border class="query-desc">
                <el-descriptions-item label="追溯码" :span="2">{{ queryResult.code || '-' }}</el-descriptions-item>
                <el-descriptions-item label="药品">{{ queryResult.drugName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="批号">{{ queryResult.batchNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag size="small" effect="plain" :type="statusTagType(queryResult.status)">
                    {{ statusText(queryResult.status) }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="上传状态">
                  <el-tag size="small" effect="plain" :type="uploadTagType(queryResult.uploadStatus)">
                    {{ uploadText(queryResult.uploadStatus) }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="入库时间">{{ formatDateTime(queryResult.bindTime) }}</el-descriptions-item>
                <el-descriptions-item label="发药时间">
                  {{ queryResult.dispenseTime ? formatDateTime(queryResult.dispenseTime) : '未发药' }}
                </el-descriptions-item>
                <el-descriptions-item label="患者">{{ queryResult.patientName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="处方">
                  {{ queryResult.prescriptionId ? `#${queryResult.prescriptionId}` : '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="操作人">{{ queryResult.operatorName || '-' }}</el-descriptions-item>
              </el-descriptions>

              <el-timeline class="query-timeline">
                <el-timeline-item :timestamp="formatDateTime(queryResult.bindTime)" placement="top" type="primary">
                  入库绑定：{{ queryResult.drugName || '-' }} / 批号 {{ queryResult.batchNo || '-' }}
                </el-timeline-item>
                <el-timeline-item v-if="queryResult.dispenseTime" :timestamp="formatDateTime(queryResult.dispenseTime)"
                  placement="top" type="success">
                  发药：{{ queryResult.patientName || '-' }}
                  {{ queryResult.prescriptionId ? `（处方 #${queryResult.prescriptionId}）` : '' }}
                </el-timeline-item>
                <el-timeline-item v-if="queryResult.status === 'RETURNED'" timestamp="退药" placement="top" type="info">
                  已退药：追溯码状态回退为「在库」
                </el-timeline-item>
              </el-timeline>
            </template>
            <el-empty v-else-if="querySearched" :image-size="90"
              description="未查询到该追溯码，请核对 20 位码后重试" />
            <div v-else class="empty-hint text-muted">
              输入追溯码后可查看药品、批次、入库时间、发药时间、患者、处方与操作人。
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshLeft, Search, Upload } from '@element-plus/icons-vue'
import {
  bindTraces,
  getDrugStock,
  getTraceByCode,
  pageDrugs,
  pageTraces,
  returnTraces,
  uploadTraces
} from '@/api'
import { DRUG_CATEGORY_TEXT, formatDateTime } from '@/utils/format'

const TRACE_STATUS_TEXT = {
  IN_STOCK: '在库',
  DISPENSED: '已发药',
  RETURNED: '已退药',
  VOID: '作废'
}

const TRACE_STATUS_TAG = {
  IN_STOCK: 'primary',
  DISPENSED: 'success',
  RETURNED: 'info',
  VOID: 'danger'
}

const UPLOAD_STATUS_TEXT = {
  PENDING: '待上传',
  UPLOADED: '已上传',
  UNSUPPORTED: '平台未接入',
  FAILED: '上传失败'
}

const UPLOAD_STATUS_TAG = {
  PENDING: 'warning',
  UPLOADED: 'success',
  UNSUPPORTED: 'info',
  FAILED: 'danger'
}

function statusText(status) {
  return TRACE_STATUS_TEXT[status] || status || '-'
}

function statusTagType(status) {
  return TRACE_STATUS_TAG[status] || 'info'
}

function uploadText(status) {
  if (!status) return '待上传'
  return UPLOAD_STATUS_TEXT[status] || status
}

function uploadTagType(status) {
  if (!status) return 'warning'
  return UPLOAD_STATUS_TAG[status] || 'info'
}

/* ---------------- 药品字典辅助 ---------------- */
/* needTrace 由后端 DrugDto 返回；历史数据缺失时按分类兜底（饮片不采集） */
function drugNeedsTrace(drug) {
  if (!drug) return false
  if (drug.needTrace !== undefined && drug.needTrace !== null && drug.needTrace !== '') {
    return Number(drug.needTrace) === 1
  }
  if (drug.category) return drug.category !== 'HERB'
  return false
}

function drugOptionLabel(drug) {
  if (!drug) return ''
  const parts = [drug.name, drug.spec, drug.unit].filter(Boolean)
  const category = DRUG_CATEGORY_TEXT[drug.category] || drug.category || ''
  return `${parts.join(' / ')}${category ? ` · ${category}` : ''}`
}

/* ---------------- 页签 ---------------- */
const activeTab = ref('bind')
const ledgerLoaded = ref(false)

function onTabChange(name) {
  if (name === 'ledger' && !ledgerLoaded.value) {
    loadTraces()
  }
}

/* ---------------- 入库绑定 ---------------- */
const scanInputRef = ref(null)
const bindForm = reactive({ drugId: null, batchNo: '' })
const bindDrugOptions = ref([])
const bindDrugLoading = ref(false)
const bindDrugLoaded = ref(false)
const bindHerbHint = ref('')
const bindBatches = ref([])
const batchLoading = ref(false)
const scanCode = ref('')
const bindCodes = ref([])
const bindSubmitting = ref(false)
const bindResult = ref(null)

const canSubmitBind = computed(
  () => !!(bindForm.drugId && String(bindForm.batchNo || '').trim() && bindCodes.value.length)
)

async function searchBindDrugs(keyword) {
  bindDrugLoading.value = true
  try {
    const data = await pageDrugs({ keyword: (keyword || '').trim() || undefined, page: 1, size: 20 })
    const all = data?.list || []
    bindDrugOptions.value = all.filter((drug) => drugNeedsTrace(drug))
    const skipped = all.find((drug) => !drugNeedsTrace(drug))
    bindHerbHint.value = skipped
      ? `「${skipped.name}」${skipped.category === 'HERB' ? '为中药饮片' : '暂未标记需要追溯'}，按医保政策暂无需采集追溯码。`
      : ''
  } catch {
    bindDrugOptions.value = []
  } finally {
    bindDrugLoading.value = false
    bindDrugLoaded.value = true
  }
}

function onBindDrugVisible(visible) {
  if (visible && !bindDrugOptions.value.length) searchBindDrugs('')
}

async function loadBatches(drugId) {
  batchLoading.value = true
  try {
    const data = await getDrugStock(drugId)
    bindBatches.value = Array.isArray(data?.batches) ? data.batches : []
  } catch {
    bindBatches.value = []
  } finally {
    batchLoading.value = false
  }
}

function onBindDrugChange(drugId) {
  bindForm.batchNo = ''
  bindBatches.value = []
  bindCodes.value = []
  bindResult.value = null
  if (drugId) loadBatches(drugId)
}

function batchOptionLabel(batch) {
  return `${batch.batchNo}（现存 ${batch.quantity ?? 0}）`
}

function focusScanInput() {
  nextTick(() => scanInputRef.value?.focus())
}

function addBindCode() {
  const raw = scanCode.value.trim()
  if (!raw) return
  // 与后端一致：20 位字母或数字，统一转大写
  if (!/^[A-Za-z0-9]{20}$/.test(raw)) {
    ElMessage.warning(`追溯码应为 20 位字母或数字，当前 ${raw.length} 位`)
    scanCode.value = ''
    focusScanInput()
    return
  }
  const code = raw.toUpperCase()
  if (bindCodes.value.includes(code)) {
    ElMessage.warning('该追溯码已在本次录入列表中，请勿重复扫码')
    scanCode.value = ''
    focusScanInput()
    return
  }
  bindCodes.value.push(code)
  scanCode.value = ''
  focusScanInput()
}

function removeBindCode(index) {
  bindCodes.value.splice(index, 1)
}

function clearBindCodes() {
  bindCodes.value = []
  bindResult.value = null
}

async function submitBind() {
  if (!bindForm.drugId) {
    ElMessage.warning('请先选择药品')
    return
  }
  const batchNo = String(bindForm.batchNo || '').trim()
  if (!batchNo) {
    ElMessage.warning('请选择或输入批号')
    return
  }
  if (!bindCodes.value.length) {
    ElMessage.warning('请至少扫描一个追溯码')
    return
  }
  bindSubmitting.value = true
  bindResult.value = null
  try {
    const data = await bindTraces({ drugId: bindForm.drugId, batchNo, codes: [...bindCodes.value] })
    const success = Number(data?.success ?? 0)
    const failed = Number(data?.failed ?? 0)
    const rawErrors = Array.isArray(data?.errors) ? data.errors : []
    bindResult.value = {
      success,
      failed,
      errors: rawErrors.map((message, index) => ({ index: index + 1, message: String(message) }))
    }
    if (failed === 0) {
      ElMessage.success(`绑定成功，共 ${success} 个追溯码`)
      bindCodes.value = []
    } else if (success > 0) {
      ElMessage.warning(`成功 ${success} 个，失败 ${failed} 个，失败明细见下方结果`)
    } else {
      ElMessage.error(`绑定失败，共 ${failed} 个追溯码未成功，请查看失败明细`)
    }
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    bindSubmitting.value = false
  }
}

/* ---------------- 追溯台账 ---------------- */
const traceTableRef = ref(null)
const filters = reactive({ drugId: null, batchNo: '', status: '', keyword: '' })
const filterDrugOptions = ref([])
const filterDrugLoading = ref(false)
const tracePage = ref(1)
const traceSize = ref(10)
const traceTotal = ref(0)
const traceList = ref([])
const traceLoading = ref(false)
const selectedTraces = ref([])
const uploading = ref(false)
const returning = ref(false)

async function searchFilterDrugs(keyword) {
  filterDrugLoading.value = true
  try {
    const data = await pageDrugs({ keyword: (keyword || '').trim() || undefined, page: 1, size: 20 })
    filterDrugOptions.value = data?.list || []
  } catch {
    filterDrugOptions.value = []
  } finally {
    filterDrugLoading.value = false
  }
}

function onFilterDrugVisible(visible) {
  if (visible && !filterDrugOptions.value.length) searchFilterDrugs('')
}

async function loadTraces() {
  traceLoading.value = true
  try {
    const data = await pageTraces({
      drugId: filters.drugId || undefined,
      batchNo: filters.batchNo.trim() || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      page: tracePage.value,
      size: traceSize.value
    })
    traceList.value = data?.list || []
    traceTotal.value = data?.total || 0
    ledgerLoaded.value = true
  } catch {
    traceList.value = []
    traceTotal.value = 0
  } finally {
    traceLoading.value = false
    selectedTraces.value = []
    nextTick(() => traceTableRef.value?.clearSelection())
  }
}

function applyFilters() {
  tracePage.value = 1
  loadTraces()
}

function resetFilters() {
  filters.drugId = null
  filters.batchNo = ''
  filters.status = ''
  filters.keyword = ''
  tracePage.value = 1
  loadTraces()
}

function onTraceSizeChange() {
  tracePage.value = 1
  loadTraces()
}

function onSelectionChange(rows) {
  selectedTraces.value = rows
}

function selectedIds() {
  return selectedTraces.value.map((row) => row.id).filter((id) => id != null)
}

async function onUploadTraces() {
  const ids = selectedIds()
  if (!ids.length) {
    ElMessage.warning('请先勾选要上传的追溯码')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将选中的 ${ids.length} 条追溯码上传至医保平台？未接入平台时数据会保留在本地待补传。`,
      '上传确认',
      { type: 'info', confirmButtonText: '确认上传', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  uploading.value = true
  try {
    const data = await uploadTraces(ids)
    const status = data?.status || data?.uploadStatus || (typeof data === 'string' ? data : '')
    const message = data?.message || data?.msg || ''
    if (status === 'UNSUPPORTED') {
      ElMessage.warning(message || '未接入医保上传接口，数据已保存在本地，可导出后补传')
    } else {
      ElMessage.success(message || `已提交 ${ids.length} 条追溯码上传`)
    }
    await loadTraces()
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    uploading.value = false
  }
}

async function onReturnTraces() {
  const dispensed = selectedTraces.value.filter((row) => row.status === 'DISPENSED')
  if (!dispensed.length) {
    ElMessage.warning('所选追溯码中没有「已发药」记录，无需退药回退')
    return
  }
  const skipped = selectedTraces.value.length - dispensed.length
  try {
    await ElMessageBox.confirm(
      `确认将 ${dispensed.length} 条已发药追溯码退药回退？回退后状态变为「在库」。${skipped ? `另有 ${skipped} 条非已发药记录将被忽略。` : ''}`,
      '退药确认',
      { type: 'warning', confirmButtonText: '确认回退', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  returning.value = true
  try {
    const data = await returnTraces(dispensed.map((row) => row.id))
    const changed = Number(data?.changed ?? dispensed.length)
    ElMessage.success(`退药回退成功，共 ${changed} 条追溯码状态已变为「在库」`)
    await loadTraces()
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    returning.value = false
  }
}

/* ---------------- 按码反查 ---------------- */
const queryCode = ref('')
const queryLoading = ref(false)
const querySearched = ref(false)
const queryResult = ref(null)

async function queryTrace() {
  const raw = queryCode.value.trim()
  if (!raw) {
    ElMessage.warning('请输入追溯码')
    return
  }
  if (!/^[A-Za-z0-9]{20}$/.test(raw)) {
    ElMessage.warning('请输入 20 位字母或数字追溯码')
    return
  }
  const code = raw.toUpperCase()
  queryLoading.value = true
  querySearched.value = true
  queryResult.value = null
  try {
    const data = await getTraceByCode(code)
    queryResult.value = data || null
  } catch {
    queryResult.value = null
  } finally {
    queryLoading.value = false
  }
}

onMounted(() => {
  searchBindDrugs('')
})
</script>

<style scoped>
.trace-page {
  display: flex;
  flex-direction: column;
}

.toolbar-tip {
  margin-left: auto;
}

.trace-tabs {
  margin-top: 4px;
}

/* ---------------- 入库绑定 ---------------- */
.bind-layout {
  max-width: 920px;
}

.policy-alert {
  margin-bottom: 14px;
}

.bind-form {
  margin-top: 4px;
}

.full-width {
  width: 100%;
}

.form-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
}

.herb-hint {
  color: #b88230;
}

.scan-box {
  width: 100%;
}

.scan-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}

.submit-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.code-table {
  width: 100%;
  margin-top: 4px;
}

.bind-result {
  margin-top: 14px;
  border-radius: 6px;
}

.result-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.result-title {
  font-size: 14px;
  font-weight: 600;
}

.error-table {
  width: 100%;
}

.result-tip {
  margin-top: 10px;
}

/* ---------------- 追溯台账 ---------------- */
.ledger-toolbar {
  margin-bottom: 10px;
}

.filter-drug {
  width: 220px;
}

.filter-batch {
  width: 150px;
}

.filter-status {
  width: 130px;
}

.filter-keyword {
  width: 220px;
}

.ledger-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.ledger-count {
  margin-left: auto;
}

.trace-table {
  width: 100%;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* ---------------- 按码反查 ---------------- */
.query-box {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 620px;
  margin-bottom: 16px;
}

.query-input {
  max-width: 420px;
}

.query-result {
  min-height: 180px;
}

.query-desc {
  max-width: 860px;
}

.query-timeline {
  max-width: 720px;
  margin-top: 18px;
}
</style>
