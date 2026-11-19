<template>
  <div class="dispense-page">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-title">待发药处方</div>
        <el-input v-model="keyword" class="search-input" clearable placeholder="搜索患者姓名 / 处方号">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button :loading="loading" @click="loadList">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <el-button type="warning" plain @click="openScanOutDialog">
          <el-icon><Box /></el-icon>
          <span>直接出库</span>
        </el-button>
        <span class="text-muted">共 {{ filteredList.length }} 张待发药处方</span>
      </div>

      <el-alert class="tip-alert" type="info" :closable="false" show-icon
        title="仅显示已收费且未发药的处方；确认发药后将自动扣减对应批次库存并写入库存流水。" />

      <el-table v-loading="loading" :data="filteredList" border stripe class="dispense-table"
        empty-text="暂无待发药处方">
        <el-table-column prop="prescriptionNo" label="处方号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者" min-width="110" show-overflow-tooltip />
        <el-table-column prop="doctorName" label="医生" min-width="100" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ PRESCRIPTION_TYPE_TEXT[row.type] || row.type || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ formatMoney(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开方时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">查看明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawerVisible" title="处方明细" size="700px" :destroy-on-close="true"
      @opened="focusScanInput">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="currentRx">
          <!-- 扫码发药：扫一下扣一次库存，全部扫完自动完成发药 -->
          <div class="scan-panel">
            <div class="scan-panel-head">
              <span class="scan-panel-title">扫码发药</span>
              <el-tag v-if="scanCompleted" size="small" type="success" effect="dark">已发药完成</el-tag>
              <el-tag v-else-if="scanStarted" size="small" type="warning" effect="plain">
                扫码中 {{ scanDoneRows }} / {{ scanProgressRows.length }}
              </el-tag>
              <el-tag v-else size="small" type="info" effect="plain">扫一下扣一次库存</el-tag>
            </div>
            <el-alert class="scan-hint" type="info" :closable="false" show-icon
              title="中成药 / 西药 / 耗材：直接扫包装上的 20 位追溯码，扫一次核销一件；中药饮片：扫药品条形码或编码，默认发完剩余量。" />
            <div class="scan-input-row">
              <el-input ref="scanInputRef" v-model="scanInput" class="scan-input" clearable
                :disabled="scanCompleted || scanSubmitting"
                placeholder="扫码枪扫描后回车：20 位追溯码，或药品条形码 / 编码" @keyup.enter="onScanSubmit">
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-input-number v-model="scanQuantity" class="scan-qty" :min="0" :step="1"
                :controls="false" :disabled="scanCompleted || scanSubmitting"
                placeholder="本次数量（可空）" />
              <el-button type="primary" :loading="scanSubmitting" :disabled="scanCompleted"
                @click="onScanSubmit">
                扫码核销
              </el-button>
            </div>
            <div class="scan-qty-tip text-muted">
              「本次数量」仅对无需追溯药品（中药饮片等）生效，留空 = 发完剩余量；需追溯药品按追溯码一件一件核销。
            </div>
            <el-alert v-if="scanMessage" class="scan-message" :type="scanMessageType" show-icon
              :title="scanMessage" @close="scanMessage = ''" />

            <el-table v-loading="progressLoading" :data="scanProgressRows" size="small" border
              class="progress-table" empty-text="暂无扫码进度，扫码成功后自动显示">
              <el-table-column prop="drugName" label="药名" min-width="130" show-overflow-tooltip />
              <el-table-column prop="unit" label="单位" width="64" />
              <el-table-column label="需要" width="76" align="right">
                <template #default="{ row }">{{ formatQty(row.required) }}</template>
              </el-table-column>
              <el-table-column label="已扫" width="76" align="right">
                <template #default="{ row }">
                  <span :class="{ 'scan-done-text': row.done }">{{ formatQty(row.scanned) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="86" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.done ? 'success' : 'info'" effect="plain">
                    {{ row.done ? '已完成' : '待扫' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="需追溯" width="86" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.needTrace" size="small" type="warning" effect="plain">需追溯</el-tag>
                  <span v-else class="text-muted">否</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <el-descriptions :column="2" border size="small" class="rx-desc">
            <el-descriptions-item label="处方号">{{ currentRx.prescriptionNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="患者">{{ currentRx.patientName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="医生">{{ currentRx.doctorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="类型">
              {{ PRESCRIPTION_TYPE_TEXT[currentRx.type] || currentRx.type || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="剂数">{{ currentRx.doses != null ? currentRx.doses : '-' }}</el-descriptions-item>
            <el-descriptions-item label="煎法">{{ currentRx.decoctionMethod || '不涉及' }}</el-descriptions-item>
            <el-descriptions-item label="开方时间">{{ formatDateTime(currentRx.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" effect="plain" :type="rxStatusTag(currentRx.status)">
                {{ PRESCRIPTION_STATUS_TEXT[currentRx.status] || currentRx.status || '-' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="currentRx.remark" label="备注" :span="2">
              {{ currentRx.remark }}
            </el-descriptions-item>
          </el-descriptions>

          <el-table :data="rxItems" border size="small" class="detail-table" empty-text="暂无处方明细">
            <el-table-column prop="drugName" label="药名" min-width="130" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" width="110" show-overflow-tooltip />
            <el-table-column prop="unit" label="单位" width="70" />
            <el-table-column label="剂量" width="80">
              <template #default="{ row }">{{ row.dose || '-' }}</template>
            </el-table-column>
            <el-table-column label="数量" width="80" align="right">
              <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="单价" width="90" align="right">
              <template #default="{ row }">
                <span class="money">{{ formatMoney(row.price) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">
                <span class="money">{{ formatMoney(itemAmount(row)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="用法" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.usageText || '-' }}</template>
            </el-table-column>
          </el-table>

          <div v-if="traceItems.length && !scanStarted && !scanCompleted" class="trace-section">
            <div class="trace-section-head">
              <span class="trace-title">追溯码采集</span>
              <el-tag size="small" :type="allTraceScanned ? 'success' : 'warning'" effect="plain">
                已扫 {{ scannedCount }} / 需扫 {{ requiredCount }}
              </el-tag>
            </div>
            <el-alert class="trace-alert" type="warning" :closable="false" show-icon
              title="使用上方「扫码发药」时无需此步；若改用「确认发药」整单发药，须先逐盒扫描追溯码，数量不足不允许发药。" />
            <div v-for="item in traceItems" :key="item.drugId" class="trace-row">
              <div class="trace-row-head">
                <span class="trace-drug-name">{{ item.drugName }}</span>
                <span class="text-muted">
                  {{ [item.spec, item.unit].filter(Boolean).join(' / ') || '-' }}
                </span>
                <span class="trace-progress" :class="traceCodesOf(item).length >= item.required ? 'done' : 'pending'">
                  已扫 {{ traceCodesOf(item).length }} / 需要 {{ item.required }}
                </span>
              </div>
              <el-input v-model="traceInputs[item.drugId]" size="small" clearable
                placeholder="扫描追溯码后按回车录入（20 位）" @keyup.enter="addTraceCode(item)" />
              <div class="trace-code-list">
                <el-tag v-for="(code, index) in traceCodesOf(item)" :key="code" size="small" type="info" closable
                  @close="removeTraceCode(item, index)">{{ code }}</el-tag>
                <span v-if="!traceCodesOf(item).length" class="text-muted">尚未扫码</span>
              </div>
            </div>
          </div>

          <div class="drawer-total">
            合计金额：<span class="money total-amount">￥{{ formatMoney(rxTotal) }}</span>
          </div>

          <el-alert v-if="!scanStarted && !scanCompleted" class="drawer-tip" type="warning" :closable="false"
            show-icon title="发药前提示：确认发药后将扣减对应批次库存，请核对处方与实物一致。" />
        </template>
        <el-empty v-else description="暂无处方明细" />
      </div>

      <template #footer>
        <div class="drawer-footer">
          <span v-if="scanStarted || scanCompleted" class="footer-hint text-muted">
            {{ scanCompleted ? '扫码发药已完成，无需再整单发药' : '已使用扫码发药，请继续扫码至完成' }}
          </span>
          <el-button :loading="printingRx" :disabled="!currentRx" @click="printCurrentPrescription">
            打印处方
          </el-button>
          <el-button @click="drawerVisible = false">关闭</el-button>
          <el-button type="primary" :loading="dispensing" :disabled="!currentRx || scanStarted || scanCompleted"
            @click="onDispense">
            确认发药
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 直接出库：无处方领用 / 报损 -->
    <el-dialog v-model="scanOutVisible" title="直接出库（领用 / 报损）" width="500px"
      :close-on-click-modal="false" @opened="focusScanOutCode">
      <el-form ref="scanOutFormRef" :model="scanOutForm" :rules="scanOutRules" label-width="72px">
        <el-form-item label="扫码" prop="code">
          <el-input ref="scanOutCodeRef" v-model="scanOutForm.code" clearable
            placeholder="扫描药品条码，或需追溯药品的 20 位追溯码" @keyup.enter="submitScanOut" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="scanOutForm.quantity" :min="0" :step="1" :controls="false"
            style="width: 100%" placeholder="默认 1" />
          <div class="text-muted out-tip">需追溯药品按追溯码一件一件出库，数量固定为 1。</div>
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="scanOutForm.reason" type="textarea" :rows="2" maxlength="100" show-word-limit
            placeholder="必填，如：内部领用 / 破损报损 / 样品" />
        </el-form-item>
      </el-form>
      <el-alert v-if="scanOutResult" class="out-result" type="success" :closable="false" show-icon
        :title="scanOutResultTitle" />
      <template #footer>
        <el-button @click="scanOutVisible = false">关闭</el-button>
        <el-button type="warning" :loading="scanOutSubmitting" @click="submitScanOut">确认出库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  dispensePrescription,
  fetchPrescriptionHtml,
  getPrescription,
  pageDrugs,
  pendingDispense,
  pharmacyScan,
  pharmacyScanOut,
  scanProgress
} from '@/api'
import request from '@/api/request'
import { openPrintWindow } from '@/utils/print'
import {
  PRESCRIPTION_STATUS_TEXT,
  PRESCRIPTION_TYPE_TEXT,
  formatDateTime,
  formatMoney
} from '@/utils/format'

const RX_STATUS_TAG = {
  DRAFT: 'info',
  PENDING: 'warning',
  DISPENSED: 'success',
  CANCELLED: 'danger'
}

const loading = ref(false)
const list = ref([])
const keyword = ref('')

const drawerVisible = ref(false)
const detailLoading = ref(false)
const currentRx = ref(null)
const dispensing = ref(false)
const printingRx = ref(false)

/* ---------------- 追溯码采集 ---------------- */
/* 药品字典缓存：处方明细不含 needTrace/分类，用药品字典补充判断 */
const drugDict = ref({})
let drugDictLoaded = false
let drugDictReady = false
/* drugId -> 已扫描的追溯码数组 */
const traceCodes = ref({})
/* drugId -> 扫码输入框内容 */
const traceInputs = reactive({})

/* ---------------- 扫码发药 ---------------- */
const scanInputRef = ref(null)
const scanInput = ref('')
const scanQuantity = ref(undefined)
const scanSubmitting = ref(false)
const scanProgressRows = ref([])
const progressLoading = ref(false)
const scanMessage = ref('')
const scanMessageType = ref('success')

const scanStarted = computed(() =>
  scanProgressRows.value.some((row) => Number(row?.scanned) > 0)
)
const scanCompleted = computed(
  () =>
    currentRx.value?.status === 'DISPENSED' ||
    (scanProgressRows.value.length > 0 && scanProgressRows.value.every((row) => row?.done))
)
const scanDoneRows = computed(() => scanProgressRows.value.filter((row) => row?.done).length)

/* ---------------- 直接出库 ---------------- */
const scanOutVisible = ref(false)
const scanOutSubmitting = ref(false)
const scanOutResult = ref(null)
const scanOutFormRef = ref(null)
const scanOutCodeRef = ref(null)
const scanOutForm = reactive({ code: '', quantity: 1, reason: '' })
const scanOutRules = {
  code: [{ required: true, message: '请扫描药品条码或追溯码', trigger: 'blur' }],
  quantity: [{ required: true, type: 'number', message: '请输入数量', trigger: 'change' }],
  reason: [{ required: true, message: '请填写出库原因', trigger: 'blur' }]
}
const scanOutResultTitle = computed(() => {
  const data = scanOutResult.value
  if (!data) return ''
  const qty = data.quantity != null ? ` ${formatQty(data.quantity)}` : ''
  const stock = data.stockAfter != null ? `，当前库存 ${formatQty(data.stockAfter)}` : ''
  const reason = data.reason ? `，原因：${data.reason}` : ''
  return `${data.message || `已出库：${data.drugName || ''}${qty}`}${stock}${reason}`
})

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((row) => {
    const patientName = String(row.patientName || '').toLowerCase()
    const prescriptionNo = String(row.prescriptionNo || '').toLowerCase()
    return patientName.includes(kw) || prescriptionNo.includes(kw)
  })
})

const rxItems = computed(() => currentRx.value?.items || [])

const rxTotal = computed(() => {
  if (currentRx.value?.totalAmount != null) return Number(currentRx.value.totalAmount)
  return rxItems.value.reduce((sum, item) => sum + itemAmount(item), 0)
})

function itemAmount(row) {
  if (row?.amount != null) return Number(row.amount)
  return Number(row?.price || 0) * Number(row?.quantity || 0)
}

function rxStatusTag(status) {
  return RX_STATUS_TAG[status] || 'info'
}

/* ---------------- 追溯码判断与采集 ---------------- */
function resolveNeedTrace(item) {
  if (!item) return false
  const direct = item.needTrace ?? item.drugNeedTrace
  if (direct !== undefined && direct !== null && direct !== '') return Number(direct) === 1
  const category = item.category || item.drugCategory
  if (category) return category !== 'HERB'
  const drug = drugDict.value[item.drugId]
  if (drug) {
    if (drug.needTrace !== undefined && drug.needTrace !== null && drug.needTrace !== '') {
      return Number(drug.needTrace) === 1
    }
    if (drug.category) return drug.category !== 'HERB'
  }
  // 字典已完整加载仍找不到药品：后端按药品不存在处理，不强制扫码
  if (drugDictReady) return false
  // 字典未就绪时兜底：按处方类型判断（中药饮片暂不强制采集，中成药/西药需采集）
  return currentRx.value?.type ? currentRx.value.type !== 'HERB' : false
}

const traceItems = computed(() =>
  rxItems.value
    .filter((item) => resolveNeedTrace(item) && Number(item.quantity) > 0)
    .map((item) => ({
      drugId: item.drugId,
      drugName: item.drugName || '未知药品',
      spec: item.spec,
      unit: item.unit,
      required: Math.ceil(Number(item.quantity) || 0)
    }))
)

const requiredCount = computed(() => traceItems.value.reduce((sum, item) => sum + item.required, 0))
const scannedCount = computed(() =>
  traceItems.value.reduce((sum, item) => sum + traceCodesOf(item).length, 0)
)
const allTraceScanned = computed(
  () => traceItems.value.length > 0 && scannedCount.value >= requiredCount.value
)

function traceCodesOf(item) {
  return traceCodes.value[item?.drugId] || []
}

async function ensureDrugDict() {
  if (drugDictLoaded) return
  drugDictLoaded = true
  const map = {}
  try {
    // 药品字典按页拉全，用于按 drugId 判断 needTrace / 分类
    const size = 200
    for (let page = 1; page <= 20; page++) {
      const data = await pageDrugs({ page, size })
      const rows = data?.list || []
      rows.forEach((drug) => {
        if (drug?.id != null) map[drug.id] = drug
      })
      const total = Number(data?.total || 0)
      if (!rows.length || Object.keys(map).length >= total) break
    }
    drugDictReady = true
  } catch {
    // 字典加载失败时保留已取到的部分，后续走处方类型兜底
    drugDictReady = Object.keys(map).length > 0
  } finally {
    drugDict.value = map
  }
}

function resetTraceState() {
  traceCodes.value = {}
  Object.keys(traceInputs).forEach((key) => {
    delete traceInputs[key]
  })
}

function addTraceCode(item) {
  if (!item?.drugId) return
  const key = item.drugId
  const raw = String(traceInputs[key] || '').trim()
  if (!raw) return
  // 与后端一致：20 位字母或数字，统一转大写
  if (!/^[A-Za-z0-9]{20}$/.test(raw)) {
    ElMessage.warning(`追溯码应为 20 位字母或数字，当前 ${raw.length} 位`)
    traceInputs[key] = ''
    return
  }
  const code = raw.toUpperCase()
  const codes = traceCodes.value[key] || (traceCodes.value[key] = [])
  if (codes.includes(code)) {
    ElMessage.warning('该追溯码已扫描，请勿重复录入')
    traceInputs[key] = ''
    return
  }
  if (codes.length >= item.required) {
    ElMessage.warning(`「${item.drugName}」已扫满 ${item.required} 个，无需继续扫码`)
    traceInputs[key] = ''
    return
  }
  codes.push(code)
  traceInputs[key] = ''
}

function removeTraceCode(item, index) {
  const codes = traceCodes.value[item?.drugId]
  if (Array.isArray(codes)) codes.splice(index, 1)
}

async function printCurrentPrescription() {
  if (!currentRx.value?.id) return
  printingRx.value = true
  try {
    const html = await fetchPrescriptionHtml(currentRx.value.id)
    if (!openPrintWindow(html)) {
      ElMessage.warning('打印窗口被浏览器拦截或打印内容异常，请允许本站弹出窗口后重试')
    }
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    printingRx.value = false
  }
}

function formatQty(value) {
  const num = Number(value ?? 0)
  if (Number.isNaN(num)) return '0'
  return Number.isInteger(num) ? String(num) : String(Number(num.toFixed(3)))
}

/* ---------------- 扫码发药 ---------------- */
function focusScanInput() {
  nextTick(() => {
    if (!scanCompleted.value) scanInputRef.value?.focus()
  })
}

function resetScanState() {
  scanInput.value = ''
  scanQuantity.value = undefined
  scanProgressRows.value = []
  scanMessage.value = ''
  scanMessageType.value = 'success'
}

async function loadScanProgress() {
  if (!currentRx.value?.id) return
  progressLoading.value = true
  try {
    const data = await scanProgress(currentRx.value.id)
    scanProgressRows.value = Array.isArray(data) ? data : []
  } catch {
    scanProgressRows.value = []
  } finally {
    progressLoading.value = false
  }
}

function applyScanResult(data) {
  if (Array.isArray(data?.progress)) scanProgressRows.value = data.progress
  const completed = !!data?.completed
  scanMessage.value = data?.message || (completed ? '全部药品已扫完，处方已发药完成' : '扫码出库成功')
  scanMessageType.value = 'success'
  if (completed) {
    if (currentRx.value) currentRx.value = { ...currentRx.value, status: 'DISPENSED' }
    ElNotification({
      type: 'success',
      title: '已自动完成发药',
      message: data?.message || '全部药品已扫完，处方已发药完成',
      duration: 4500
    })
  } else {
    ElNotification({
      type: 'success',
      title: data?.drugName ? `已出库：${data.drugName}` : '扫码出库成功',
      message: data?.message || '',
      duration: 2500
    })
  }
  return completed
}

async function onScanSubmit() {
  if (!currentRx.value || scanSubmitting.value) return
  if (scanCompleted.value) {
    ElMessage.info('该处方已发药完成，无需继续扫码')
    return
  }
  const code = scanInput.value.trim()
  if (!code) {
    focusScanInput()
    return
  }
  scanSubmitting.value = true
  try {
    const payload = { prescriptionId: currentRx.value.id, code }
    const qty = Number(scanQuantity.value)
    if (scanQuantity.value !== undefined && scanQuantity.value !== null && !Number.isNaN(qty) && qty > 0) {
      payload.quantity = qty
    }
    const data = await pharmacyScan(payload)
    const completed = applyScanResult(data)
    scanInput.value = ''
    scanQuantity.value = undefined
    if (completed) await loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理；清空后把焦点留在扫码框，方便直接重扫
    scanInput.value = ''
  } finally {
    scanSubmitting.value = false
    focusScanInput()
  }
}

/* ---------------- 直接出库 ---------------- */
function openScanOutDialog() {
  scanOutVisible.value = true
  scanOutResult.value = null
  scanOutForm.code = ''
  scanOutForm.quantity = 1
  scanOutForm.reason = ''
  nextTick(() => scanOutFormRef.value?.clearValidate())
}

function focusScanOutCode() {
  nextTick(() => scanOutCodeRef.value?.focus())
}

async function submitScanOut() {
  if (scanOutFormRef.value) {
    try {
      await scanOutFormRef.value.validate()
    } catch {
      return
    }
  }
  const code = scanOutForm.code.trim()
  const reason = scanOutForm.reason.trim()
  if (!code || !reason) return
  scanOutSubmitting.value = true
  try {
    const data = await pharmacyScanOut({
      code,
      quantity: Number(scanOutForm.quantity) || 1,
      reason
    })
    scanOutResult.value = data || null
    ElNotification({
      type: 'success',
      title: '直接出库成功',
      message: scanOutResultTitle.value || '出库成功',
      duration: 3500
    })
    scanOutForm.code = ''
    scanOutForm.reason = ''
    scanOutForm.quantity = 1
    scanOutFormRef.value?.clearValidate()
    focusScanOutCode()
    await loadList()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    scanOutSubmitting.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await pendingDispense()
    list.value = Array.isArray(data) ? data : (data?.list || [])
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  drawerVisible.value = true
  detailLoading.value = true
  currentRx.value = row
  resetTraceState()
  resetScanState()
  try {
    const detail = await getPrescription(row.id)
    if (detail) currentRx.value = detail
  } catch {
    /* 失败提示由 request 拦截器统一处理，保留列表行数据兜底展示 */
  }
  // 处方明细不含 needTrace / 分类字段，补充药品字典用于判断是否需扫追溯码；同时载入扫码进度
  await Promise.all([loadScanProgress(), ensureDrugDict()])
  detailLoading.value = false
  focusScanInput()
}

async function onDispense() {
  if (!currentRx.value) return
  if (scanStarted.value || scanCompleted.value) {
    ElMessage.warning('已使用扫码发药，请继续扫码至完成')
    return
  }
  const missing = traceItems.value.filter((item) => traceCodesOf(item).length < item.required)
  if (missing.length) {
    const detail = missing
      .map((item) => `${item.drugName}：已扫 ${traceCodesOf(item).length} / 需要 ${item.required}`)
      .join('；')
    ElMessage.warning(`追溯码数量不足，请扫码后再发药（${detail}）`)
    return
  }
  const traces = traceItems.value.map((item) => ({
    drugId: item.drugId,
    codes: [...traceCodesOf(item)]
  }))
  const traceTip = traces.length
    ? `本次将提交 ${traces.reduce((sum, trace) => sum + trace.codes.length, 0)} 个追溯码。`
    : ''
  try {
    await ElMessageBox.confirm(
      `确认发药后，将扣减处方「${currentRx.value.prescriptionNo || ''}」对应批次的库存。${traceTip}请核对处方与实物一致。`,
      '发药确认',
      { type: 'warning', confirmButtonText: '确认发药', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  dispensing.value = true
  try {
    if (traces.length) {
      // 处方含需追溯药品：按契约携带 traces 提交，饮片等无需扫码的药品不传
      await request.post(`/prescriptions/${currentRx.value.id}/dispense`, { traces })
    } else {
      await dispensePrescription(currentRx.value.id)
    }
    ElMessage.success('发药成功，库存已扣减')
    drawerVisible.value = false
    resetTraceState()
    await loadList()
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    dispensing.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.dispense-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-title {
  flex: 0 0 auto;
  margin: 0;
}

.search-input {
  width: 240px;
}

.tip-alert {
  margin-bottom: 12px;
}

.dispense-table {
  width: 100%;
}

.drawer-body {
  min-height: 200px;
}

.rx-desc {
  margin-bottom: 14px;
}

.detail-table {
  width: 100%;
}

.drawer-total {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 4px;
  padding: 12px 2px;
  font-size: 14px;
}

.total-amount {
  font-size: 18px;
  color: #f56c6c;
}

.drawer-tip {
  margin-top: 6px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.footer-hint {
  margin-right: auto;
  font-size: 12px;
}

.scan-panel {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #b3d8ff;
  border-radius: 6px;
  background: #f0f9ff;
}

.scan-panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.scan-panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2d3d;
}

.scan-hint {
  margin-bottom: 10px;
}

.scan-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scan-input {
  flex: 1;
  min-width: 0;
}

.scan-qty {
  flex: 0 0 120px;
  width: 120px;
}

.scan-qty-tip {
  margin-top: 4px;
  font-size: 12px;
}

.scan-message {
  margin-top: 8px;
}

.progress-table {
  width: 100%;
  margin-top: 10px;
}

.scan-done-text {
  color: #67c23a;
  font-weight: 600;
}

.out-tip {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
}

.out-result {
  margin-top: 4px;
}

.trace-section {
  margin: 14px 0 4px;
  padding: 12px;
  border: 1px solid #faecd8;
  border-radius: 6px;
  background: #fdf6ec;
}

.trace-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.trace-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.trace-alert {
  margin-bottom: 10px;
}

.trace-row {
  padding: 8px 0;
  border-top: 1px dashed #f0d9b5;
}

.trace-row:first-of-type {
  border-top: none;
}

.trace-row-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.trace-drug-name {
  font-weight: 600;
}

.trace-progress {
  margin-left: auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.trace-progress.pending {
  color: #e6a23c;
}

.trace-progress.done {
  color: #67c23a;
}

.trace-code-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  min-height: 24px;
  align-items: center;
}
</style>
