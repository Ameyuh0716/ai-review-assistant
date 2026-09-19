import { get, post, put, del } from './request'

export interface Conversation {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  intent?: string
  createdAt: string
  /** RAG 检索元数据（仅前端附加，不来自后端消息表） */
  ragMeta?: RagMetaInfo
}

/** RAG 检索元数据，用于对话中标注知识库检索情况 */
export interface RagMetaInfo {
  resultCount: number
  topK: number
  threshold?: number
  latencyMs?: number
  keywordFallback?: boolean
  topScore?: number | null
  candidateCount?: number
}

export function listConversations() {
  return get<Conversation[]>('/api/conversations')
}

export function createConversation(title: string = '新对话') {
  return post<Conversation>('/api/conversations', { title })
}

export function updateConversation(id: number, title: string) {
  return put<Conversation>(`/api/conversations/${id}`, { title })
}

export function deleteConversation(id: number) {
  return del<boolean>(`/api/conversations/${id}`)
}

export function listMessages(conversationId: number) {
  return get<Message[]>(`/api/messages?conversationId=${conversationId}`)
}

/**
 * 更新指定消息的内容（“编辑提问”场景）。
 * 只修改该条文本，不删除任何历史记录。
 */
export function updateMessage(messageId: number, content: string) {
  return put<Message>(`/api/messages/${messageId}`, { content })
}
