import { get } from './request'

/** 后端 rag_search_log 实体（仅前端用到的字段） */
export interface RagSearchLog {
  id: number
  conversationId: number | null
  query: string
  resultCount: number
  topK: number
  similarityThreshold: number
  latencyMs: number
  success: boolean
  errorMsg: string | null
  createdAt: string
}

/** 查询会话的 RAG 检索日志（按时间倒序），用于历史消息的检索标记回显 */
export function listRagLogs(conversationId: number) {
  return get<RagSearchLog[]>(`/api/rag-search-logs?conversationId=${conversationId}`, {
    skipErrorToast: true
  })
}
