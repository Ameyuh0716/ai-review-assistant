import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  /**
   * 切换栏目时回到页面顶部。
   * 与 App.vue 的页面过渡配合：新页面从顶部开始淡入，避免"停留在上一页的滚动位置"。
   */
  scrollBehavior() {
    return { top: 0, behavior: 'auto' }
  },
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

/**
 * 全局路由守卫。
 *
 * 职责：① 未登录禁止进入受保护页面；② 在<b>放行前</b>完成令牌续期，
 * 让导航结果只有“直接进入”或“直接去登录页”两种，不出现“先渲染再跳走”的闪烁。
 */
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const isPublic = to.meta.public === true

  // 公开页（登录页）直接放行
  if (isPublic) {
    next()
    return
  }

  if (!authStore.isLoggedIn) {
    next('/login')
    return
  }

  /*
   * Access Token 已过期时的处理。
   *
   * 为什么不能直接放行：isLoggedIn 只判断“有没有 token”，过期 token 仍会通过这一关；
   * 若放行，页面会先渲染骨架，再由 /api/auth/me 的 401 触发跳转——用户可见明显闪烁。
   * 也不能仅依赖 refreshToken 是否存在：它同样可能已过期（7 天），
   * 此时应立即去登录页，而不是白白渲染一次页面。
   */
  if (authStore.isTokenExpired) {
    if (!authStore.canRefresh) {
      authStore.logoutLocal()
      next('/login')
      return
    }
    const refreshed = await authStore.tryRefresh()
    if (!refreshed) {
      authStore.logoutLocal()
      next('/login')
      return
    }
  }

  // 仅在用户信息缺失时拉取（续期成功后 user 仍为 null，需要补上）
  if (!authStore.user) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      authStore.logoutLocal()
      next('/login')
      return
    }
  }

  next()
})

export default router
