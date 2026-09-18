import { get, post, del, AI_TIMEOUT } from './request'

export interface StudyPlan {
  id: number
  userId: number
  courseName: string
  availableDays: string
  content: string
  createdAt: string
  updatedAt?: string
}

export function generatePlan(courseId: number, days: number) {
  // LLM 生成计划耗时较长，使用 AI 专用超时
  return post<string>('/api/plan/generate', { courseId, days }, { timeout: AI_TIMEOUT })
}

export function savePlan(courseName: string, availableDays: string, content: string) {
  return post<StudyPlan>('/api/plans', { courseName, availableDays, content })
}

export function listPlans() {
  return get<StudyPlan[]>('/api/plans')
}

export function deletePlan(id: number) {
  return del<boolean>(`/api/plans/${id}`)
}
