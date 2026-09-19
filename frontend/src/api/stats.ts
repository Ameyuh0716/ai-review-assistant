import { get, post, put, del } from './request'

/** 与后端 UserProgressDto 一一对应 */
export interface StatsOverview {
  userId: string
  totalCourses: number
  reviewedCourses: number
  totalReviews: number
  activeDays: number
  streakDays: number
  lastReviewTime: string | null
  overallScore: number
}

/** 与后端 /api/wrong-book/stats 返回结构对应 */
export interface WrongStats {
  total: number
  mastered: number
  unmastered: number
  masteryRate: number
}

export interface WrongAnswer {
  id: number
  userId: number
  courseId: number | null
  question: string
  options: string | null
  userAnswer: string
  correctAnswer: string
  explanation: string
  topic: string | null
  /** 注意: 后端实体字段为 isMastered */
  isMastered: boolean
  wrongCount: number
  lastWrongAt: string
  createdAt: string
}

/** 错题列表筛选条件 */
export interface WrongFilter {
  courseId?: number | null
  mastered?: boolean | null
  topic?: string | null
  keyword?: string | null
}

/** 错题重做结果 */
export interface RedoResult {
  correct: boolean
  correctAnswer: string
  explanation: string
  wrongCount: number
  isMastered: boolean
}

/** 获取学习总览（不发全局错误弹窗，由页面自行处理） */
export function getOverview() {
  return get<StatsOverview>('/api/stats/overview', { skipErrorToast: true })
}

/** 获取错题统计（不发全局错误弹窗） */
export function getWrongStats() {
  return get<WrongStats>('/api/wrong-book/stats', { skipErrorToast: true })
}

/** 查询错题列表，支持课程/掌握状态/知识点/关键词筛选（不发全局错误弹窗） */
export function listWrongAnswers(filter: WrongFilter = {}) {
  const params = new URLSearchParams()
  if (filter.courseId != null) params.append('courseId', String(filter.courseId))
  if (filter.mastered != null) params.append('mastered', String(filter.mastered))
  if (filter.topic) params.append('topic', filter.topic)
  if (filter.keyword) params.append('keyword', filter.keyword)
  const qs = params.toString()
  return get<WrongAnswer[]>(`/api/wrong-book${qs ? `?${qs}` : ''}`, { skipErrorToast: true })
}

/** 标记错题为已掌握 */
export function masterWrongAnswer(id: number) {
  return put<boolean>(`/api/wrong-book/${id}/master`)
}

/** 删除错题 */
export function deleteWrongAnswer(id: number) {
  return del<boolean>(`/api/wrong-book/${id}`)
}

/** 重做错题并提交答案 */
export function redoWrongAnswer(id: number, answer: string) {
  return post<RedoResult>(`/api/wrong-book/${id}/redo`, { answer })
}
