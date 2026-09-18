import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'

export interface UserInfo {
  id: number
  username: string
  nickname: string
  role: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const tokenExpiry = ref<number>(parseInt(localStorage.getItem('tokenExpiry') || '0'))
  const user = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const displayName = computed(() => user.value?.nickname || user.value?.username || '访客')

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
    displayName,
    setTokens,
    clearTokens,
    login,
    register,
    fetchUserInfo,
    logout
  }
})
