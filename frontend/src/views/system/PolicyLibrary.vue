<template>
  <div class="policy-page">
    <div class="page-card">
      <div class="page-title">医保政策库</div>
      <div class="policy-note">
        <el-icon><InfoFilled /></el-icon>
        <span>
          政策以官方发布为准，系统只做结构化登记与到期提醒，<b>不自动抓取</b>；
          各地经办机构执行细则不同，上线与执行前请以当地医保部门正式文件为准。
        </span>
      </div>

      <!-- 到期提醒 -->
      <div v-loading="reminderLoading" class="reminder-area">
        <el-alert v-if="reminders.effectiveSoon.length" type="warning" :closable="false" show-icon class="reminder-alert">
          <template #title>
            即将生效（30 天内）：{{ reminders.effectiveSoon.length }} 条政策
          </template>
          <div class="reminder-list">
            <span v-for="(item, index) in reminders.effectiveSoon" :key="reminderKey(item, `effective-${index}`)"
              class="reminder-item" :class="{ clickable: !!reminderId(item) }" @click="onReminderClick(item)">
              {{ reminderTitle(item) }}
              <span class="reminder-date">{{ reminderDate(item, 'effective') }}{{ reminderDays(item) }}</span>
            </span>
          </div>
        </el-alert>

        <el-alert v-if="reminders.expireSoon.length" type="error" :closable="false" show-icon class="reminder-alert">
          <template #title>
            即将失效（30 天内）：{{ reminders.expireSoon.length }} 条政策，请及时评估并调整相关参数
          </template>
          <div class="reminder-list">
            <span v-for="(item, index) in reminders.expireSoon" :key="reminderKey(item, `expire-${index}`)"
              class="reminder-item" :class="{ clickable: !!reminderId(item) }" @click="onReminderClick(item)">
              {{ reminderTitle(item) }}
              <span class="reminder-date">{{ reminderDate(item, 'expire') }}{{ reminderDays(item) }}</span>
            </span>
          </div>
        </el-alert>

        <el-alert v-if="!reminderLoading && !hasReminders" type="success" :closable="false" show-icon
          title="暂无 30 天内生效或失效的政策提醒" />
      </div>

      <!-- 查询栏 -->
      <div class="page-toolbar query-bar">
        <el-select v-model="query.category" class="filter-category" clearable placeholder="全部分类">
          <el-option v-for="(text, key) in POLICY_CATEGORY_TEXT" :key="key" :label="text" :value="key" />
        </el-select>
        <el-select v-model="query.status" class="filter-status" clearable placeholder="全部状态">
          <el-option v-for="(text, key) in POLICY_STATUS_TEXT" :key="key" :label="text" :value="key" />
        </el-select>
        <el-input v-model="query.keyword" class="filter-keyword" clearable placeholder="标题 / 发文机关 / 文号"
          @keyup.enter="onSearch" @clear="onSearch">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="onSearch">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="onReset">
          <el-icon><RefreshLeft /></el-icon>
          <span>重置</span>
        </el-button>
        <div class="flex-spacer" />
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增政策</span>
        </el-button>
      </div>

      <!-- 政策列表 -->
      <el-table v-loading="loading" :data="list" border stripe class="policy-table" empty-text="暂无政策登记">
        <el-table-column label="标题" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" class="title-link" @click="openDetail(row.id, row)">
              {{ row.title || '-' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="发文机关" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.issuer || '-' }}</template>
        </el-table-column>
        <el-table-column label="文号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.docNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="categoryTag(row.category)">
              {{ POLICY_CATEGORY_TEXT[row.category] || row.category || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布日期" width="118">
          <template #default="{ row }">{{ formatDate(row.publishDate) }}</template>
        </el-table-column>
        <el-table-column label="生效日期" width="118">
          <template #default="{ row }">{{ formatDate(row.effectiveDate) }}</template>
        </el-table-column>
        <el-table-column label="失效日期" width="118">
          <template #default="{ row }">
            <span :class="{ 'expire-soon': isExpireSoon(row) }">{{ formatDate(row.expireDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row)">{{ statusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="80" description="暂无政策登记，点击右上角「新增政策」开始登记" />
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background
          @size-change="onSizeChange" @current-change="loadList" />
      </div>
    </div>

    <!-- 新增 / 编辑政策 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑政策' : '新增政策'" width="780px"
      :close-on-click-modal="false" top="6vh" @closed="onDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="政策标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="请输入政策标题" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发文机关" prop="issuer">
              <el-input v-model="form.issuer" maxlength="100" placeholder="如：国家医疗保障局" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文号" prop="docNo">
              <el-input v-model="form.docNo" maxlength="80" placeholder="如：医保发〔2026〕1号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" class="full-width" placeholder="请选择分类">
                <el-option v-for="(text, key) in POLICY_CATEGORY_TEXT" :key="key" :label="text" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布日期" prop="publishDate">
              <el-date-picker v-model="form.publishDate" type="date" value-format="YYYY-MM-DD" clearable
                placeholder="选择发布日期" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" clearable
                placeholder="选择生效日期" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效日期" prop="expireDate">
              <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" clearable
                placeholder="长期有效可留空" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="用一两句话概括政策要点，便于列表快速浏览" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="12" maxlength="20000"
            placeholder="粘贴政策正文或关键条款摘录（建议保留原文表述，便于对照检查）" />
        </el-form-item>
        <el-form-item label="来源链接" prop="sourceUrl">
          <el-input v-model="form.sourceUrl" maxlength="500" clearable
            placeholder="官方发布页地址，如 https://www.nhsa.gov.cn/..." />
        </el-form-item>
      </el-form>
      <div class="dialog-tip text-muted">
        政策状态由日期自动判定（未生效为草稿、已生效为生效中、过期失效）；修改记录会写入操作审计。
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 政策详情抽屉 -->
    <el-drawer v-model="detailVisible" title="政策详情" size="620px" direction="rtl">
      <div v-loading="detailLoading" class="detail-wrap">
        <template v-if="detail">
          <div class="detail-title">{{ detail.title || '-' }}</div>
          <div class="detail-tag-row">
            <el-tag size="small" :type="statusTag(detail)">{{ statusText(detail) }}</el-tag>
            <el-tag size="small" effect="plain" :type="categoryTag(detail.category)">
              {{ POLICY_CATEGORY_TEXT[detail.category] || detail.category || '-' }}
            </el-tag>
          </div>

          <el-descriptions :column="2" border size="small" class="detail-desc">
            <el-descriptions-item label="发文机关">{{ detail.issuer || '-' }}</el-descriptions-item>
            <el-descriptions-item label="文号">{{ detail.docNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发布日期">{{ formatDate(detail.publishDate) }}</el-descriptions-item>
            <el-descriptions-item label="生效日期">{{ formatDate(detail.effectiveDate) }}</el-descriptions-item>
            <el-descriptions-item label="失效日期">{{ formatDate(detail.expireDate) }}</el-descriptions-item>
            <el-descriptions-item label="登记时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-section-title">摘要</div>
          <div class="detail-summary">{{ detail.summary || '未登记摘要' }}</div>

          <div class="detail-section-title">正文</div>
          <div class="detail-content">{{ detail.content || '未登记正文' }}</div>

          <div class="detail-section-title">来源链接</div>
          <div class="detail-source">
            <a v-if="detail.sourceUrl" :href="detail.sourceUrl" target="_blank" rel="noopener noreferrer">
              {{ detail.sourceUrl }}
            </a>
            <span v-else class="text-muted">未登记来源链接</span>
          </div>
          <div class="detail-foot text-muted">政策原文以官方发布为准，本系统仅登记摘要与链接，不作为合规依据。</div>
        </template>
        <el-empty v-else description="暂无政策详情" />
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="editFromDetail">编辑此政策</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled, Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import {
  createPolicy,
  deletePolicy,
  getPolicy,
  pagePolicies,
  policyReminders,
  updatePolicy
} from '@/api'
import { formatDate, formatDateTime, todayStr } from '@/utils/format'

/* ================= 常量 ================= */
const POLICY_CATEGORY_TEXT = {
  TRACE: '追溯码',
  SETTLEMENT: '结算',
  CATALOG: '目录',
  FEE: '价格',
  SUPERVISION: '监管',
  OTHER: '其他'
}

const POLICY_CATEGORY_TAG = {
  TRACE: 'primary',
  SETTLEMENT: 'success',
  CATALOG: 'warning',
  FEE: 'danger',
  SUPERVISION: 'info',
  OTHER: 'info'
}

const POLICY_STATUS_TEXT = {
  DRAFT: '草稿',
  EFFECTIVE: '生效中',
  EXPIRED: '已失效'
}

const POLICY_STATUS_TAG = {
  DRAFT: 'info',
  EFFECTIVE: 'success',
  EXPIRED: 'danger'
}

function categoryTag(category) {
  return POLICY_CATEGORY_TAG[category] || 'info'
}

/* 状态：优先使用后端 status，缺失时按日期推导 */
function statusOf(row) {
  if (row && row.status && POLICY_STATUS_TEXT[row.status]) return row.status
  const today = todayStr()
  const expire = row?.expireDate ? String(row.expireDate).slice(0, 10) : ''
  const effective = row?.effectiveDate ? String(row.effectiveDate).slice(0, 10) : ''
  if (expire && expire < today) return 'EXPIRED'
  if (effective && effective <= today) return 'EFFECTIVE'
  return 'DRAFT'
}

function statusText(row) {
  const status = statusOf(row)
  return POLICY_STATUS_TEXT[status] || status || '-'
}

function statusTag(row) {
  return POLICY_STATUS_TAG[statusOf(row)] || 'info'
}

/* 失效日期在 30 天内 → 高亮 */
function isExpireSoon(row) {
  if (!row?.expireDate) return false
  const expire = String(row.expireDate).slice(0, 10)
  const today = todayStr()
  if (expire < today) return false
  const diff = (new Date(`${expire}T00:00:00`).getTime() - new Date(`${today}T00:00:00`).getTime()) / 86400000
  return diff >= 0 && diff <= 30
}

/* ================= 到期提醒 ================= */
const reminderLoading = ref(false)
const reminders = reactive({ effectiveSoon: [], expireSoon: [] })

function normalizeReminderArray(value) {
  if (!value) return []
  if (Array.isArray(value)) return value.filter(Boolean)
  if (Array.isArray(value.list)) return value.list.filter(Boolean)
  if (Array.isArray(value.items)) return value.items.filter(Boolean)
  if (Array.isArray(value.records)) return value.records.filter(Boolean)
  return []
}

function splitReminderArray(list) {
  const effectiveSoon = []
  const expireSoon = []
  for (const item of list) {
    const marker = String(item?.reminderType ?? item?.type ?? item?.kind ?? item?.tag ?? '').toUpperCase()
    if (marker.includes('EXPIR')) {
      expireSoon.push(item)
    } else {
      effectiveSoon.push(item)
    }
  }
  return { effectiveSoon, expireSoon }
}

/* 兼容两种返回：{effectiveSoon, expireSoon} 或数组（数组按 type 字段拆分） */
function normalizeReminders(data) {
  if (data && !Array.isArray(data) && typeof data === 'object') {
    const hasKeys =
      data.effectiveSoon !== undefined ||
      data.expireSoon !== undefined ||
      data.effective !== undefined ||
      data.expiringSoon !== undefined ||
      data.expiring !== undefined ||
      data.expiredSoon !== undefined
    if (hasKeys) {
      return {
        effectiveSoon: normalizeReminderArray(data.effectiveSoon ?? data.effective ?? data.effectSoon),
        expireSoon: normalizeReminderArray(
          data.expireSoon ?? data.expiringSoon ?? data.expiring ?? data.expiredSoon
        )
      }
    }
    const nested = normalizeReminderArray(data)
    if (nested.length) return splitReminderArray(nested)
    return { effectiveSoon: [], expireSoon: [] }
  }
  return splitReminderArray(normalizeReminderArray(data))
}

async function loadReminders() {
  reminderLoading.value = true
  try {
    const data = await policyReminders()
    const normalized = normalizeReminders(data)
    reminders.effectiveSoon = normalized.effectiveSoon
    reminders.expireSoon = normalized.expireSoon
  } catch {
    reminders.effectiveSoon = []
    reminders.expireSoon = []
  } finally {
    reminderLoading.value = false
  }
}

const hasReminders = computed(
  () => reminders.effectiveSoon.length > 0 || reminders.expireSoon.length > 0
)

function reminderId(item) {
  if (!item) return null
  return item.id ?? item.policyId ?? item.policy_id ?? null
}

function reminderKey(item, fallback) {
  return reminderId(item) ?? `${fallback}-${item?.title || item?.policyTitle || JSON.stringify(item)}`
}

function reminderTitle(item) {
  return (
    item?.title ||
    item?.policyTitle ||
    item?.name ||
    (reminderId(item) ? `政策 #${reminderId(item)}` : '未命名政策')
  )
}

function reminderDate(item, kind) {
  const raw = kind === 'effective'
    ? item?.effectiveDate ?? item?.startDate ?? item?.date ?? item?.remindDate
    : item?.expireDate ?? item?.expiryDate ?? item?.endDate ?? item?.date ?? item?.remindDate
  return formatDate(raw)
}

function reminderDays(item) {
  const raw = item?.daysLeft ?? item?.remainingDays ?? item?.days
  if (raw === null || raw === undefined || raw === '') return ''
  const num = Number(raw)
  if (!Number.isFinite(num)) return ''
  if (num < 0) return `（已逾期 ${Math.abs(num)} 天）`
  return `（剩余 ${num} 天）`
}

function onReminderClick(item) {
  const id = reminderId(item)
  if (id) openDetail(id, item)
}

/* ================= 列表 ================= */
const query = reactive({ keyword: '', category: '', status: '' })
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    const keyword = query.keyword.trim()
    if (keyword) params.keyword = keyword
    if (query.category) params.category = query.category
    if (query.status) params.status = query.status
    const data = await pagePolicies(params)
    const rows = Array.isArray(data) ? data : Array.isArray(data?.list) ? data.list : []
    list.value = rows
    total.value = Array.isArray(data) ? rows.length : Number(data?.total ?? 0)
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function onReset() {
  query.keyword = ''
  query.category = ''
  query.status = ''
  page.value = 1
  loadList()
}

function onSizeChange() {
  page.value = 1
  loadList()
}

/* ================= 新增 / 编辑 ================= */
const dialogVisible = ref(false)
const editingId = ref(null)
const editingStatus = ref('DRAFT')
const submitting = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  title: '',
  issuer: '',
  docNo: '',
  category: 'TRACE',
  publishDate: '',
  effectiveDate: '',
  expireDate: '',
  summary: '',
  content: '',
  sourceUrl: ''
})

const form = reactive(defaultForm())

function validateSourceUrl(rule, value, callback) {
  const text = String(value ?? '').trim()
  if (!text) {
    callback()
    return
  }
  if (!/^https?:\/\/\S+$/i.test(text)) {
    callback(new Error('来源链接需以 http:// 或 https:// 开头'))
    return
  }
  callback()
}

function validateExpireDate(rule, value, callback) {
  if (value && form.effectiveDate && value < form.effectiveDate) {
    callback(new Error('失效日期不能早于生效日期'))
    return
  }
  callback()
}

const rules = {
  title: [{ required: true, message: '请输入政策标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择政策分类', trigger: 'change' }],
  expireDate: [{ validator: validateExpireDate, trigger: 'change' }],
  sourceUrl: [{ validator: validateSourceUrl, trigger: 'blur' }]
}

/* 按日期推导状态；无日期时沿用原状态 */
function deriveStatus(source, fallback = 'DRAFT') {
  const today = todayStr()
  const expire = source.expireDate ? String(source.expireDate).slice(0, 10) : ''
  const effective = source.effectiveDate ? String(source.effectiveDate).slice(0, 10) : ''
  if (expire && expire < today) return 'EXPIRED'
  if (effective && effective <= today) return 'EFFECTIVE'
  if (!effective && !expire) return fallback
  return 'DRAFT'
}

function openCreate() {
  editingId.value = null
  editingStatus.value = 'DRAFT'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function openEdit(row) {
  if (!row) return
  let source = row
  try {
    const data = await getPolicy(row.id)
    if (data) source = { ...row, ...data }
  } catch {
    /* 取详情失败时使用列表行数据，错误提示由拦截器处理 */
  }
  editingId.value = source.id ?? row.id
  editingStatus.value = statusOf(source)
  Object.assign(form, {
    title: source.title || '',
    issuer: source.issuer || '',
    docNo: source.docNo || '',
    category: source.category || 'OTHER',
    publishDate: source.publishDate ? String(source.publishDate).slice(0, 10) : '',
    effectiveDate: source.effectiveDate ? String(source.effectiveDate).slice(0, 10) : '',
    expireDate: source.expireDate ? String(source.expireDate).slice(0, 10) : '',
    summary: source.summary || '',
    content: source.content || '',
    sourceUrl: source.sourceUrl || ''
  })
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function onDialogClosed() {
  editingId.value = null
  editingStatus.value = 'DRAFT'
  formRef.value?.resetFields()
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.expireDate && form.effectiveDate && form.expireDate < form.effectiveDate) {
    ElMessage.warning('失效日期不能早于生效日期')
    return
  }
  submitting.value = true
  const payload = {
    title: form.title.trim(),
    issuer: form.issuer.trim() || null,
    docNo: form.docNo.trim() || null,
    category: form.category,
    publishDate: form.publishDate || null,
    effectiveDate: form.effectiveDate || null,
    expireDate: form.expireDate || null,
    summary: form.summary.trim() || null,
    content: form.content.trim() || null,
    sourceUrl: form.sourceUrl.trim() || null,
    status: deriveStatus(form, editingStatus.value)
  }
  try {
    if (editingId.value) {
      await updatePolicy(editingId.value, payload)
      ElMessage.success('政策已更新')
    } else {
      await createPolicy(payload)
      ElMessage.success('政策已新增')
      page.value = 1
    }
    dialogVisible.value = false
    await Promise.all([loadList(), loadReminders()])
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    submitting.value = false
  }
}

/* ================= 删除 ================= */
async function onDelete(row) {
  if (!row) return
  try {
    await ElMessageBox.confirm(
      `确定删除政策「${row.title || '-'}」吗？删除后不可恢复，也不利于留存检查。建议改为将状态调整为「已失效」，保留完整登记记录。`,
      '删除政策',
      {
        type: 'warning',
        confirmButtonText: '仍要删除',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true
      }
    )
  } catch {
    return
  }
  try {
    await deletePolicy(row.id)
    ElMessage.success('政策已删除')
    if (list.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await Promise.all([loadList(), loadReminders()])
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  }
}

/* ================= 详情 ================= */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function openDetail(id, fallback) {
  if (!id) {
    if (fallback) {
      detail.value = fallback
      detailVisible.value = true
    }
    return
  }
  detail.value = fallback || null
  detailVisible.value = true
  detailLoading.value = true
  try {
    const data = await getPolicy(id)
    if (data) {
      detail.value = { ...(fallback || {}), ...data }
    }
  } catch {
    /* 详情失败时保留列表行数据 */
  } finally {
    detailLoading.value = false
  }
}

function editFromDetail() {
  if (!detail.value) return
  const row = detail.value
  detailVisible.value = false
  openEdit(row)
}

/* ================= 生命周期 ================= */
onMounted(() => {
  loadReminders()
  loadList()
})
</script>

<style scoped>
.policy-page {
  min-height: calc(100vh - 88px);
}

.policy-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: -4px 0 14px;
  padding: 10px 12px;
  border-radius: 4px;
  background: #f4f8ff;
  color: #606266;
  font-size: 13px;
  line-height: 1.65;
}

.policy-note .el-icon {
  margin-top: 2px;
  color: #409eff;
  flex-shrink: 0;
}

.reminder-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
  min-height: 8px;
}

.reminder-alert {
  padding: 8px 12px;
}

.reminder-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
}

.reminder-item {
  font-size: 13px;
  line-height: 1.5;
  color: #303133;
  cursor: default;
}

.reminder-item.clickable {
  cursor: pointer;
  color: #1f6feb;
}

.reminder-item.clickable:hover {
  text-decoration: underline;
}

.reminder-date {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}

.query-bar {
  margin-bottom: 12px;
}

.filter-category {
  width: 140px;
}

.filter-status {
  width: 130px;
}

.filter-keyword {
  width: 260px;
}

.flex-spacer {
  flex: 1;
}

.policy-table {
  width: 100%;
}

.title-link {
  font-weight: 600;
  text-align: left;
  white-space: normal;
  line-height: 1.4;
  padding: 0;
  height: auto;
}

.title-link:hover {
  text-decoration: underline;
}

.expire-soon {
  color: #e6a23c;
  font-weight: 600;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.full-width {
  width: 100%;
}

.dialog-tip {
  padding: 0 4px 4px 100px;
  line-height: 1.6;
}

/* ---------- 详情抽屉 ---------- */
.detail-wrap {
  min-height: 200px;
  padding-bottom: 12px;
}

.detail-title {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.5;
  color: #303133;
  margin-bottom: 10px;
  word-break: break-word;
}

.detail-tag-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.detail-desc {
  margin-bottom: 16px;
}

.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-left: 8px;
  border-left: 3px solid #1f8a6d;
  line-height: 1.15;
  margin: 16px 0 8px;
}

.detail-summary {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
  color: #303133;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-content {
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #f8f9fa;
  color: #303133;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 46vh;
  overflow: auto;
}

.detail-source {
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

.detail-source a {
  color: #409eff;
  text-decoration: none;
}

.detail-source a:hover {
  text-decoration: underline;
}

.detail-foot {
  margin-top: 18px;
  line-height: 1.6;
  border-top: 1px dashed #ebeef5;
  padding-top: 10px;
}
</style>
