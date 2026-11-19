<template>
  <div class="page-card">
    <el-tabs v-model="activeTab">
      <!-- 员工账号 -->
      <el-tab-pane label="员工账号" name="users">
        <div class="page-toolbar">
          <el-button type="primary" @click="openCreate">新增员工</el-button>
          <el-button :loading="loading" @click="loadUsers">刷新</el-button>
        </div>

        <el-table v-loading="loading" :data="users" border stripe>
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="realName" label="姓名" width="120">
            <template #default="{ row }">{{ row.realName || '-' }}</template>
          </el-table-column>
          <el-table-column label="角色" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ ROLE_TEXT[row.role] || row.role || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="职称" width="140">
            <template #default="{ row }">{{ row.title || '-' }}</template>
          </el-table-column>
          <el-table-column label="科室" width="130">
            <template #default="{ row }">{{ row.dept || '-' }}</template>
          </el-table-column>
          <el-table-column label="手机号" width="130">
            <template #default="{ row }">{{ row.phone || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="isEnabled(row) ? 'success' : 'info'" size="small">
                {{ isEnabled(row) ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link :type="isEnabled(row) ? 'danger' : 'success'" @click="toggleStatus(row)">
                {{ isEnabled(row) ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无员工数据" :image-size="80" />
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
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </el-tab-pane>

      <!-- 医馆信息 -->
      <el-tab-pane label="医馆信息" name="clinic">
        <el-form
          ref="clinicFormRef"
          v-loading="clinicLoading"
          :model="clinicForm"
          :rules="clinicRules"
          label-width="100px"
          class="clinic-form"
        >
          <el-form-item label="医馆名称" prop="name">
            <el-input v-model="clinicForm.name" maxlength="50" placeholder="请输入医馆名称" />
          </el-form-item>
          <el-form-item label="地址" prop="address">
            <el-input v-model="clinicForm.address" maxlength="100" placeholder="请输入医馆地址" />
          </el-form-item>
          <el-form-item label="电话" prop="phone">
            <el-input v-model="clinicForm.phone" maxlength="20" placeholder="座机或手机号" />
          </el-form-item>
          <el-form-item label="公告" prop="notice">
            <el-input
              v-model="clinicForm.notice"
              type="textarea"
              :rows="4"
              maxlength="200"
              show-word-limit
              placeholder="展示在登录页 / 首页的公告，选填"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="clinicSaving" @click="saveClinic">保存</el-button>
            <el-button @click="loadClinic">重置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增 / 编辑员工 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑员工' : '新增员工'"
      width="560px"
      @closed="resetUserForm"
    >
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="90px">
        <el-form-item v-if="!isEdit" label="用户名" prop="username">
          <el-input v-model="userForm.username" maxlength="20" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item :label="isEdit ? '重置密码' : '密码'" prop="password">
          <el-input
            v-model="userForm.password"
            type="password"
            show-password
            maxlength="32"
            :placeholder="isEdit ? '留空则不修改密码' : '至少 6 位'"
          />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="userForm.realName" maxlength="20" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" style="width: 100%">
            <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="职称">
          <el-input v-model="userForm.title" maxlength="20" placeholder="如：主治中医师" />
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="userForm.dept" maxlength="20" placeholder="如：中医内科" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" maxlength="11" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUser">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageUsers,
  createUser,
  updateUser,
  updateUserStatus,
  getClinic,
  updateClinic
} from '@/api'
import { ROLE_TEXT } from '@/utils/format'

const ROLE_OPTIONS = Object.entries(ROLE_TEXT).map(([value, label]) => ({ value, label }))

const activeTab = ref('users')

/* ---------------- 员工列表 ---------------- */
const loading = ref(false)
const users = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

async function loadUsers() {
  loading.value = true
  try {
    const data = await pageUsers({ page: page.value, size: size.value })
    users.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total ?? 0)
  } catch (e) {
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function isEnabled(row) {
  const status = row?.status
  if (status === undefined || status === null) return true
  if (typeof status === 'boolean') return status
  if (typeof status === 'number') return status === 1
  return ['1', 'ACTIVE', 'ENABLED', 'NORMAL', 'TRUE'].includes(String(status).toUpperCase())
}

async function toggleStatus(row) {
  const enabled = isEnabled(row)
  const actionText = enabled ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${actionText}员工「${row.realName || row.username}」？`,
      '提示',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await updateUserStatus(row.id, enabled ? 0 : 1)
    ElMessage.success(`已${actionText}`)
    await loadUsers()
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  }
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const userFormRef = ref(null)
const userForm = ref({
  id: null,
  username: '',
  password: '',
  realName: '',
  role: 'DOCTOR',
  title: '',
  dept: '',
  phone: ''
})

const phoneValidator = (rule, value, callback) => {
  if (!value) return callback()
  if (!/^1[3-9]\d{9}$/.test(String(value))) return callback(new Error('请输入正确的 11 位手机号'))
  callback()
}

const userRules = computed(() => ({
  username: isEdit.value ? [] : [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!isEdit.value && !value) return callback(new Error('请输入密码'))
        if (value && String(value).length < 6) return callback(new Error('密码至少 6 位'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  phone: [{ validator: phoneValidator, trigger: 'blur' }]
}))

function emptyForm() {
  return {
    id: null,
    username: '',
    password: '',
    realName: '',
    role: 'DOCTOR',
    title: '',
    dept: '',
    phone: ''
  }
}

function resetUserForm() {
  userForm.value = emptyForm()
  isEdit.value = false
  userFormRef.value?.clearValidate()
}

function openCreate() {
  userForm.value = emptyForm()
  isEdit.value = false
  dialogVisible.value = true
  nextTick(() => userFormRef.value?.clearValidate())
}

function openEdit(row) {
  userForm.value = {
    id: row.id,
    username: row.username || '',
    password: '',
    realName: row.realName || '',
    role: row.role || 'DOCTOR',
    title: row.title || '',
    dept: row.dept || '',
    phone: row.phone || ''
  }
  isEdit.value = true
  dialogVisible.value = true
  nextTick(() => userFormRef.value?.clearValidate())
}

async function submitUser() {
  if (!userFormRef.value) return
  try {
    await userFormRef.value.validate()
  } catch (e) {
    return
  }
  const form = userForm.value
  saving.value = true
  try {
    if (isEdit.value) {
      const payload = {
        realName: form.realName,
        role: form.role,
        title: form.title || '',
        dept: form.dept || '',
        phone: form.phone || ''
      }
      if (form.password) payload.password = form.password
      await updateUser(form.id, payload)
      ElMessage.success('员工信息已更新')
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        realName: form.realName,
        role: form.role,
        title: form.title || '',
        dept: form.dept || '',
        phone: form.phone || ''
      })
      ElMessage.success('员工创建成功')
      page.value = 1
    }
    dialogVisible.value = false
    await loadUsers()
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  } finally {
    saving.value = false
  }
}

/* ---------------- 医馆信息 ---------------- */
const clinicLoading = ref(false)
const clinicSaving = ref(false)
const clinicFormRef = ref(null)
const clinicForm = ref({ name: '', address: '', phone: '', notice: '' })

const clinicRules = {
  name: [{ required: true, message: '请输入医馆名称', trigger: 'blur' }],
  phone: [
    {
      validator: (rule, value, callback) => {
        if (!value) return callback()
        if (!/^[0-9-]{6,20}$/.test(String(value))) return callback(new Error('请输入正确的联系电话'))
        callback()
      },
      trigger: 'blur'
    }
  ]
}

async function loadClinic() {
  clinicLoading.value = true
  try {
    const data = await getClinic()
    clinicForm.value = {
      name: data?.name || '',
      address: data?.address || '',
      phone: data?.phone || '',
      notice: data?.notice || ''
    }
    clinicFormRef.value?.clearValidate()
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  } finally {
    clinicLoading.value = false
  }
}

async function saveClinic() {
  if (!clinicFormRef.value) return
  try {
    await clinicFormRef.value.validate()
  } catch (e) {
    return
  }
  clinicSaving.value = true
  try {
    await updateClinic({
      name: clinicForm.value.name,
      address: clinicForm.value.address,
      phone: clinicForm.value.phone,
      notice: clinicForm.value.notice
    })
    ElMessage.success('医馆信息已保存')
  } catch (e) {
    /* 错误提示由请求拦截器统一处理 */
  } finally {
    clinicSaving.value = false
  }
}

/* ---------------- 初始化 ---------------- */
onMounted(() => {
  loadUsers()
  loadClinic()
})
</script>

<style scoped>
.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.clinic-form {
  max-width: 560px;
  padding-top: 8px;
}
</style>
