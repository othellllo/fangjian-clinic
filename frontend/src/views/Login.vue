<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-inner">
          <div class="brand-logo">
            <el-icon :size="30"><FirstAidKit /></el-icon>
          </div>
          <h1 class="brand-title">医馆 HIS</h1>
          <p class="brand-sub">中医馆信息管理系统</p>
          <div class="brand-divider"></div>
          <ul class="brand-features">
            <li>挂号候诊 · 中医病历 · 处方开立</li>
            <li>收费退费 · 会员储值 · 药品库存</li>
            <li>经营报表 · 医生绩效 · 日结对账</li>
          </ul>
          <p class="brand-foot">单机私有化部署 · 数据不出店</p>
        </div>
      </div>

      <div class="form-pane">
        <h2 class="form-title">账号登录</h2>
        <p class="form-sub">欢迎回来，请使用员工账号登录系统</p>

        <el-form ref="formRef" :model="form" :rules="rules" class="login-form" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入用户名" clearable
              autocomplete="username" @keyup.enter="onSubmit" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" :prefix-icon="Lock" type="password" placeholder="请输入密码"
              show-password autocomplete="current-password" @keyup.enter="onSubmit" />
          </el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
            {{ loading ? '登录中…' : '登 录' }}
          </el-button>
        </el-form>

        <div class="demo-block">
          <div class="demo-title">演示账号</div>
          <div class="demo-list">
            <span v-for="acc in demoAccounts" :key="acc.username" class="demo-item"
              :title="`点击填入 ${acc.username}`" @click="fillDemo(acc)">
              {{ acc.username }}/{{ acc.password }}（{{ acc.label }}）
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FirstAidKit, Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { HOME_BY_ROLE } from '@/router'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const demoAccounts = [
  { username: 'admin', password: '123456', label: '管理员' },
  { username: 'doctor01', password: '123456', label: '医生' },
  { username: 'cashier01', password: '123456', label: '收费员' },
  { username: 'pharmacist01', password: '123456', label: '药师' }
]

function fillDemo(acc) {
  form.username = acc.username
  form.password = acc.password
  formRef.value?.clearValidate()
}

/** 只接受站内路径，避免 redirect 参数被用作开放跳转 */
function safeRedirect() {
  const raw = route.query.redirect
  const target = Array.isArray(raw) ? raw[0] : raw
  if (typeof target !== 'string' || !target) return ''
  if (!target.startsWith('/') || target.startsWith('//')) return ''
  if (target === '/login' || target.startsWith('/login?')) return ''
  return target
}

async function onSubmit() {
  if (loading.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const user = await store.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来，${user?.realName || user?.username || ''}`)
    const target = safeRedirect() || HOME_BY_ROLE[user?.role] || '/'
    await router.replace(target)
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 12% 18%, rgba(31, 138, 109, 0.14) 0, transparent 42%),
    radial-gradient(circle at 88% 84%, rgba(64, 158, 255, 0.12) 0, transparent 46%),
    #f5f7fa;
}

.login-card {
  width: 920px;
  max-width: 100%;
  min-height: 520px;
  display: flex;
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 18px 48px rgba(31, 45, 61, 0.14);
}

.brand {
  width: 44%;
  flex-shrink: 0;
  position: relative;
  padding: 44px 38px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  color: #fff;
  background: linear-gradient(155deg, #12503f 0%, #1f7d63 52%, #2f9e7e 100%);
}

.brand::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 82% 12%, rgba(255, 255, 255, 0.16) 0, transparent 38%),
    radial-gradient(circle at 8% 92%, rgba(255, 255, 255, 0.1) 0, transparent 40%);
  pointer-events: none;
}

.brand-inner {
  position: relative;
  z-index: 1;
  width: 100%;
}

.brand-logo {
  width: 58px;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
}

.brand-title {
  margin: 20px 0 6px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 3px;
}

.brand-sub {
  margin: 0;
  font-size: 14px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.82);
}

.brand-divider {
  width: 44px;
  height: 3px;
  margin: 26px 0 22px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.55);
}

.brand-features {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
  line-height: 2.1;
  color: rgba(255, 255, 255, 0.9);
}

.brand-features li::before {
  content: '·';
  margin-right: 8px;
  font-weight: 700;
}

.brand-foot {
  margin: 34px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.66);
}

.form-pane {
  flex: 1;
  padding: 56px 52px 40px;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.form-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f2d3d;
}

.form-sub {
  margin: 8px 0 30px;
  font-size: 13px;
  color: #909399;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.submit-btn {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  font-size: 15px;
  letter-spacing: 4px;
}

.demo-block {
  margin-top: 30px;
  padding-top: 18px;
  border-top: 1px dashed #ebeef5;
}

.demo-title {
  margin-bottom: 8px;
  font-size: 12px;
  color: #909399;
}

.demo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.demo-item {
  font-size: 12px;
  line-height: 20px;
  padding: 2px 8px;
  border-radius: 4px;
  color: #909399;
  background: #f5f7fa;
  cursor: pointer;
  transition: all 0.2s;
}

.demo-item:hover {
  color: #1f8a6d;
  background: #eaf7f1;
}

@media (max-width: 820px) {
  .brand {
    display: none;
  }

  .login-card {
    width: 400px;
    min-height: auto;
  }

  .form-pane {
    padding: 40px 28px 32px;
  }
}
</style>
