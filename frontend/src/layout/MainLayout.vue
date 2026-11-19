<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">
        <el-icon><OfficeBuilding /></el-icon>
        <span>方笺</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu" background-color="#1f2d3d" text-color="#c0c4cc"
        active-text-color="#ffd04b">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <el-tag size="small" effect="plain">{{ roleText }}</el-tag>
          <el-dropdown @command="onCommand">
            <span class="user-name">
              {{ store.displayName }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import { logout as logoutApi } from '../api'
import { ROLE_TEXT } from '../utils/format'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const allMenus = router.getRoutes().filter((r) => r.meta?.title && r.meta?.icon)

// 菜单按门诊业务顺序排列：挂号 -> 接诊 -> 收费 -> 发药 -> 库存 -> 会员 -> 报表 -> 系统
const MENU_ORDER = [
  '/registration',
  '/patients',
  '/doctor',
  '/cashier',
  '/pharmacy/dispense',
  '/pharmacy/trace',
  '/pharmacy/decoction',
  '/pharmacy/drugs',
  '/pharmacy/stock',
  '/members',
  '/reports',
  '/system/users',
  '/system/policies',
  '/system/audit',
  '/data/import',
  '/data/export',
  '/settings'
]

const menus = computed(() =>
  allMenus
    .filter((r) => {
      const roles = r.meta?.roles
      if (!roles || !roles.length) return true
      return roles.includes(store.role)
    })
    .map((r) => ({ path: r.path, title: r.meta.title, icon: r.meta.icon }))
    .sort((a, b) => MENU_ORDER.indexOf(a.path) - MENU_ORDER.indexOf(b.path))
)

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')
const roleText = computed(() => ROLE_TEXT[store.role] || store.role)

async function onCommand(command) {
  if (command !== 'logout') return
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  try {
    await logoutApi()
  } catch (e) {
    /* 无状态登出，忽略失败 */
  }
  store.clear()
  router.replace('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #1f2d3d;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  border-bottom: 1px solid #2b3a4d;
}

.menu {
  border-right: none;
  flex: 1;
}

.header {
  height: 56px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 1;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #303133;
}

.main {
  padding: 16px;
  overflow: auto;
}
</style>
