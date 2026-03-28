import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/ChatLayout.vue'),
    children: [
      {
        path: '',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: '智能对话' }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'students',
        name: 'Students',
        component: () => import('@/views/admin/StudentsView.vue'),
        meta: { title: '学生管理' }
      },
      {
        path: 'students/:id',
        name: 'StudentDetail',
        component: () => import('@/views/admin/StudentDetailView.vue'),
        meta: { title: '学生详情' }
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('@/views/admin/AlertsView.vue'),
        meta: { title: '预警管理' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/admin/KnowledgeView.vue'),
        meta: { title: '知识库管理' }
      },
      {
        path: 'finetune',
        name: 'Finetune',
        component: () => import('@/views/admin/FinetuneView.vue'),
        meta: { title: '模型微调' }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/admin/StatisticsView.vue'),
        meta: { title: '统计分析' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/admin/SettingsView.vue'),
        meta: { title: '系统设置' }
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', guest: true }
  },
  {
    path: '/install',
    name: 'Install',
    component: () => import('@/views/install/InstallView.vue'),
    meta: { title: '系统安装' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面未找到' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || 'Calmara'} - Calmara`
  
  const userStore = useUserStore()
  const isAuthenticated = userStore.isAuthenticated
  const isAdmin = userStore.isAdmin
  
  if (to.meta.requiresAuth && !isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.meta.requiresAdmin && !isAdmin) {
    next({ name: 'Chat' })
  } else if (to.meta.guest && isAuthenticated) {
    next({ name: 'Chat' })
  } else {
    next()
  }
})

export default router
