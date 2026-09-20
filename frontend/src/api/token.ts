import axios from 'axios'

/** 后端 /api/auth/refresh 返回的令牌对 */
export interface TokenPair {
  token: string
  refreshToken: string
  expiresIn: number
}

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

/** 与 request.ts 使用同一来源，保证开发（Vite 代理）与生产（同源）两种模式路径一致 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

/**
 * 用 Refresh Token 换取新的令牌对。
 *
 * **为什么单独放在这里、且用裸 axios：**
 * 1. 避免递归 —— 刷新请求若走项目的 `request` 实例，一旦它自身返回 401
 *    会被拦截器再次送去刷新，形成无限递归；
 * 2. 避免循环依赖 —— `request.ts` 需要调用刷新、`stores/auth.ts` 也需要调用刷新，
 *    若把实现放在任一侧都会形成“store ⇄ request”的双向依赖；
 * 3. 职责单一 —— 本模块只负责“拿令牌”，令牌如何存储由调用方决定
 *    （拦截器写 store，路由守卫也写 store）。
 *
 * @param refreshToken 当前持有的 Refresh Token
 * @returns 成功返回新令牌对；无 token 或刷新失败返回 null
 */
export async function requestTokenRefresh(refreshToken: string): Promise<TokenPair | null> {
  if (!refreshToken) return null
  try {
    const res = await axios.post<ApiEnvelope<TokenPair>>(
      `${BASE_URL}/api/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`
    )
    if (res.data.code === 0 && res.data.data) {
      return res.data.data
    }
    return null
  } catch (e) {
    // 刷新失败是预期内的分支（Refresh Token 过期），用 warn 避免误导性 error 噪音
    console.warn('[token] Refresh Token 换取新令牌失败', e)
    return null
  }
}
