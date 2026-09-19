import { useAuthStore } from '@/stores/auth'

/**
 * 构建流式对话 URL。
 *
 * @param message          用户消息
 * @param conversationId   会话 ID（可选）
 * @param reuseUserMessage 为 true 时不重复保存用户消息（“重新生成”场景）
 */
export function buildStreamUrl(message: string, conversationId?: number, reuseUserMessage = false): string {
  const authStore = useAuthStore()
  const params = new URLSearchParams()
  params.append('message', message)
  if (conversationId) params.append('conversationId', String(conversationId))
  if (reuseUserMessage) params.append('reuseUserMessage', 'true')
  if (authStore.token) params.append('token', authStore.token)
  return `/api/agent/stream?${params.toString()}`
}
