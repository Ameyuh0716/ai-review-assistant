import { get, put, del } from './request'

export interface StatsOverview {
  totalScore: number
  activeDays: number
  streakDays: number
  masteryRate: number
  quizCount: number
  wrongCount: number
}

export interface WrongAnswer {
  id: number
  question: string
  userAnswer: string
  correctAnswer: string
  explanation: string
  mastered: boolean
  createdAt: string
}

export function getOverview() {
  return get<StatsOverview>('/api/stats/overview')
}

export function getCourseStats(courseId: number) {
  return get<StatsOverview>(`/api/stats/course/${courseId}`)
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
