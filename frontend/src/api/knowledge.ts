import { get, post, del } from './request'

export interface DocumentChunk {
  id: string
  content: string
  metadata: Record<string, unknown>
}

export interface ChunkPage {
  records: DocumentChunk[]
  total: number
  size: number
  current: number
  pages: number
}

export function uploadDocument(courseId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('courseId', String(courseId))
  return post<{ documentId: number; chunkCount: number }>('/api/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listChunks(courseId: number, page: number = 1, size: number = 10) {
  return get<{ items: DocumentChunk[]; total: number; page: number; pageSize: number; totalPages: number }>(
    `/api/knowledge-base/${courseId}/chunks?page=${page}&size=${size}`
  ).then(res => ({
    records: res.items,
    total: res.total,
    size: res.pageSize,
    current: res.page,
    pages: res.totalPages
  }))
}

export function getChunkCount(courseId: number) {
  return get<{ count: number }>(`/api/knowledge-base/${courseId}/count`).then(res => res.count)
}

export function clearKnowledgeBase(courseId: number) {
  return del<boolean>(`/api/knowledge-base/${courseId}`)
}
