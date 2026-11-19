<template>
  <div class="cashier-desk">
    <div class="page-card">
      <div class="page-toolbar">
        <span class="toolbar-label">日期</span>
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期"
          :clearable="false" style="width: 160px" @change="onDateChange" />
        <el-select v-model="status" placeholder="状态" style="width: 140px" @change="onFilterChange">
          <el-option label="全部状态" value="" />
          <el-option label="待收费" value="UNPAID" />
          <el-option label="已收费" value="PAID" />
          <el-option label="已退费" value="REFUNDED" />
        </el-select>
        <el-button :icon="Refresh" :loading="refreshing" @click="refreshAll">刷新</el-button>
        <span class="text-muted toolbar-tip">当前筛选共 {{ total }} 笔收费单</span>
      </div>

      <div v-loading="statsLoading" class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">今日收费总额</div>
          <div class="stat-value money">¥ {{ formatMoney(summary.total) }}</div>
          <div class="stat-foot text-muted">{{ date }} 已收金额</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">收费笔数</div>
          <div class="stat-value">{{ summary.count ?? 0 }}</div>
          <div class="stat-foot text-muted">当日已收费单数</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">待收费笔数</div>
          <div class="stat-value stat-warning">{{ unpaidCount }}</div>
          <div class="stat-foot text-muted">当日尚未支付的收费单</div>
        </div>
      </div>

      <el-table v-loading="tableLoading" :data="orders" size="default">
        <el-table-column prop="orderNo" label="单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者" min-width="110" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ chargeTypeText(row.type) }}</template>
        </el-table-column>
        <el-table-column label="应收" width="120" align="right">
          <template #default="{ row }">
            <span class="money">¥ {{ formatMoney(row.payableAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="CHARGE_STATUS_TAG[row.status] || 'info'">
              {{ CHARGE_STATUS_TEXT[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="164">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="支付时间" width="164">
          <template #default="{ row }">{{ row.paidAt ? formatDateTime(row.paidAt) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="192" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openCharge(row)">
              {{ row.status === 'UNPAID' ? '收费' : '明细' }}
            </el-button>
            <el-button v-if="row.status === 'PAID'" link type="danger" size="small" @click="onRefund(row)">
              退费
            </el-button>
            <el-button v-if="row.status !== 'UNPAID'" link type="success" size="small"
              :loading="printingOrderId === row.id" @click="printOrderReceipt(row)">打印小票</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-hint">
            <el-empty :image-size="80" :description="`${date} 没有符合条件的收费单`" />
          </div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="onSizeChange" @current-change="loadOrders" />
      </div>
    </div>

    <!-- 收费 / 明细 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="740px" :close-on-click-modal="false">
      <div v-loading="detailLoading" class="charge-detail">
        <template v-if="charge">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="收费单号">{{ charge.order.orderNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="CHARGE_STATUS_TAG[charge.order.status] || 'info'">
                {{ CHARGE_STATUS_TEXT[charge.order.status] || charge.order.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="类型">{{ chargeTypeText(charge.order.type) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(charge.order.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="应收">
              <span class="money">¥ {{ formatMoney(charge.order.payableAmount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="优惠">
              <span class="money">¥ {{ formatMoney(charge.order.discountAmount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="实收">
              <span class="money">¥ {{ formatMoney(charge.order.paidAmount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="支付时间">{{ formatDateTime(charge.order.paidAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-section-title">患者信息</div>
          <el-descriptions v-if="charge.patient" :column="2" border size="small">
            <el-descriptions-item label="姓名">{{ charge.patient.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">
              {{ GENDER_TEXT[charge.patient.gender] || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="年龄">
              {{ charge.patient.age != null ? `${charge.patient.age} 岁` : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="手机号">{{ charge.patient.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="病历号">{{ charge.patient.patientNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="会员卡">
              {{ charge.patient.memberId ? '已开卡' : '未开卡' }}
            </el-descriptions-item>
          </el-descriptions>
          <div v-else class="empty-hint">未获取到患者信息</div>

          <div class="detail-section-title">收费明细</div>
          <el-table :data="charge.items" size="small" border>
            <el-table-column type="index" label="#" width="48" align="center" />
            <el-table-column prop="itemName" label="项目" min-width="200" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="80" align="center" />
            <el-table-column label="单价" width="110" align="right">
              <template #default="{ row }">
                <span class="money">¥ {{ formatMoney(row.unitPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">
                <span class="money">¥ {{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="50" description="暂无收费明细" />
            </template>
          </el-table>
          <div class="detail-total">
            合计应收
            <span class="money total-amount">¥ {{ formatMoney(charge.order.payableAmount) }}</span>
          </div>

          <template v-if="isUnpaid">
            <div class="detail-section-title">收款</div>
            <el-form label-width="84px" class="pay-form">
              <el-form-item label="支付方式" required>
                <el-select v-model="payForm.method" style="width: 100%">
                  <el-option v-for="m in payMethods" :key="m.value" :label="m.label" :value="m.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="payForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="选填" />
              </el-form-item>
            </el-form>
            <el-alert v-if="payForm.method === 'MEMBER_BALANCE'" class="member-alert" :closable="false" show-icon
              :type="memberOpened ? 'success' : 'warning'"
              :title="memberOpened
                ? `患者已开卡（会员 ID ${charge.patient?.memberId}），确认后将直接从会员余额中扣除。`
                : '该患者尚未开通会员卡，无法使用会员余额支付，请先到「会员管理」开卡。'" />
          </template>
          <el-alert v-else class="member-alert" :closable="false" show-icon type="info"
            :title="charge.order.status === 'PAID' ? '该收费单已完成收费。' : '该收费单已退费。'" />
        </template>
        <div v-else-if="!detailLoading" class="empty-hint">
          <el-empty :image-size="60" description="未获取到收费单信息" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="charge?.order?.status !== 'UNPAID' && charge?.order?.id" :loading="printingReceipt"
          @click="printCurrentReceipt">
          打印小票
        </el-button>
        <el-button v-if="isUnpaid" type="primary" :loading="paying" :disabled="memberBalanceBlocked"
          @click="submitPay">
          确认收费（¥ {{ formatMoney(charge?.order?.payableAmount) }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { chargeSummary, fetchReceiptHtml, getCharge, pageCharges, payCharge, refundCharge } from '@/api'
import { openPrintWindow } from '@/utils/print'
import {
  CHARGE_STATUS_TAG,
  CHARGE_STATUS_TEXT,
  formatDateTime,
  formatMoney,
  GENDER_TEXT,
  PAY_METHOD_TEXT,
  todayStr
} from '@/utils/format'

/* 收费单类型：契约未固化枚举，兼容常见取值，未知值原样展示 */
const CHARGE_TYPE_TEXT = {
  REGISTER: '挂号费',
  REGISTRATION: '挂号费',
  REGISTER_FEE: '挂号费',
  PRESCRIPTION: '处方',
  DRUG: '处方',
  OTHER: '其他',
  TREATMENT: '诊疗费'
}

function chargeTypeText(type) {
  if (!type) return '-'
  return CHARGE_TYPE_TEXT[type] || type
}

const payMethods = Object.entries(PAY_METHOD_TEXT).map(([value, label]) => ({ value, label }))

/* ---------------- 查询 ---------------- */
const date = ref(todayStr())
const status = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const orders = ref([])
const tableLoading = ref(false)
const statsLoading = ref(false)
const refreshing = ref(false)

const summary = ref({ total: 0, count: 0 })
const unpaidCount = ref(0)

async function loadOrders() {
  tableLoading.value = true
  try {
    const data = await pageCharges({
      status: status.value,
      date: date.value,
      page: page.value,
      size: size.value
    })
    orders.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    tableLoading.value = false
  }
}

async function loadSummary() {
  statsLoading.value = true
  try {
    const data = await chargeSummary({ date: date.value })
    summary.value = data || { total: 0, count: 0 }
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    statsLoading.value = false
  }
}

async function loadUnpaidCount() {
  try {
    const data = await pageCharges({ status: 'UNPAID', date: date.value, page: 1, size: 1 })
    unpaidCount.value = data?.total || 0
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  }
}

async function refreshAll() {
  if (!date.value) date.value = todayStr()
  refreshing.value = true
  await Promise.allSettled([loadOrders(), loadSummary(), loadUnpaidCount()])
  refreshing.value = false
}

function onDateChange() {
  page.value = 1
  refreshAll()
}

function onFilterChange() {
  page.value = 1
  loadOrders()
}

function onSizeChange() {
  page.value = 1
  loadOrders()
}

/* ---------------- 收费 / 明细 ---------------- */
const dialogVisible = ref(false)
const detailLoading = ref(false)
const charge = ref(null)
const currentId = ref(null)
const paying = ref(false)
const printingReceipt = ref(false)
const printingOrderId = ref(null)

const payForm = reactive({ method: 'CASH', remark: '' })

const isUnpaid = computed(() => charge.value?.order?.status === 'UNPAID')
const memberOpened = computed(() => !!charge.value?.patient?.memberId)
const memberBalanceBlocked = computed(
  () => isUnpaid.value && payForm.method === 'MEMBER_BALANCE' && !memberOpened.value
)
const dialogTitle = computed(() =>
  charge.value?.order?.orderNo ? `收费单 ${charge.value.order.orderNo}` : '收费单'
)

async function loadChargeDetail(id) {
  detailLoading.value = true
  charge.value = null
  try {
    const data = await getCharge(id)
    const order = data?.order || {}
    const items = data?.items || order.items || []
    const patient = data?.patient || null
    charge.value = { order, items, patient }
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    detailLoading.value = false
  }
}

function openCharge(row) {
  currentId.value = row.id
  payForm.method = 'CASH'
  payForm.remark = ''
  dialogVisible.value = true
  loadChargeDetail(row.id)
}

async function submitPay() {
  const orderId = charge.value?.order?.id || currentId.value
  if (!orderId) return
  paying.value = true
  try {
    await payCharge(orderId, {
      method: payForm.method,
      remark: payForm.remark.trim() || null
    })
    ElMessage.success(`收费成功，实收 ¥${formatMoney(charge.value?.order?.payableAmount)}`)
    await refreshAll()
    // 收费成功后保留弹窗并刷新为已收费状态，方便直接打印小票
    await loadChargeDetail(orderId)
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    paying.value = false
  }
}

/* ---------------- 打印小票 ---------------- */
async function fetchAndPrintReceipt(orderId) {
  if (!orderId) return false
  try {
    const html = await fetchReceiptHtml(orderId)
    if (!openPrintWindow(html)) {
      ElMessage.warning('打印窗口被浏览器拦截或打印内容异常，请允许本站弹出窗口后重试')
      return false
    }
    return true
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
    return false
  }
}

async function printOrderReceipt(row) {
  if (!row?.id) return
  printingOrderId.value = row.id
  try {
    await fetchAndPrintReceipt(row.id)
  } finally {
    printingOrderId.value = null
  }
}

async function printCurrentReceipt() {
  const orderId = charge.value?.order?.id || currentId.value
  if (!orderId) return
  printingReceipt.value = true
  try {
    await fetchAndPrintReceipt(orderId)
  } finally {
    printingReceipt.value = false
  }
}

/* ---------------- 退费 ---------------- */
async function onRefund(row) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `确认为「${row.patientName || '该患者'}」的收费单 ${row.orderNo || ''} 办理退费？退费后不可撤销。`,
      '退费确认',
      {
        confirmButtonText: '确认退费',
        cancelButtonText: '取消',
        type: 'warning',
        inputPlaceholder: '请输入退费原因',
        inputValidator: (value) => (value && value.trim() ? true : '请输入退费原因')
      }
    )
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    await refundCharge(row.id, { reason })
    ElMessage.success('退费成功')
    await refreshAll()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  }
}

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.cashier-desk {
  display: flex;
  flex-direction: column;
}

.toolbar-label {
  font-size: 13px;
  color: #606266;
}

.toolbar-tip {
  margin-left: auto;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: linear-gradient(180deg, #fbfdfc 0%, #fff 100%);
}

.stat-label {
  font-size: 13px;
  color: #606266;
}

.stat-value {
  margin: 8px 0 4px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  color: #1f2d3d;
  font-variant-numeric: tabular-nums;
}

.stat-value.stat-warning {
  color: #e6a23c;
}

.stat-foot {
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.charge-detail {
  min-height: 120px;
}

.detail-section-title {
  margin: 20px 0 10px;
  font-size: 14px;
  font-weight: 600;
  padding-left: 8px;
  border-left: 3px solid #1f8a6d;
}

.detail-total {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
  font-size: 13px;
  color: #606266;
}

.total-amount {
  font-size: 20px;
  color: #c45656;
}

.pay-form {
  margin-top: 4px;
}

.member-alert {
  margin: 0 0 4px;
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-tip {
    margin-left: 0;
  }
}
</style>
