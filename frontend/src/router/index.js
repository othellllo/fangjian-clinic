import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

export const HOME_BY_ROLE = {
  ADMIN: '/registration',
  DOCTOR: '/doctor',
  CASHIER: '/cashier',
  PHARMACIST: '/pharmacy/dispense'
}

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    children: [
      {
        path: 'registration',
        name: 'registration',
        component: () => import('../views/registration/RegistrationDesk.vue'),
        meta: { title: '挂号台', icon: 'Tickets', roles: ['ADMIN', 'CASHIER'] }
      },
      {
        path: 'patients',
        name: 'patients',
        component: () => import('../views/patient/PatientList.vue'),
        meta: { title: '患者档案', icon: 'User', roles: ['ADMIN', 'DOCTOR', 'CASHIER'] }
      },
      {
        path: 'doctor',
        name: 'doctor',
        component: () => import('../views/doctor/Workbench.vue'),
        meta: { title: '医生工作台', icon: 'FirstAidKit', roles: ['ADMIN', 'DOCTOR'] }
      },
      {
        path: 'cashier',
        name: 'cashier',
        component: () => import('../views/cashier/CashierDesk.vue'),
        meta: { title: '收费台', icon: 'Money', roles: ['ADMIN', 'CASHIER'] }
      },
      {
        path: 'pharmacy/dispense',
        name: 'dispense',
        component: () => import('../views/pharmacy/DispenseDesk.vue'),
        meta: { title: '药房发药', icon: 'Box', roles: ['ADMIN', 'PHARMACIST'] }
      },
      {
        path: 'pharmacy/drugs',
        name: 'drugs',
        component: () => import('../views/pharmacy/DrugCatalog.vue'),
        meta: { title: '药品字典', icon: 'Collection', roles: ['ADMIN', 'PHARMACIST'] }
      },
      {
        path: 'pharmacy/stock',
        name: 'stock',
        component: () => import('../views/pharmacy/StockManage.vue'),
        meta: { title: '库存管理', icon: 'Files', roles: ['ADMIN', 'PHARMACIST'] }
      },
      {
        path: 'pharmacy/decoction',
        name: 'decoction',
        component: () => import('../views/pharmacy/DecoctionManage.vue'),
        meta: { title: '代煎管理', icon: 'CoffeeCup', roles: ['ADMIN', 'PHARMACIST'] }
      },
      {
        path: 'pharmacy/trace',
        name: 'trace',
        component: () => import('../views/pharmacy/TraceManage.vue'),
        meta: { title: '追溯码管理', icon: 'Tickets', roles: ['ADMIN', 'PHARMACIST', 'CASHIER'] }
      },
      {
        path: 'members',
        name: 'members',
        component: () => import('../views/member/MemberList.vue'),
        meta: { title: '会员管理', icon: 'Star', roles: ['ADMIN', 'CASHIER'] }
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('../views/report/ReportDashboard.vue'),
        meta: { title: '经营报表', icon: 'DataAnalysis', roles: ['ADMIN', 'CASHIER'] }
      },
      {
        path: 'system/users',
        name: 'users',
        component: () => import('../views/system/UserManage.vue'),
        meta: { title: '员工管理', icon: 'Setting', roles: ['ADMIN'] }
      },
      {
        path: 'system/audit',
        name: 'audit',
        component: () => import('../views/system/AuditLog.vue'),
        meta: { title: '操作审计', icon: 'Document', roles: ['ADMIN'] }
      },
      {
        path: 'system/policies',
        name: 'policies',
        component: () => import('../views/system/PolicyLibrary.vue'),
        meta: { title: '医保政策', icon: 'Reading', roles: ['ADMIN'] }
      },
      {
        path: 'data/import',
        name: 'dataImport',
        component: () => import('../views/data/ImportCenter.vue'),
        meta: { title: '数据导入', icon: 'Upload', roles: ['ADMIN'] }
      },
      {
        path: 'data/export',
        name: 'dataExport',
        component: () => import('../views/data/ExportCenter.vue'),
        meta: { title: '数据导出', icon: 'Download', roles: ['ADMIN'] }
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('../views/settings/SettingsCenter.vue'),
        meta: { title: '系统设置', icon: 'Tools', roles: ['ADMIN'] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (!store.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const roles = to.meta.roles
  if (roles && roles.length && !roles.includes(store.role)) {
    return HOME_BY_ROLE[store.role] || '/login'
  }
  if (to.path === '/') {
    return HOME_BY_ROLE[store.role] || '/registration'
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 医馆 HIS` : '医馆 HIS'
})

export default router
