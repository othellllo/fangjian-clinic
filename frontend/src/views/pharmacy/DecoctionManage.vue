<template>
  <div class="decoction-page">
    <div class="page-card">
      <div class="status-filter">
        <el-radio-group v-model="statusFilter" @change="handleStatusChange">
          <el-radio-button v-for="item in STATUS_OPTIONS" :key="item.key" :value="item.value">
            {{ item.label }}
            <span class="count-badge">{{ statusCounts[item.key] ?? 0 }}</span>
          </el-radio-button>
        </el-radio-group>
      </div>

      <div class="page-toolbar">
        <el-input
          v-model="keyword"
          placeholder="代煎号 / 患者姓名"
          clearable
          style="width: 240px"
          :prefix-icon="Search"
          @keyup.enter="handleQuery"
          @clear="handleQuery"
        />
        <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
        <span class="text-muted">共 {{ total }} 条代煎单</span>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        border
        stripe
        class="decoction-table"
        :row-style="{ cursor: 'pointer' }"
        @row-click="openDetail"
      >
        <el-table-column prop="decoctionNo" label="代煎号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者" min-width="110" show-overflow-tooltip />
        <el-table-column prop="doctorName" label="医生" min-width="110" show-overflow-tooltip />
        <el-table-column label="剂数" width="82" align="center">
          <template #default="{ row }">{{ row.doses != null ? `${row.doses} 剂` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="104" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_TAG[row.status] || 'info'">
              {{ STATUS_TEXT[row.status] || row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预计完成时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.expectedTime) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">明细</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              size="small"
              :loading="actingId === row.id"
              @click.stop="onCook(row)"
            >
              开始煎制
            </el-button>
            <el-button
              v-if="row.status === 'COOKING'"
              link
              type="success"
              size="small"
              :loading="actingId === row.id"
              @click.stop="onReady(row)"
            >
              煎制完成
            </el-button>
            <el-button
              v-if="row.status === 'READY'"
              link
              type="warning"
              size="small"
              :loading="actingId === row.id"
              @click.stop="openDeliver(row)"
            >
              交付
            </el-button>
            <el-button
              v-if="isCancellable(row)"
              link
              type="danger"
              size="small"
              @click.stop="onCancel(row)"
            >
              作废
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="80" description="暂无代煎单" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        class="pagination"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="loadList"
      />
    </div>

    <!-- 交付 -->
    <el-dialog
      v-model="deliverVisible"
      title="代煎交付"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetDeliverForm"
    >
      <el-form label-width="90px">
        <el-form-item label="代煎单">
          <span>{{ deliverRow?.decoctionNo || '-' }}</span>
          <span v-if="deliverRow?.patientName" class="text-muted deliver-patient">
            患者：{{ deliverRow.patientName }}
          </span>
        </el-form-item>
        <el-form-item label="交付方式" required>
          <el-radio-group v-model="deliverForm.deliverType">
            <el-radio-button value="SELF">自取</el-radio-button>
            <el-radio-button value="DELIVERY">配送</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="deliverForm.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="选填，如取药人、配送地址等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverVisible = false">取消</el-button>
        <el-button type="primary" :loading="delivering" @click="submitDeliver">确认交付</el-button>
      </template>
    </el-dialog>

    <!-- 明细抽屉 -->
    <el-drawer v-model="drawerVisible" title="代煎明细" size="680px" :destroy-on-close="true">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail">
          <el-alert
            v-if="detail.status === 'CANCELLED'"
            class="cancel-alert"
            type="warning"
            :closable="false"
            show-icon
            title="该代煎单已作废，不再进行煎制与交付。"
          />
          <el-descriptions :column="2" border size="small" class="detail-desc">
            <el-descriptions-item label="代煎号">{{ detail.decoctionNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处方号">{{ detail.prescriptionNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="患者">{{ detail.patientName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="医生">{{ detail.doctorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="剂数">
              {{ detail.doses != null ? `${detail.doses} 剂` : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="STATUS_TAG[detail.status] || 'info'">
                {{ STATUS_TEXT[detail.status] || detail.status || '-' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="煎法">{{ detail.decoctionMethod || '代煎' }}</el-descriptions-item>
            <el-descriptions-item label="预计完成">{{ formatDateTime(detail.expectedTime) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.deliverType" label="交付方式">
              {{ deliverTypeText(detail.deliverType) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.deliverTime" label="交付时间">
              {{ formatDateTime(detail.deliverTime) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.remark" label="备注" :span="2">
              {{ detail.remark }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">进度时间线</div>
          <el-timeline class="timeline">
            <el-timeline-item
              v-for="item in timeline"
              :key="item.label"
              :timestamp="formatDateTime(item.time)"
              :type="item.done ? 'success' : 'info'"
              :hollow="!item.done"
            >
              {{ item.label }}
              <span v-if="!item.done" class="text-muted">（未完成）</span>
            </el-timeline-item>
          </el-timeline>

          <div class="section-title">药品明细</div>
          <el-table :data="detailItems" border size="small" empty-text="暂无药品明细">
            <el-table-column prop="drugName" label="药名" min-width="140" show-overflow-tooltip />
            <el-table-column label="剂量" width="90" align="center">
              <template #default="{ row }">{{ row.dose || '-' }}</template>
            </el-table-column>
            <el-table-column label="数量" width="90" align="right">
              <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="单位" width="80" align="center">
              <template #default="{ row }">{{ row.unit || '-' }}</template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else description="暂无代煎明细" />
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">关闭</el-button>
          <el-button
            v-if="detail?.status === 'PENDING'"
            type="primary"
            :loading="actingId === detail.id"
            @click="onCook(detail)"
          >
            开始煎制
          </el-button>
          <el-button
            v-if="detail?.status === 'COOKING'"
            type="success"
            :loading="actingId === detail.id"
            @click="onReady(detail)"
          >
            煎制完成
          </el-button>
          <el-button v-if="detail?.status === 'READY'" type="warning" @click="openDeliver(detail)">
            交付
          </el-button>
          <el-button v-if="isCancellable(detail)" type="danger" plain @click="onCancel(detail)">
            作废
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  cancelDecoction,
  cookDecoction,
  deliverDecoction,
  getDecoction,
  pageDecoctions,
  readyDecoction
} from '@/api'
import { formatDateTime } from '@/utils/format'

const STATUS_TEXT = {
  PENDING: '待煎',
  COOKING: '煎制中',
  READY: '待取',
  DELIVERED: '已交付',
  CANCELLED: '已作废'
}

const STATUS_TAG = {
  PENDING: 'warning',
  COOKING: 'primary',
  READY: 'success',
  DELIVERED: 'info',
  CANCELLED: 'danger'
}

const STATUS_OPTIONS = [
  { key: 'ALL', value: '', label: '全部' },
  { key: 'PENDING', value: 'PENDING', label: '待煎' },
  { key: 'COOKING', value: 'COOKING', label: '煎制中' },
  { key: 'READY', value: 'READY', label: '待取' },
  { key: 'DELIVERED', value: 'DELIVERED', label: '已交付' },
  { key: 'CANCELLED', value: 'CANCELLED', label: '已作废' }
]

// 交付方式取值契约未固定枚举，前端约定 SELF=自取 / DELIVERY=配送
const DELIVER_TYPE_TEXT = {
  SELF: '自取',
  DELIVERY: '配送',
  SELF_PICKUP: '自取',
  PICKUP: '自取',
  EXPRESS: '配送'
}

const CANCELLABLE_STATUS = ['PENDING', 'COOKING', 'READY']

/* ---------------- 筛选 / 列表 ---------------- */
const statusFilter = ref('')
const keyword = ref('')
const statusCounts = ref({})
const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const actingId = ref(null)

function deliverTypeText(value) {
  return DELIVER_TYPE_TEXT[value] || value || '-'
}

function isCancellable(row) {
  return !!row && CANCELLABLE_STATUS.includes(row.status)
}

async function loadList() {
  loading.value = true
  try {
    const params = { keyword: keyword.value.trim(), page: page.value, size: size.value }
    if (statusFilter.value) params.status = statusFilter.value
    const data = await pageDecoctions(params)
    list.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total ?? 0)
  } catch {
    /* 错误提示由 request 拦截器统一处理，保留已有列表 */
  } finally {
    loading.value = false
  }
}

// 各状态数量统一通过 pageDecoctions({status, page:1, size:1}).total 获取
async function loadCounts() {
  const kw = keyword.value.trim()
  const results = await Promise.allSettled(
    STATUS_OPTIONS.map(async (option) => {
      const params = { page: 1, size: 1 }
      if (option.value) params.status = option.value
      if (kw) params.keyword = kw
      const data = await pageDecoctions(params)
      return Number(data?.total ?? 0)
    })
  )
  const next = {}
  STATUS_OPTIONS.forEach((option, index) => {
    const result = results[index]
    next[option.key] =
      result.status === 'fulfilled' ? result.value : Number(statusCounts.value[option.key] ?? 0)
  })
  statusCounts.value = next
}

async function refreshAll() {
  await Promise.allSettled([loadList(), loadCounts()])
}

function handleStatusChange() {
  page.value = 1
  loadList()
}

function handleQuery() {
  page.value = 1
  refreshAll()
}

function handleSizeChange() {
  page.value = 1
  loadList()
}

/* ---------------- 代煎动作 ---------------- */
async function onCook(row) {
  if (!row || actingId.value) return
  actingId.value = row.id
  try {
    await cookDecoction(row.id)
    ElMessage.success('已开始煎制')
    drawerVisible.value = false
    await refreshAll()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    actingId.value = null
  }
}

async function onReady(row) {
  if (!row || actingId.value) return
  actingId.value = row.id
  try {
    await readyDecoction(row.id)
    ElMessage.success('煎制完成，代煎单已转为待取')
    drawerVisible.value = false
    await refreshAll()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    actingId.value = null
  }
}

async function onCancel(row) {
  if (!row || actingId.value) return
  try {
    await ElMessageBox.confirm(
      `确认作废代煎单「${row.decoctionNo || ''}」？作废后不可恢复。`,
      '作废代煎单',
      { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await cancelDecoction(row.id)
    ElMessage.success('代煎单已作废')
    drawerVisible.value = false
    await refreshAll()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    actingId.value = null
  }
}

/* ---------------- 交付 ---------------- */
const deliverVisible = ref(false)
const delivering = ref(false)
const deliverRow = ref(null)
const deliverForm = reactive({ deliverType: 'SELF', remark: '' })

function resetDeliverForm() {
  deliverForm.deliverType = 'SELF'
  deliverForm.remark = ''
  deliverRow.value = null
}

function openDeliver(row) {
  if (!row?.id) return
  deliverRow.value = row
  deliverForm.deliverType = 'SELF'
  deliverForm.remark = ''
  deliverVisible.value = true
}

async function submitDeliver() {
  if (!deliverRow.value) return
  delivering.value = true
  try {
    await deliverDecoction(deliverRow.value.id, {
      deliverType: deliverForm.deliverType,
      remark: deliverForm.remark.trim()
    })
    ElMessage.success('交付成功')
    deliverVisible.value = false
    drawerVisible.value = false
    await refreshAll()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    delivering.value = false
  }
}

/* ---------------- 明细抽屉 ---------------- */
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const detailItems = computed(() => detail.value?.items || [])

const timeline = computed(() => {
  const current = detail.value
  if (!current) return []
  return [
    { label: '创建代煎单', time: current.createdAt, done: true },
    { label: '开始煎制', time: current.cookStartTime, done: !!current.cookStartTime },
    { label: '煎制完成', time: current.cookEndTime, done: !!current.cookEndTime },
    { label: '交付', time: current.deliverTime, done: !!current.deliverTime }
  ]
})

async function openDetail(row) {
  if (!row?.id) return
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const data = await getDecoction(row.id)
    detail.value = data || row
  } catch {
    /* 错误提示由 request 拦截器统一处理，保留列表行数据兜底展示 */
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.decoction-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-filter {
  margin-bottom: 12px;
  overflow-x: auto;
}

.count-badge {
  margin-left: 4px;
  font-variant-numeric: tabular-nums;
  opacity: 0.82;
}

.decoction-table {
  width: 100%;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.deliver-patient {
  margin-left: 10px;
}

.drawer-body {
  min-height: 200px;
}

.cancel-alert {
  margin-bottom: 12px;
}

.detail-desc {
  margin-bottom: 14px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  padding-left: 8px;
  border-left: 3px solid #1f8a6d;
  line-height: 1.1;
  margin: 14px 0 10px;
}

.timeline {
  padding-left: 2px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
