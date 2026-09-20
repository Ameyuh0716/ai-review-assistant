import axios, { AxiosError, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { requestTokenRefresh } from '@/api/token'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 为 true 时不弹出全局错误提示，由调用方自行展示带上下文的提示（避免重复弹窗） */
    skipErrorToast?: boolean
  }
}

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

let isRefreshing = false
/**
 * 令牌刷新期间被挡住的请求回调。
 * 参数为 null 表示刷新<b>失败</b>——必须把失败也广播出去，
 * 否则排队中的 Promise 会永远处于 pending（既不会重放也不会报错）。
 */
let refreshSubscribers: Array<(token: string | null) => void> = []

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000
  // 注意：不要在这里设置全局 'Content-Type': 'application/json'。
  // axios 对 FormData 的处理逻辑是「若 Content-Type 为 json 则把 FormData 序列化成 JSON」，
  // 设置全局 json 头会让文件上传退化成 `JSON.stringify(formDataToJSON(data))`，
  // 后端收不到 multipart 而报 MissingServletRequestParameterException(500)。
  // axios 会自动为对象负载带上 application/json，为 FormData 带上正确的 multipart boundary。
})

/** AI 生成类接口专用超时（LLM 生成通常需要 30-90 秒） */
export const AI_TIMEOUT = 120000

function subscribeTokenRefresh(cb: (token: string | null) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(token: string | null) {
  refreshSubscribers.forEach(cb => cb(token))
  refreshSubscribers = []
}

/**
 * 刷新 Access Token 并写回 store。
 *
 * 令牌交换本身交给 {@link requestTokenRefresh}（裸 axios 实现），这里只负责状态写入。
 * 拆分的原因：若把刷新逻辑写在这里，{@code stores/auth.ts} 为了在路由守卫中预判续期
 * 就必须反过来导入本模块，形成“store ⇄ request”的循环依赖。
 *
 * @returns 刷新成功返回新 Access Token；失败返回 null
 */
async function doRefresh(): Promise<string | null> {
  const authStore = useAuthStore()
  const pair = await requestTokenRefresh(authStore.refreshToken || '')
  if (!pair) return null
  authStore.setTokens(pair.token, pair.refreshToken, pair.expiresIn)
  return pair.token
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
      if (!response.config.skipErrorToast) {
        ElMessage.error(data.message || '请求失败')
      }
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  async (error: AxiosError<ApiResponse>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const status = error.response?.status
    // 401 属预期内的会话过期流程（自动刷新），仅记录 warn，避免误导
    if (status === 401) {
      console.warn('[axios] 会话过期，尝试自动刷新:', error.config?.url)
    } else {
      console.error('[axios error]', error.config?.url, status, error.response?.data, error.message)
    }

    if (status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // 已有刷新在途：排队等待结果，避免 N 个 401 触发 N 次刷新（刷新风暴 + 轮换竞态）
        return new Promise((resolve, reject) => {
          subscribeTokenRefresh((token) => {
            // 刷新失败的情况：直接让该请求失败，不能让它永远挂起
            if (!token) {
              reject(error)
              return
            }
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
        // 刷新失败：先把失败广播给所有排队请求（否则它们会永久 pending），再登出
        onTokenRefreshed(null)
        const authStore = useAuthStore()
        authStore.logout()
        return Promise.reject(error)
      }
    }

    // 刷新后仍然 401：属于会话彻底失效，静默登出跳转，避免弹出误导性的错误弹窗
    if (status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      window.location.href = '/login'
      return Promise.reject(error)
    }

    const msg = error.response?.data?.message || error.message || '网络错误'
    if (!originalRequest?.skipErrorToast) {
      ElMessage.error(msg)
    }
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
