import { get, post, del, AI_TIMEOUT } from './request'

export interface DocumentChunk {
  id: string
  content: string
  /** 元数据 JSON 字符串（使用时需 JSON.parse） */
  metadata: string
  courseId: number | null
  /** 分块所属的资料（上传文件）名称；历史数据可能为空 */
  fileName: string | null
  /** 分块在原文档中的序号（后端已从 metadata 解析，直接使用） */
  chunkIndex: number | null
  chunkTotal: number | null
}

/** 一份资料（按上传文件聚合的分块集合） */
export interface KnowledgeDocument {
  /** 原始资料记录 ID；历史数据（未保存原文）为 null */
  id: number | null
  name: string
  chunkCount: number
  /** 是否存在完整原文（用于决定“预览原文”入口） */
  hasFullContent: boolean
  chunks: DocumentChunk[]
}

/** 资料列表响应 */
export interface KnowledgeDocumentList {
  documents: KnowledgeDocument[]
  documentCount: number
  totalChunks: number
}

/** 资料完整原文响应 */
export interface DocumentContent {
  name: string
  content: string
  length: number
}

export interface ChunkPage {
  records: DocumentChunk[]
  total: number
  size: number
  current: number
  pages: number
}

export interface UploadResult {
  /** 后端处理结果描述，例如“成功：文档已分块并向量化，共处理 4 块” */
  message: string
  courseId: number
}

/** 单个文件大小上限，与后端 application.yml 的 spring.servlet.multipart 配置保持一致 */
export const MAX_UPLOAD_SIZE = 10 * 1024 * 1024

/** 允许上传的扩展名（按扩展名校验，不依赖浏览器上报的 MIME 类型） */
export const ALLOWED_UPLOAD_EXTENSIONS = ['.txt', '.md', '.markdown', '.pdf', '.docx']

export function uploadDocument(courseId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('courseId', String(courseId))
  // 不手动设置 Content-Type：axios 会自动生成带 boundary 的 multipart 头，
  // 手动指定反而可能导致后端无法解析。
  // 上传后需调用 Embedding 接口向量化，耗时较长，故使用 AI_TIMEOUT。
  return post<UploadResult>('/api/document/upload', formData, {
    timeout: AI_TIMEOUT,
    // 由上传组件展示具体失败原因，这里关闭全局提示避免重复弹窗
    skipErrorToast: true
  })
}

export function listChunks(courseId: number, page: number = 1, size: number = 10) {
  return get<{ items: DocumentChunk[]; total: number; page: number; pageSize: number; totalPages: number }>(
    `/api/knowledge-base/${courseId}/chunks?page=${page}&pageSize=${size}`
  ).then(res => ({
    records: res.items,
    total: res.total,
    size: res.pageSize,
    current: res.page,
    pages: res.totalPages
  }))
}

/**
 * 查询课程下的资料列表（按上传文件聚合，每份资料含其全部分块）。
 * <p>知识库页默认展示资料列表，用户点击某份资料后再展开其分块。</p>
 */
export function listDocuments(courseId: number) {
  return get<KnowledgeDocumentList>(`/api/knowledge-base/${courseId}/documents`)
}

/**
 * 查询某份资料的完整原文（用于预览源文件）。
 * <p>后端优先返回上传时保存的原文，历史资料自动回退为分块拼接。</p>
 *
 * @param courseId 课程 ID
 * @param name     资料名称（含扩展名）
 */
export function getDocumentContent(courseId: number, name: string) {
  return get<DocumentContent>(
    `/api/knowledge-base/${courseId}/document-content?name=${encodeURIComponent(name)}`,
    { skipErrorToast: true }
  )
}

export function getChunkCount(courseId: number) {
  return get<{ count: number }>(`/api/knowledge-base/${courseId}/count`).then(res => res.count)
}

export function clearKnowledgeBase(courseId: number) {
  return del<boolean>(`/api/knowledge-base/${courseId}`)
}
