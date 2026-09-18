import { useAuthStore } from '@/stores/auth'

export function buildStreamUrl(message: string, conversationId?: number): string {
  const authStore = useAuthStore()
  const params = new URLSearchParams()
  params.append('message', message)
  if (conversationId) params.append('conversationId', String(conversationId))
  if (authStore.token) params.append('token', authStore.token)
  return `/api/agent/stream?${params.toString()}`
}
