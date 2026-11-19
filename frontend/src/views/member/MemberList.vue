<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-input
        v-model="keyword"
        placeholder="姓名 / 手机号 / 会员号"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="toolbar-right">
        <el-button type="success" @click="openCreate">新建会员</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="memberNo" label="会员号" width="140" />
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="phone" label="手机号" width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="等级" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="levelTag(row.level)" size="small">{{ levelText(row.level) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="余额" width="120" align="right">
        <template #default="{ row }">
          <span class="money">{{ formatMoney(row.balance) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="积分" width="90" align="right">
        <template #default="{ row }">{{ row.points ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="isEnabled(row) ? 'success' : 'info'" size="small">
            {{ isEnabled(row) ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开卡时间" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openRecharge(row)">充值</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无会员数据" :image-size="80" />
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
      @size-change="loadMembers"
      @current-change="loadMembers"
    />

    <!-- 新建会员 -->
    <el-dialog v-model="createVisible" title="新建会员" width="520px" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="选择患者" prop="patientId">
          <el-select
            v-model="createForm.patientId"
            filterable
            remote
            clearable
            :remote-method="searchPatients"
            :loading="patientLoading"
            placeholder="输入姓名 / 手机号 / 病历号搜索"
            style="width: 100%"
            @change="onPatientChange"
          >
            <el-option
              v-for="p in patientOptions"
              :key="p.id"
              :label="`${p.name} / ${p.phone || '无手机号'} / ${p.patientNo || '-'}`"
              :value="p.id"
            >
              <div class="patient-option">
                <span class="patient-name">{{ p.name }}</span>
                <span class="text-muted">{{ p.phone || '无手机号' }}</span>
                <span class="text-muted">病历号 {{ p.patientNo || '-' }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="会员等级" prop="level">
          <el-select v-model="createForm.level" style="width: 100%">
            <el-option v-for="item in LEVEL_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="createForm.phone" maxlength="11" placeholder="选择患者后自动带出，可修改" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 会员详情 -->
    <el-drawer v-model="detailVisible" title="会员详情" size="680px" @closed="onDetailClosed">
      <div v-loading="detailLoading">
        <template v-if="detail.member">
          <div class="member-card">
            <div class="member-card-head">
              <div>
                <div class="member-name">
                  {{ detail.member.name }}
                  <el-tag :type="levelTag(detail.member.level)" size="small">{{ levelText(detail.member.level) }}</el-tag>
                  <el-tag :type="isEnabled(detail.member) ? 'success' : 'info'" size="small">
                    {{ isEnabled(detail.member) ? '正常' : '停用' }}
                  </el-tag>
                </div>
                <div class="text-muted">
                  会员号 {{ detail.member.memberNo }} · 手机号 {{ detail.member.phone || '-' }}
                </div>
              </div>
              <el-button type="primary" size="small" @click="openRecharge(detail.member)">充值</el-button>
            </div>
            <div class="balance-row">
              <div class="balance-item">
                <div class="text-muted">账户余额（元）</div>
                <div class="balance money">¥ {{ formatMoney(detail.member.balance) }}</div>
              </div>
              <div class="balance-item">
                <div class="text-muted">积分</div>
                <div class="balance-sub">{{ detail.member.points ?? 0 }}</div>
              </div>
              <div class="balance-item">
                <div class="text-muted">开卡时间</div>
                <div class="balance-sub">{{ formatDateTime(detail.member.createdAt) }}</div>
              </div>
            </div>
          </div>

          <p class="page-title section-title">储值流水</p>
          <el-table :data="detail.txns" border size="small">
            <el-table-column label="时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(txnTime(row)) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">{{ txnTypeText(row) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">
                <span class="money" :class="txnAmountClass(row)">{{ txnAmountText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变动后余额" width="120" align="right">
              <template #default="{ row }">{{ txnBalanceText(row) }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="140">
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无储值流水" :image-size="70" />
            </template>
          </el-table>
        </template>
        <el-empty v-else description="暂无会员信息" :image-size="80" />
      </div>
    </el-drawer>

    <!-- 充值 -->
    <el-dialog v-model="rechargeVisible" title="会员充值" width="460px" @closed="resetRechargeForm">
      <el-form ref="rechargeFormRef" :model="rechargeForm" :rules="rechargeRules" label-width="90px">
        <el-form-item label="会员">
          <span>{{ rechargeTarget?.name || '-' }}（{{ rechargeTarget?.memberNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="充值金额" prop="amount">
          <el-input-number
            v-model="rechargeForm.amount"
            :min="0.01"
            :max="1000000"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="支付方式" prop="method">
          <el-radio-group v-model="rechargeForm.method">
            <el-radio v-for="item in RECHARGE_METHODS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="rechargeForm.remark"
            type="textarea"
            :rows="2"
            maxlength="100"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageMembers, createMember, getMember, rechargeMember, pagePatients } from '@/api'
import { formatMoney, formatDateTime, PAY_METHOD_TEXT } from '@/utils/format'

const LEVEL_TEXT = { 1: '普通', 2: '银卡', 3: '金卡' }
const LEVEL_TAG = { 1: 'info', 2: 'primary', 3: 'warning' }
const LEVEL_OPTIONS = [
  { value: 1, label: '普通' },
  { value: 2, label: '银卡' },
  { value: 3, label: '金卡' }
]
const RECHARGE_METHODS = ['CASH', 'WECHAT', 'ALIPAY', 'BANK_CARD'].map((method) => ({
  value: method,
  label: PAY_METHOD_TEXT[method]
}))
const TXN_TYPE_TEXT = {
  RECHARGE: '充值',
  CONSUME: '消费',
  REFUND: '退款',
  ADJUST: '调整'
}

/* ---------------- 列表 ---------------- */
const keyword = ref('')
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

async function loadMembers() {
  loading.value = true
  try {
    const data = await pageMembers({ keyword: keyword.value || '', page: page.value, size: size.value })
    list.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total ?? 0)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadMembers()
}

function handleReset() {
  keyword.value = ''
  page.value = 1
  loadMembers()
}

function levelText(level) {
  return LEVEL_TEXT[Number(level)] || '普通'
}

function levelTag(level) {
  return LEVEL_TAG[Number(level)] || 'info'
}

function isEnabled(row) {
  const status = row?.status
  if (status === undefined || status === null) return true
  if (typeof status === 'boolean') return status
  if (typeof status === 'number') return status === 1
  return ['1', 'ACTIVE', 'ENABLED', 'NORMAL', 'TRUE'].includes(String(status).toUpperCase())
}

/* ---------------- 新建会员 ---------------- */
const createVisible = ref(false)
const creating = ref(false)
const patientLoading = ref(false)
const patientOptions = ref([])
const createFormRef = ref(null)
const createForm = ref({ patientId: null, level: 1, phone: '' })

const phoneValidator = (rule, value, callback) => {
  if (!value) return callback()
  if (!/^1[3-9]\d{9}$/.test(String(value))) return callback(new Error('请输入正确的 11 位手机号'))
  callback()
}

const createRules = {
  patientId: [{ required: true, message: '请选择患者', trigger: 'change' }],
  level: [{ required: true, message: '请选择会员等级', trigger: 'change' }],
  phone: [{ validator: phoneValidator, trigger: 'blur' }]
}

function openCreate() {
  resetCreateForm()
  createVisible.value = true
  searchPatients('')
}

function resetCreateForm() {
  createForm.value = { patientId: null, level: 1, phone: '' }
  patientOptions.value = []
  createFormRef.value?.clearValidate()
}

async function searchPatients(keywordText) {
  patientLoading.value = true
  try {
    const data = await pagePatients({ keyword: keywordText || '', page: 1, size: 20 })
    patientOptions.value = Array.isArray(data?.list) ? data.list : []
  } catch (e) {
    patientOptions.value = []
  } finally {
    patientLoading.value = false
  }
}

function onPatientChange(patientId) {
  const patient = patientOptions.value.find((p) => p.id === patientId)
  if (!patient) return
  if (patient.phone) createForm.value.phone = patient.phone
}

async function submitCreate() {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch (e) {
    return
  }
  const patient = patientOptions.value.find((p) => p.id === createForm.value.patientId)
  creating.value = true
  try {
    await createMember({
      patientId: createForm.value.patientId,
      name: patient?.name || '',
      phone: createForm.value.phone || patient?.phone || '',
      level: createForm.value.level
    })
    ElMessage.success('会员创建成功')
    createVisible.value = false
    page.value = 1
    await loadMembers()
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  } finally {
    creating.value = false
  }
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailId = ref(null)
const detail = ref({ member: null, txns: [] })

async function openDetail(row) {
  detailId.value = row.id
  detail.value = { member: row, txns: [] }
  detailVisible.value = true
  await loadDetail()
}

async function loadDetail() {
  if (!detailId.value) return
  detailLoading.value = true
  try {
    const data = await getMember(detailId.value)
    detail.value = {
      member: data?.member || null,
      txns: Array.isArray(data?.txns) ? data.txns : []
    }
  } catch (e) {
    detail.value = { member: null, txns: [] }
  } finally {
    detailLoading.value = false
  }
}

function onDetailClosed() {
  detailId.value = null
  detail.value = { member: null, txns: [] }
}

function txnTime(row) {
  return row.createdAt || row.createTime || row.txnTime
}

function txnTypeText(row) {
  return TXN_TYPE_TEXT[row.type] || row.type || '-'
}

function txnAmountNumber(row) {
  const num = Number(row.amount)
  return Number.isNaN(num) ? 0 : num
}

function txnAmountText(row) {
  const num = txnAmountNumber(row)
  const sign = num > 0 ? '+' : ''
  return `${sign}${formatMoney(num)}`
}

function txnAmountClass(row) {
  return txnAmountNumber(row) < 0 ? 'amount-out' : 'amount-in'
}

function txnBalance(row) {
  const raw = row.balanceAfter ?? row.afterBalance ?? row.balance ?? row.afterAmount
  return raw === undefined || raw === null ? '-' : formatMoney(raw)
}

function txnBalanceText(row) {
  const value = txnBalance(row)
  return value === '-' ? '-' : `¥ ${value}`
}

/* ---------------- 充值 ---------------- */
const rechargeVisible = ref(false)
const recharging = ref(false)
const rechargeTarget = ref(null)
const rechargeFormRef = ref(null)
const rechargeForm = ref({ amount: 100, method: 'CASH', remark: '' })

const amountValidator = (rule, value, callback) => {
  if (value === null || value === undefined || value === '') return callback(new Error('请输入充值金额'))
  const num = Number(value)
  if (Number.isNaN(num) || num <= 0) return callback(new Error('充值金额必须大于 0'))
  if (Math.round(num * 100) !== num * 100) return callback(new Error('充值金额最多保留两位小数'))
  if (num > 1000000) return callback(new Error('单笔充值金额不能超过 1000000 元'))
  callback()
}

const rechargeRules = {
  amount: [{ required: true, validator: amountValidator, trigger: 'blur' }],
  method: [{ required: true, message: '请选择支付方式', trigger: 'change' }]
}

function openRecharge(row) {
  rechargeTarget.value = { id: row.id, name: row.name, memberNo: row.memberNo }
  rechargeForm.value = { amount: 100, method: 'CASH', remark: '' }
  rechargeFormRef.value?.clearValidate()
  rechargeVisible.value = true
}

function resetRechargeForm() {
  rechargeForm.value = { amount: 100, method: 'CASH', remark: '' }
  rechargeFormRef.value?.clearValidate()
}

async function submitRecharge() {
  if (!rechargeFormRef.value || !rechargeTarget.value) return
  try {
    await rechargeFormRef.value.validate()
  } catch (e) {
    return
  }
  recharging.value = true
  try {
    await rechargeMember(rechargeTarget.value.id, {
      amount: Number(rechargeForm.value.amount).toFixed(2),
      method: rechargeForm.value.method,
      remark: rechargeForm.value.remark || ''
    })
    ElMessage.success('充值成功')
    rechargeVisible.value = false
    if (detailVisible.value && detailId.value === rechargeTarget.value.id) {
      await loadDetail()
    }
    await loadMembers()
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  } finally {
    recharging.value = false
  }
}

/* ---------------- 初始化 ---------------- */
loadMembers()
</script>

<style scoped>
.toolbar-right {
  margin-left: auto;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.patient-option {
  display: flex;
  align-items: center;
  gap: 12px;
}

.patient-name {
  min-width: 64px;
  font-weight: 500;
}

.member-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 16px;
  background: #fafcff;
}

.member-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.member-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.balance-row {
  display: flex;
  flex-wrap: wrap;
  gap: 40px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #e4e7ed;
}

.balance-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.balance {
  font-size: 26px;
  color: #e6a23c;
  line-height: 1.2;
}

.balance-sub {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.section-title {
  margin: 16px 0 12px;
}

.amount-in {
  color: #67c23a;
}

.amount-out {
  color: #f56c6c;
}
</style>
