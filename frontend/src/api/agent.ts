import { useAuthStore } from '@/stores/auth'

/**
 * 构建流式对话 URL。
 *
 * @param message          用户消息
 * @param conversationId   会话 ID（可选）
 * @param reuseUserMessage 为 true 时不重复保存用户消息（“编辑/重新生成”场景）
 * @param courseId         当前选中的课程 ID（可选），用于绑定会话课程以支撑学习统计
 * @param assistantMessageId 需要就地覆盖的 AI 消息 ID（可选）：回复写入原消息行而非追加，
 *                          保证“编辑/重新生成”不会删除任何历史记录
 * @param historyBeforeId  上下文截断点（可选）：只取该消息之前的历史作为上下文
 */
export function buildStreamUrl(
  message: string,
  conversationId?: number,
  reuseUserMessage = false,
  courseId?: number,
  assistantMessageId?: number,
  historyBeforeId?: number
): string {
  const authStore = useAuthStore()
  const params = new URLSearchParams()
  params.append('message', message)
  if (conversationId) params.append('conversationId', String(conversationId))
  if (reuseUserMessage) params.append('reuseUserMessage', 'true')
  if (courseId) params.append('courseId', String(courseId))
  if (assistantMessageId) params.append('assistantMessageId', String(assistantMessageId))
  if (historyBeforeId) params.append('historyBeforeId', String(historyBeforeId))
  if (authStore.token) params.append('token', authStore.token)
  return `/api/agent/stream?${params.toString()}`
}
