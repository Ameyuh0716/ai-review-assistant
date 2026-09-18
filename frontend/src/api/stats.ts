import { get, put, del } from './request'

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

export function getOverview() {
  return get<StatsOverview>('/api/stats/overview')
}

export function getWrongStats() {
  return get<WrongStats>('/api/wrong-book/stats')
}

export function listWrongAnswers() {
  return get<WrongAnswer[]>('/api/wrong-book')
}

export function masterWrongAnswer(id: number) {
  return put<boolean>(`/api/wrong-book/${id}/master`)
}

export function deleteWrongAnswer(id: number) {
  return del<boolean>(`/api/wrong-book/${id}`)
}
