<template>
  <div class="patient-list">
    <div class="page-card">
      <div class="page-toolbar">
        <el-input v-model="keyword" placeholder="姓名 / 手机号 / 病历号" clearable style="width: 280px"
          :prefix-icon="Search" @keyup.enter="onSearch" @clear="onSearch" />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <div class="toolbar-right">
          <el-button type="primary" plain :icon="Plus" @click="openCreate">新建患者</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" size="default">
        <el-table-column prop="patientNo" label="病历号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="name" label="姓名" width="110" show-overflow-tooltip />
        <el-table-column label="性别" width="72">
          <template #default="{ row }">{{ GENDER_TEXT[row.gender] || '-' }}</template>
        </el-table-column>
        <el-table-column label="年龄" width="80">
          <template #default="{ row }">{{ row.age != null ? `${row.age}岁` : '-' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="过敏史" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.allergy" type="danger" size="small" effect="light">{{ row.allergy }}</el-tag>
            <span v-else class="text-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="建档时间" width="164">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-hint">
            <el-empty :image-size="80" description="暂无患者档案，可点击「新建患者」建档" />
          </div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="onSizeChange" @current-change="loadList" />
      </div>
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" :close-on-click-modal="false"
      @closed="onDialogClosed">
      <el-form ref="formRef" v-loading="submitting" :model="form" :rules="rules" label-width="86px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="form.gender">
                <el-radio label="MALE">男</el-radio>
                <el-radio label="FEMALE">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出生日期">
              <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD"
                placeholder="选择日期" style="width: 100%" :disabled-date="disableFutureDate" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="身份证" prop="idCard">
              <el-input v-model="form.idCard" placeholder="选填，15 或 18 位" maxlength="18" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="form.address" placeholder="选填" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="过敏史">
              <el-input v-model="form.allergy" type="textarea" :rows="2" maxlength="200" show-word-limit
                placeholder="如：青霉素过敏；无则不填" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="既往史">
              <el-input v-model="form.chronicHistory" type="textarea" :rows="2" maxlength="200" show-word-limit
                placeholder="如：高血压 5 年" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="drawerVisible" title="患者详情" size="min(780px, 94vw)">
      <div v-loading="drawerLoading" class="drawer-body">
        <template v-if="detail">
          <div class="detail-head">
            <span class="detail-name">{{ detail.name }}</span>
            <el-tag size="small" effect="plain">{{ GENDER_TEXT[detail.gender] || '-' }}</el-tag>
            <span class="text-muted">{{ detail.age != null ? `${detail.age} 岁` : '年龄未知' }}</span>
            <el-tag v-if="detail.allergy" type="danger" size="small" effect="light">有过敏史</el-tag>
          </div>

          <div class="detail-section-title">基本信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="病历号">{{ detail.patientNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ formatDate(detail.birthDate) }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="身份证">{{ detail.idCard || '-' }}</el-descriptions-item>
            <el-descriptions-item label="会员卡">
              {{ detail.memberId ? `已开卡（ID ${detail.memberId}）` : '未开卡' }}
            </el-descriptions-item>
            <el-descriptions-item label="建档时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ detail.address || '-' }}</el-descriptions-item>
            <el-descriptions-item label="过敏史" :span="2">{{ detail.allergy || '无' }}</el-descriptions-item>
            <el-descriptions-item label="既往史" :span="2">{{ detail.chronicHistory || '无' }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-section-title">
            就诊记录
            <span class="text-muted">共 {{ detailVisits.length }} 次</span>
          </div>
          <el-table v-loading="visitsLoading" :data="detailVisits" size="small" border max-height="300">
            <el-table-column prop="visitNo" label="就诊号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="doctorName" label="医生" min-width="90" show-overflow-tooltip />
            <el-table-column label="状态" width="92">
              <template #default="{ row }">
                <el-tag size="small" :type="VISIT_STATUS_TAG[row.status] || 'info'">
                  {{ VISIT_STATUS_TEXT[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="挂号时间" width="164">
              <template #default="{ row }">{{ formatDateTime(row.registerTime) }}</template>
            </el-table-column>
            <el-table-column prop="complaint" label="主诉" min-width="160" show-overflow-tooltip />
            <template #empty>
              <el-empty :image-size="50" description="暂无就诊记录" />
            </template>
          </el-table>

          <template v-if="detailCharges.length">
            <div class="detail-section-title">
              历史收费
              <span class="text-muted">共 {{ detailCharges.length }} 笔</span>
            </div>
            <el-table :data="detailCharges" size="small" border max-height="240">
              <el-table-column prop="orderNo" label="收费单号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="visitNo" label="就诊号" min-width="140" show-overflow-tooltip />
              <el-table-column label="应收" width="110" align="right">
                <template #default="{ row }">
                  <span class="money">¥ {{ formatMoney(row.payableAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="92">
                <template #default="{ row }">
                  <el-tag size="small" :type="CHARGE_STATUS_TAG[row.status] || 'info'">
                    {{ CHARGE_STATUS_TEXT[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="164">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </template>

          <div class="drawer-actions">
            <el-button type="primary" plain @click="openEdit(detail)">编辑患者信息</el-button>
          </div>
        </template>
        <div v-else-if="!drawerLoading" class="empty-hint">
          <el-empty :image-size="60" description="未获取到患者信息" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createPatient, getPatient, getPatientVisits, pagePatients, updatePatient } from '@/api'
import {
  CHARGE_STATUS_TAG,
  CHARGE_STATUS_TEXT,
  formatDate,
  formatDateTime,
  formatMoney,
  GENDER_TEXT,
  VISIT_STATUS_TAG,
  VISIT_STATUS_TEXT
} from '@/utils/format'

/* ---------------- 列表 ---------------- */
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const data = await pagePatients({
      keyword: keyword.value.trim(),
      page: page.value,
      size: size.value
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function onReset() {
  keyword.value = ''
  page.value = 1
  size.value = 10
  loadList()
}

function onSizeChange() {
  page.value = 1
  loadList()
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref()

function emptyForm() {
  return {
    name: '',
    gender: 'MALE',
    birthDate: '',
    phone: '',
    idCard: '',
    address: '',
    allergy: '',
    chronicHistory: ''
  }
}

const form = reactive(emptyForm())

const dialogTitle = computed(() => (dialogMode.value === 'edit' ? '编辑患者' : '新建患者'))

const optionalPhone = (rule, value, callback) => {
  if (!value) return callback()
  if (!/^1[3-9]\d{9}$/.test(value)) return callback(new Error('手机号格式不正确'))
  return callback()
}

const optionalIdCard = (rule, value, callback) => {
  if (!value) return callback()
  if (!/(^\d{15}$)|(^\d{17}[\dXx]$)/.test(value)) return callback(new Error('身份证号格式不正确'))
  return callback()
}

const rules = {
  name: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  phone: [{ validator: optionalPhone, trigger: 'blur' }],
  idCard: [{ validator: optionalIdCard, trigger: 'blur' }]
}

const disableFutureDate = (date) => date.getTime() > Date.now()

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, emptyForm(), {
    name: row.name || '',
    gender: row.gender || 'MALE',
    birthDate: row.birthDate ? String(row.birthDate).slice(0, 10) : '',
    phone: row.phone || '',
    idCard: row.idCard || '',
    address: row.address || '',
    allergy: row.allergy || '',
    chronicHistory: row.chronicHistory || ''
  })
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function onDialogClosed() {
  editingId.value = null
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}

async function submitForm() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  const payload = {
    name: form.name.trim(),
    gender: form.gender,
    birthDate: form.birthDate || null,
    phone: form.phone || null,
    idCard: form.idCard || null,
    address: form.address.trim() || null,
    allergy: form.allergy.trim() || null,
    chronicHistory: form.chronicHistory.trim() || null
  }
  try {
    if (dialogMode.value === 'edit' && editingId.value) {
      await updatePatient(editingId.value, payload)
      ElMessage.success('患者信息已更新')
      if (drawerVisible.value && detail.value?.id === editingId.value) {
        loadDetail(editingId.value)
      }
    } else {
      await createPatient(payload)
      ElMessage.success('患者建档成功')
      page.value = 1
    }
    dialogVisible.value = false
    await loadList()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    submitting.value = false
  }
}

/* ---------------- 详情 ---------------- */
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const visitsLoading = ref(false)
const detail = ref(null)
const detailVisits = ref([])

async function loadDetail(id) {
  drawerLoading.value = true
  detail.value = null
  detailVisits.value = []
  try {
    const raw = await getPatient(id)
    detail.value = raw?.patient || raw || null
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    drawerLoading.value = false
  }
  visitsLoading.value = true
  try {
    const data = await getPatientVisits(id)
    detailVisits.value = Array.isArray(data) ? data : data?.list || []
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    visitsLoading.value = false
  }
}

function openDetail(row) {
  drawerVisible.value = true
  loadDetail(row.id)
}

/** 若就诊记录里带了收费单则展示历史收费，否则整块不显示 */
const detailCharges = computed(() => {
  const rows = []
  for (const visit of detailVisits.value) {
    const charges = visit?.charges || visit?.chargeOrders
    if (!Array.isArray(charges)) continue
    for (const charge of charges) {
      rows.push({ ...charge, visitNo: charge.visitNo || visit.visitNo })
    }
  }
  return rows
})

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.toolbar-right {
  margin-left: auto;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.detail-name {
  font-size: 18px;
  font-weight: 600;
}

.detail-section-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 20px 0 10px;
  font-size: 14px;
  font-weight: 600;
  padding-left: 8px;
  border-left: 3px solid #1f8a6d;
}

.drawer-actions {
  margin-top: 22px;
  padding-bottom: 8px;
}

:deep(.el-drawer__body) {
  padding-top: 8px;
}
</style>
