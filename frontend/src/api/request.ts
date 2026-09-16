import axios, { AxiosError, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach(cb => cb(token))
  refreshSubscribers = []
}

async function doRefresh(): Promise<string | null> {
  const authStore = useAuthStore()
  if (!authStore.refreshToken) return null

  try {
    const res = await axios.post<ApiResponse<{
      token: string
      refreshToken: string
      expiresIn: number
    }>>(
      `${request.defaults.baseURL || ''}/api/auth/refresh?refreshToken=${encodeURIComponent(authStore.refreshToken)}`
    )
    if (res.data.code === 0 && res.data.data) {
      authStore.setTokens(res.data.data.token, res.data.data.refreshToken, res.data.data.expiresIn)
      return res.data.data.token
    }
  } catch (e) {
    console.error('Refresh token failed', e)
  }
  return null
}

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore()
    if (authStore.token && config.headers) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { data } = response
    if (data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  async (error: AxiosError<ApiResponse>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const status = error.response?.status
    console.error('[axios error]', error.config?.url, status, error.response?.data, error.message)

    if (status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve) => {
          subscribeTokenRefresh((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`
            }
            resolve(request(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const newToken = await doRefresh()
      isRefreshing = false

      if (newToken) {
        onTokenRefreshed(newToken)
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
        }
        return request(originalRequest)
      } else {
        const authStore = useAuthStore()
        authStore.logout()
        window.location.href = '/login'
      }
    }

    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export function get<T>(url: string, config?: AxiosRequestConfig) {
  return request.get<ApiResponse<T>>(url, config).then(res => res.data.data)
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return request.post<ApiResponse<T>>(url, data, config).then(res => res.data.data)
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return request.put<ApiResponse<T>>(url, data, config).then(res => res.data.data)
}

export function del<T>(url: string, config?: AxiosRequestConfig) {
  return request.delete<ApiResponse<T>>(url, config).then(res => res.data.data)
}

export default request
