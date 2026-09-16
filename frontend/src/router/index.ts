import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      name: 'Chat',
      component: () => import('@/views/ChatView.vue')
    },
    {
      path: '/courses',
      name: 'Courses',
      component: () => import('@/views/CoursesView.vue')
    },
    {
      path: '/knowledge',
      name: 'Knowledge',
      component: () => import('@/views/KnowledgeView.vue')
    },
    {
      path: '/quiz',
      name: 'Quiz',
      component: () => import('@/views/QuizView.vue')
    },
    {
      path: '/plan',
      name: 'Plan',
      component: () => import('@/views/PlanView.vue')
    },
    {
      path: '/stats',
      name: 'Stats',
      component: () => import('@/views/StatsView.vue')
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const isPublic = to.meta.public === true

  if (!isPublic && !authStore.isLoggedIn) {
    next('/login')
    return
  }

  // 仅在受保护路由且用户信息缺失时拉取用户信息，避免登录页因过期 token 报错
  if (!isPublic && authStore.isLoggedIn && !authStore.user) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      authStore.logout()
      next('/login')
      return
    }
  }

  next()
})

export default router
