import { get, post, del } from './request'

export interface StudyPlan {
  id: number
  courseId: number
  title: string
  content: string
  createdAt: string
}

export function generatePlan(courseId: number, days: number) {
  return post<string>('/api/plan/generate', { courseId, days })
}

export function savePlan(courseId: number, title: string, content: string) {
  return post<StudyPlan>('/api/plans', { courseId, title, content })
}

export function listPlans() {
  return get<StudyPlan[]>('/api/plans')
}

export function deletePlan(id: number) {
  return del<boolean>(`/api/plans/${id}`)
}
