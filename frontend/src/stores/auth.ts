import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { requestTokenRefresh } from '@/api/token'

export interface UserInfo {
  id: number
  username: string
  nickname: string
  role: string
}

/**
 * 提前判定过期的安全余量（毫秒）。
 * 避免“本地判定未过期、请求发出时刚好过期”的边界竞态。
 */
const EXPIRY_SKEW_MS = 30_000

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const tokenExpiry = ref<number>(parseInt(localStorage.getItem('tokenExpiry') || '0'))
  const user = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const displayName = computed(() => user.value?.nickname || user.value?.username || '访客')

  /**
   * Access Token 是否已过期。
   *
   * **为什么需要它：**`isLoggedIn` 只判断“token 是否存在”，过期 token 仍会被判为已登录，
   * 于是路由守卫会先把页面放行、再由接口的 401 触发跳转登录页——用户能看到明显的闪烁。
   * 有了本判定，守卫可以在放行前就完成续期或直接拒绝。
   *
   * 注：历史数据可能没有 tokenExpiry（值为 0），此时返回 false，
   * 把判定交给后端 401 + 拦截器刷新这条原有链路。
   */
  const isTokenExpired = computed(() => {
    if (!token.value) return true
    if (!tokenExpiry.value) return false
    return Date.now() >= tokenExpiry.value - EXPIRY_SKEW_MS
  })

  /** 是否具备续期条件（持有 Refresh Token） */
  const canRefresh = computed(() => !!refreshToken.value)

  function setTokens(access: string, refresh: string, expiresIn: number) {
    token.value = access
    refreshToken.value = refresh
    tokenExpiry.value = Date.now() + expiresIn
    localStorage.setItem('token', access)
    localStorage.setItem('refreshToken', refresh)
    localStorage.setItem('tokenExpiry', String(tokenExpiry.value))
  }

  function clearTokens() {
    token.value = null
    refreshToken.value = null
    tokenExpiry.value = 0
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('tokenExpiry')
    localStorage.removeItem('currentCourseId')
    localStorage.removeItem('currentCourseName')
    localStorage.removeItem('currentConversationId')
  }

  async function login(username: string, password: string) {
    const res = await authApi.login(username, password)
    if (res.token && res.refreshToken) {
      setTokens(res.token, res.refreshToken, res.expiresIn)
    }
    user.value = {
      id: res.userId,
      username: res.username,
      nickname: res.nickname,
      role: res.role
    }
    return res
  }

  async function register(username: string, password: string, nickname?: string) {
    const res = await authApi.register(username, password, nickname)
    if (res.token && res.refreshToken) {
      setTokens(res.token, res.refreshToken, res.expiresIn)
    }
    user.value = {
      id: res.userId,
      username: res.username,
      nickname: res.nickname,
      role: res.role
    }
    return res
  }

  async function fetchUserInfo() {
    // 失败时向上抛出，由路由守卫统一处理跳转登录页
    const res = await authApi.me()
    user.value = {
      id: res.userId,
      username: res.username,
      nickname: res.nickname,
      role: res.role
    }
    return res
  }

  /**
   * 主动用 Refresh Token 续期（供路由守卫在放行前预判）。
   *
   * 之所以不让守卫“等接口 401 再自动刷新”，是因为那样会先渲染页面骨架再跳走；
   * 在这里提前完成，导航结果就只有两种：直接进入，或直接去登录页。
   *
   * @returns 续期成功返回 true；无 Refresh Token 或已失效返回 false
   */
  async function tryRefresh(): Promise<boolean> {
    const refresh = refreshToken.value
    if (!refresh) return false
    const pair = await requestTokenRefresh(refresh)
    if (!pair) return false
    setTokens(pair.token, pair.refreshToken, pair.expiresIn)
    return true
  }

  /**
   * 仅清理本地登录态，<b>不做页面跳转</b>。
   *
   * 路由守卫必须用它而不是 {@link logout}：后者内部的 {@code window.location.href} 会触发
   * 整页刷新，把正在进行的路由导航打断（表现为“跳了两次”）。
   * 跳转与否交给守卫通过 {@code next('/login')} 决定。
   */
  function logoutLocal() {
    clearTokens()
  }

  function logout() {
    clearTokens()
    window.location.href = '/login'
  }

  return {
    token,
    refreshToken,
    tokenExpiry,
    user,
    isLoggedIn,
    isTokenExpired,
    canRefresh,
    displayName,
    setTokens,
    clearTokens,
    login,
    register,
    fetchUserInfo,
    tryRefresh,
    logoutLocal,
    logout
  }
})
