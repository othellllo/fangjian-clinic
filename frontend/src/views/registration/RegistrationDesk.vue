<template>
  <div class="registration-desk">
    <div class="page-card desk-head">
      <div class="head-left">
        <span class="head-date">今日 {{ dateText }}</span>
        <span class="text-muted">挂号 · 候诊 · 就诊管理</span>
      </div>
      <el-button :icon="Refresh" plain :loading="refreshing" @click="refreshAll">刷新</el-button>
    </div>

    <el-row :gutter="12" class="top-row">
      <!-- 患者 -->
      <el-col :xs="24" :md="14">
        <div class="page-card panel">
          <div class="section-head">
            <span class="section-title">患者</span>
            <el-button type="primary" plain size="small" :icon="Plus" @click="openCreatePatient">
              新建患者
            </el-button>
          </div>
          <div class="page-toolbar">
            <el-input v-model="patientKeyword" placeholder="姓名 / 手机号 / 病历号" clearable style="width: 260px"
              :prefix-icon="Search" @keyup.enter="searchPatients" @clear="searchPatients" />
            <el-button type="primary" @click="searchPatients">查询</el-button>
          </div>
          <el-table v-loading="patientLoading" :data="patients" size="small" height="270"
            :row-class-name="patientRowClass" @row-click="selectPatient">
            <el-table-column prop="name" label="姓名" width="88" />
            <el-table-column label="性别" width="60">
              <template #default="{ row }">{{ GENDER_TEXT[row.gender] || '-' }}</template>
            </el-table-column>
            <el-table-column label="年龄" width="72">
              <template #default="{ row }">{{ row.age != null ? `${row.age}岁` : '-' }}</template>
            </el-table-column>
            <el-table-column label="手机号" width="124">
              <template #default="{ row }">{{ row.phone || '-' }}</template>
            </el-table-column>
            <el-table-column prop="patientNo" label="病历号" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="82" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="selectPatient(row)">选中</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="60" description="没有找到患者，可新建建档" />
            </template>
          </el-table>
          <div class="pager">
            <el-pagination v-model:current-page="patientPage" :page-size="patientSize" :total="patientTotal"
              layout="total, prev, pager, next" small @current-change="loadPatients" />
          </div>
        </div>
      </el-col>

      <!-- 挂号 -->
      <el-col :xs="24" :md="10">
        <div class="page-card panel register-panel">
          <div class="section-head">
            <span class="section-title">挂号</span>
          </div>

          <div class="selected-patient" :class="{ 'is-empty': !selectedPatient }">
            <template v-if="selectedPatient">
              <div class="sp-line">
                <span class="sp-name">{{ selectedPatient.name }}</span>
                <el-tag size="small" effect="plain">{{ GENDER_TEXT[selectedPatient.gender] || '-' }}</el-tag>
                <span class="text-muted">
                  {{ selectedPatient.age != null ? `${selectedPatient.age} 岁` : '年龄未知' }}
                </span>
                <el-button link type="primary" size="small" class="sp-clear" @click="selectedPatient = null">
                  取消选择
                </el-button>
              </div>
              <div class="sp-meta text-muted">
                病历号 {{ selectedPatient.patientNo || '-' }} · {{ selectedPatient.phone || '未留手机号' }}
              </div>
              <div v-if="selectedPatient.allergy" class="sp-allergy">
                <el-tag type="danger" size="small" effect="light">过敏：{{ selectedPatient.allergy }}</el-tag>
              </div>
            </template>
            <div v-else class="sp-empty">
              <el-icon><User /></el-icon>
              <span>请从左侧患者列表选择患者</span>
            </div>
          </div>

          <el-form label-position="top" class="register-form">
            <el-form-item label="就诊医生" required>
              <el-select v-model="visitForm.doctorId" placeholder="请选择医生" filterable style="width: 100%">
                <el-option v-for="d in doctors" :key="d.id" :label="doctorLabel(d)" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="就诊类型">
              <el-radio-group v-model="visitForm.type">
                <el-radio-button label="FIRST">首诊</el-radio-button>
                <el-radio-button label="FOLLOWUP">复诊</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="主诉">
              <el-input v-model="visitForm.complaint" type="textarea" :rows="3" maxlength="200" show-word-limit
                placeholder="如：咳嗽 3 天，痰黄" />
            </el-form-item>
          </el-form>

          <el-button type="primary" size="large" class="register-btn" :loading="registering" @click="submitRegister">
            确认挂号
          </el-button>
        </div>
      </el-col>
    </el-row>

    <!-- 今日已挂号 / 候诊队列 -->
    <div class="page-card tabs-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane name="visits">
          <template #label>
            今日已挂号
            <el-tag size="small" type="info" effect="plain" class="tab-count">{{ visitTotal }}</el-tag>
          </template>
          <el-table v-loading="visitLoading" :data="visits" size="small" height="360">
            <el-table-column label="排队号" width="86">
              <template #default="{ row }">
                <span class="queue-no">{{ row.queueNo ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="patientName" label="患者" min-width="110" show-overflow-tooltip />
            <el-table-column prop="doctorName" label="医生" min-width="110" show-overflow-tooltip />
            <el-table-column label="类型" width="82">
              <template #default="{ row }">{{ VISIT_TYPE_TEXT[row.type] || row.type || '-' }}</template>
            </el-table-column>
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
            <el-table-column label="挂号费" width="104" align="right">
              <template #default="{ row }">
                <span class="money">¥ {{ formatMoney(row.registerFee) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canCancel(row)" link type="danger" size="small" @click="onCancelVisit(row)">
                  作废
                </el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="70" description="今日还没有挂号记录" />
            </template>
          </el-table>
          <div class="pager">
            <el-pagination v-model:current-page="visitPage" :page-size="visitSize" :total="visitTotal"
              layout="total, prev, pager, next" @current-change="loadVisits" />
          </div>
        </el-tab-pane>

        <el-tab-pane name="queue">
          <template #label>
            候诊队列
            <el-tag size="small" type="warning" effect="plain" class="tab-count">{{ queue.length }}</el-tag>
          </template>
          <div class="page-toolbar">
            <el-select v-model="queueDoctorId" placeholder="全部医生" clearable style="width: 220px"
              @change="loadQueue">
              <el-option v-for="d in doctors" :key="d.id" :label="doctorLabel(d)" :value="d.id" />
            </el-select>
            <el-button :icon="Refresh" @click="loadQueue">刷新队列</el-button>
            <span class="text-muted">共 {{ queue.length }} 人候诊</span>
          </div>
          <div v-loading="queueLoading" class="queue-wrap">
            <div v-if="queue.length" class="queue-grid">
              <div v-for="item in queue" :key="item.id" class="queue-card" :class="`is-${String(item.status || '').toLowerCase()}`">
                <div class="qc-no">{{ item.queueNo ?? '-' }}</div>
                <div class="qc-body">
                  <div class="qc-name">
                    {{ item.patientName || '-' }}
                    <span class="text-muted qc-patient-meta">
                      {{ GENDER_TEXT[item.patientGender] || '' }}
                      {{ item.patientAge != null ? `${item.patientAge}岁` : '' }}
                    </span>
                  </div>
                  <div class="text-muted qc-meta">
                    {{ item.doctorName || '-' }} · {{ VISIT_TYPE_TEXT[item.type] || item.type || '-' }}
                  </div>
                  <div class="text-muted qc-meta">挂号 {{ timeText(item.registerTime) }}</div>
                </div>
                <el-tag size="small" :type="VISIT_STATUS_TAG[item.status] || 'info'">
                  {{ VISIT_STATUS_TEXT[item.status] || item.status }}
                </el-tag>
              </div>
            </div>
            <div v-else class="empty-hint">
              <el-empty :image-size="70" description="当前没有候诊患者" />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane name="appointments">
          <template #label>
            今日预约
            <el-tag size="small" type="success" effect="plain" class="tab-count">{{ appointmentTotal }}</el-tag>
          </template>
          <div class="page-toolbar">
            <el-button :icon="Refresh" :loading="appointmentLoading" @click="loadAppointments">刷新预约</el-button>
            <span class="text-muted">仅显示今日（{{ today }}）的员工侧预约；到店报到后自动生成就诊记录</span>
          </div>
          <el-table v-loading="appointmentLoading" :data="appointments" size="small" height="360">
            <el-table-column prop="appointNo" label="预约号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="patientName" label="患者" min-width="110" show-overflow-tooltip />
            <el-table-column label="手机号" width="124">
              <template #default="{ row }">{{ row.patientPhone || '-' }}</template>
            </el-table-column>
            <el-table-column prop="doctorName" label="医生" min-width="110" show-overflow-tooltip />
            <el-table-column label="时段" width="120">
              <template #default="{ row }">{{ row.timeSlot || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="94">
              <template #default="{ row }">
                <el-tag size="small" :type="APPOINTMENT_STATUS_TAG[row.status] || 'info'">
                  {{ APPOINTMENT_STATUS_TEXT[row.status] || row.status || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="94">
              <template #default="{ row }">
                {{ APPOINTMENT_SOURCE_TEXT[row.source] || row.source || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'BOOKED'"
                  link
                  type="primary"
                  size="small"
                  :loading="arrivingId === row.id"
                  @click="onArrive(row)"
                >
                  到店报到
                </el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="70" description="今日暂无预约" />
            </template>
          </el-table>
          <div class="pager">
            <el-pagination v-model:current-page="appointmentPage" :page-size="appointmentSize"
              :total="appointmentTotal" layout="total, prev, pager, next" @current-change="loadAppointments" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新建患者 -->
    <el-dialog v-model="patientDialogVisible" title="新建患者" width="580px" :close-on-click-modal="false"
      @closed="resetPatientForm">
      <el-form ref="patientFormRef" v-loading="patientSubmitting" :model="patientForm" :rules="patientRules"
        label-width="82px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="patientForm.name" placeholder="请输入姓名" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="patientForm.gender">
                <el-radio label="MALE">男</el-radio>
                <el-radio label="FEMALE">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出生日期">
              <el-date-picker v-model="patientForm.birthDate" type="date" value-format="YYYY-MM-DD"
                placeholder="选择日期" style="width: 100%" :disabled-date="disableFutureDate" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="patientForm.phone" placeholder="11 位手机号" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="身份证" prop="idCard">
              <el-input v-model="patientForm.idCard" placeholder="选填" maxlength="18" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="patientForm.address" placeholder="选填" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="过敏史">
              <el-input v-model="patientForm.allergy" type="textarea" :rows="2" maxlength="200" show-word-limit
                placeholder="如：青霉素过敏；无则不填" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="既往史">
              <el-input v-model="patientForm.chronicHistory" type="textarea" :rows="2" maxlength="200"
                show-word-limit placeholder="如：高血压 5 年" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="patientDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="patientSubmitting" @click="submitPatient">保存并选中</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, User } from '@element-plus/icons-vue'
import { createPatient, cancelVisit, getQueue, listDoctors, pageAppointments, pagePatients, pageVisits, registerVisit, arriveAppointment } from '@/api'
import {
  formatDateTime,
  formatMoney,
  GENDER_TEXT,
  todayStr,
  VISIT_STATUS_TAG,
  VISIT_STATUS_TEXT
} from '@/utils/format'

const VISIT_TYPE_TEXT = { FIRST: '首诊', FOLLOWUP: '复诊' }

/* ---------------- 预约（员工侧） ---------------- */
const APPOINTMENT_STATUS_TEXT = { BOOKED: '已预约', ARRIVED: '已到店', CANCELLED: '已取消' }
const APPOINTMENT_STATUS_TAG = { BOOKED: 'primary', ARRIVED: 'success', CANCELLED: 'info' }
const APPOINTMENT_SOURCE_TEXT = {
  MINIAPP: '小程序',
  WECHAT: '微信',
  STAFF: '前台',
  PHONE: '电话',
  ONLINE: '线上'
}

const today = todayStr()
const dateText = computed(() => {
  const d = new Date()
  const week = '日一二三四五六'[d.getDay()]
  return `${today} 星期${week}`
})

/* ---------------- 患者 ---------------- */
const patients = ref([])
const patientKeyword = ref('')
const patientPage = ref(1)
const patientSize = ref(5)
const patientTotal = ref(0)
const patientLoading = ref(false)
const selectedPatient = ref(null)

const patientRowClass = ({ row }) =>
  selectedPatient.value && selectedPatient.value.id === row.id ? 'selected-row' : ''

async function loadPatients() {
  patientLoading.value = true
  try {
    const data = await pagePatients({
      keyword: patientKeyword.value.trim(),
      page: patientPage.value,
      size: patientSize.value
    })
    patients.value = data?.list || []
    patientTotal.value = data?.total || 0
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    patientLoading.value = false
  }
}

function searchPatients() {
  patientPage.value = 1
  loadPatients()
}

function selectPatient(row) {
  if (!row) return
  selectedPatient.value = row
}

/* ---------------- 医生 / 挂号 ---------------- */
const doctors = ref([])
const visitForm = reactive({ doctorId: null, type: 'FIRST', complaint: '' })
const registering = ref(false)

function doctorLabel(d) {
  return d.title ? `${d.realName}·${d.title}` : d.realName
}

async function loadDoctors() {
  try {
    const data = await listDoctors()
    doctors.value = Array.isArray(data) ? data : data?.list || []
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  }
}

async function submitRegister() {
  if (!selectedPatient.value) {
    ElMessage.warning('请先选择患者')
    return
  }
  const patientId = selectedPatient.value.id
  if (!visitForm.doctorId) {
    ElMessage.warning('请选择就诊医生')
    return
  }
  registering.value = true
  try {
    const visit = await registerVisit({
      patientId,
      doctorId: visitForm.doctorId,
      type: visitForm.type,
      complaint: visitForm.complaint.trim()
    })
    ElMessage.success(
      `挂号成功，排队号 ${visit?.queueNo ?? '-'}，挂号费 ¥${formatMoney(visit?.registerFee)}`
    )
    selectedPatient.value = null
    visitForm.complaint = ''
    await Promise.all([loadVisits(), loadQueue()])
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    registering.value = false
  }
}

/* ---------------- 今日已挂号 ---------------- */
const activeTab = ref('visits')
const visits = ref([])
const visitLoading = ref(false)
const visitPage = ref(1)
const visitSize = ref(10)
const visitTotal = ref(0)

async function loadVisits() {
  visitLoading.value = true
  try {
    const data = await pageVisits({
      date: today,
      status: '',
      page: visitPage.value,
      size: visitSize.value
    })
    visits.value = data?.list || []
    visitTotal.value = data?.total || 0
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    visitLoading.value = false
  }
}

function canCancel(row) {
  return row.status === 'WAITING' || row.status === 'IN_SERVICE'
}

async function onCancelVisit(row) {
  try {
    await ElMessageBox.confirm(
      `确认作废「${row.patientName || '该患者'}」的挂号（排队号 ${row.queueNo ?? '-'}）？`,
      '作废挂号',
      { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await cancelVisit(row.id)
    ElMessage.success('挂号已作废')
    await Promise.all([loadVisits(), loadQueue()])
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  }
}

/* ---------------- 候诊队列 ---------------- */
const queue = ref([])
const queueLoading = ref(false)
const queueDoctorId = ref(null)

async function loadQueue() {
  queueLoading.value = true
  try {
    const params = {}
    if (queueDoctorId.value) params.doctorId = queueDoctorId.value
    const data = await getQueue(params)
    queue.value = Array.isArray(data) ? data : data?.list || []
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    queueLoading.value = false
  }
}

function timeText(value) {
  const text = formatDateTime(value)
  return text === '-' ? '-' : text.slice(11, 16)
}

/* ---------------- 今日预约 ---------------- */
const appointments = ref([])
const appointmentLoading = ref(false)
const appointmentPage = ref(1)
const appointmentSize = ref(10)
const appointmentTotal = ref(0)
const arrivingId = ref(null)

async function loadAppointments() {
  appointmentLoading.value = true
  try {
    const data = await pageAppointments({
      date: today,
      status: '',
      page: appointmentPage.value,
      size: appointmentSize.value
    })
    appointments.value = data?.list || []
    appointmentTotal.value = data?.total || 0
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    appointmentLoading.value = false
  }
}

async function onArrive(row) {
  if (!row || arrivingId.value) return
  arrivingId.value = row.id
  try {
    await arriveAppointment(row.id)
    ElMessage.success('已生成就诊记录')
    await Promise.all([loadAppointments(), loadVisits()])
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    arrivingId.value = null
  }
}

function onTabChange(name) {
  if (name === 'queue') loadQueue()
  else if (name === 'appointments') loadAppointments()
  else loadVisits()
}

/* ---------------- 新建患者 ---------------- */
const patientDialogVisible = ref(false)
const patientSubmitting = ref(false)
const patientFormRef = ref()

function emptyPatientForm() {
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

const patientForm = reactive(emptyPatientForm())

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

const patientRules = {
  name: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  phone: [{ validator: optionalPhone, trigger: 'blur' }],
  idCard: [{ validator: optionalIdCard, trigger: 'blur' }]
}

const disableFutureDate = (date) => date.getTime() > Date.now()

function openCreatePatient() {
  resetPatientForm()
  patientDialogVisible.value = true
}

function resetPatientForm() {
  Object.assign(patientForm, emptyPatientForm())
  patientFormRef.value?.clearValidate()
}

async function submitPatient() {
  try {
    await patientFormRef.value.validate()
  } catch {
    return
  }
  patientSubmitting.value = true
  try {
    const created = await createPatient({
      name: patientForm.name.trim(),
      gender: patientForm.gender,
      birthDate: patientForm.birthDate || null,
      phone: patientForm.phone || null,
      idCard: patientForm.idCard || null,
      address: patientForm.address.trim() || null,
      allergy: patientForm.allergy.trim() || null,
      chronicHistory: patientForm.chronicHistory.trim() || null
    })
    patientDialogVisible.value = false
    ElMessage.success('建档成功，已自动选中该患者')
    if (created) selectedPatient.value = created
    patientKeyword.value = ''
    patientPage.value = 1
    await loadPatients()
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    patientSubmitting.value = false
  }
}

/* ---------------- 刷新 ---------------- */
const refreshing = ref(false)

async function refreshAll() {
  refreshing.value = true
  await Promise.allSettled([loadDoctors(), loadPatients(), loadVisits(), loadQueue(), loadAppointments()])
  refreshing.value = false
}

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.registration-desk {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.desk-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.head-date {
  font-size: 15px;
  font-weight: 600;
}

.top-row {
  align-items: stretch;
}

.panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  padding-left: 8px;
  border-left: 3px solid #1f8a6d;
  line-height: 1.1;
}

.register-panel {
  color: #303133;
}

.register-form {
  flex: 1;
}

.register-btn {
  width: 100%;
  margin-top: 4px;
}

.selected-patient {
  padding: 10px 12px;
  margin-bottom: 14px;
  border-radius: 6px;
  background: #f0f8f4;
  border: 1px solid #d6ece1;
}

.selected-patient.is-empty {
  background: #f8f9fa;
  border: 1px dashed #dcdfe6;
}

.sp-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sp-name {
  font-size: 15px;
  font-weight: 600;
}

.sp-clear {
  margin-left: auto;
}

.sp-meta {
  margin-top: 4px;
}

.sp-allergy {
  margin-top: 6px;
}

.sp-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 40px;
  color: #909399;
  font-size: 13px;
}

.queue-no {
  font-weight: 700;
  color: #1f8a6d;
  font-variant-numeric: tabular-nums;
}

.tab-count {
  margin-left: 4px;
}

.tabs-card {
  padding-top: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.queue-wrap {
  min-height: 120px;
}

.queue-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 10px;
}

.queue-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-left: 4px solid #e6a23c;
  border-radius: 6px;
  background: #fff;
  transition: box-shadow 0.2s;
}

.queue-card.is-in_service {
  border-left-color: #409eff;
  background: #f4f9ff;
}

.qc-no {
  min-width: 44px;
  text-align: center;
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  color: #1f8a6d;
  font-variant-numeric: tabular-nums;
}

.qc-body {
  flex: 1;
  min-width: 0;
}

.qc-name {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.qc-patient-meta {
  font-weight: 400;
  margin-left: 4px;
}

.qc-meta {
  margin-top: 3px;
}

:deep(.el-table .selected-row > td) {
  background: #eaf7f1 !important;
}

@media (max-width: 992px) {
  .panel {
    height: auto;
  }

  .register-panel {
    margin-top: 12px;
  }
}
</style>
