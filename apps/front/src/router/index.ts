import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    redirect: '/ledgerpilot',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'ledgerpilot',
        name: 'LedgerPilotHome',
        component: () => import('@/views/ledgerpilot/HomeView.vue'),
        meta: { title: 'AI智能做账', icon: 'MagicStick' },
      },
      {
        path: 'voucher',
        name: 'Voucher',
        component: () => import('@/views/voucher/VoucherList.vue'),
        meta: { title: '凭证管理', icon: 'Document' },
      },
      {
        path: 'voucher/create',
        name: 'VoucherCreate',
        component: () => import('@/views/voucher/VoucherCreate.vue'),
        meta: { title: '新增凭证', hidden: true },
      },
      {
        path: 'voucher/detail/:id',
        name: 'VoucherDetail',
        component: () => import('@/views/voucher/VoucherDetail.vue'),
        meta: { title: '凭证详情', hidden: true },
      },
      {
        path: 'opening-balance',
        name: 'OpeningBalance',
        component: () => import('@/views/voucher/OpeningBalance.vue'),
        meta: { title: '期初余额', hidden: true },
      },
      {
        path: 'subject',
        name: 'Subject',
        component: () => import('@/views/subject/SubjectList.vue'),
        meta: { title: '科目管理', icon: 'FolderOpened' },
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/report/ReportCenter.vue'),
        meta: { title: '报表中心', icon: 'DataAnalysis' },
      },
      {
        path: 'tax',
        name: 'Tax',
        component: () => import('@/views/tax/TaxCenter.vue'),
        meta: { title: '税务中心', icon: 'Money' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { title: '系统设置', icon: 'Setting' },
      },
      {
        path: 'knowledge',
        name: 'KnowledgeBase',
        component: () => import('@/views/knowledge/KnowledgeBaseView.vue'),
        meta: { title: '知识库管理', icon: 'Books' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫：登录拦截（未登录访问业务页跳 /login；登录页放行）
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.isLoggedIn()) {
    next('/login')
  } else {
    next()
  }
})

export default router
