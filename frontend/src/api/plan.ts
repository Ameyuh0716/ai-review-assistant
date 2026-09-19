import { get, post, put, del, AI_TIMEOUT } from './request'
import { useAuthStore } from '@/stores/auth'

/** 计划中的单日结构（后端从计划正文解析） */
export interface PlanDay {
  day: number
  title: string
  items: string[]
}

/** 单个环节（复习内容/掌握内容/练习）的状态 */
export interface PlanSectionState {
  /** AI 生成的内容（Markdown） */
  content?: string
  /** 是否已勾选完成 */
  done?: boolean
  /** 生成时间 */
  at?: string
}

/** 单日状态 */
export interface PlanDayState {
  done?: boolean
  review?: PlanSectionState
  mastery?: PlanSectionState
  practice?: PlanSectionState
}

/** 结构化进度：天数 -> 单日状态 */
export type PlanProgress = Record<string, PlanDayState>

/** 可勾选/生成的环节定义 */
export const PLAN_SECTIONS = [
  { key: 'review', label: '复习内容' },
  { key: 'mastery', label: '掌握内容' },
  { key: 'practice', label: '练习' }
] as const

export type PlanSectionKey = typeof PLAN_SECTIONS[number]['key']

export interface StudyPlan {
  id: number
  userId: number
  courseName: string
  availableDays: string
  content: string
  /** 结构化进度 JSON 字符串 */
  progress?: string | null
  /** 后端解析出的每日结构 */
  days?: PlanDay[]
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

export function getPlan(id: number) {
  return get<StudyPlan>(`/api/plans/${id}`)
}

export function deletePlan(id: number) {
  return del<boolean>(`/api/plans/${id}`)
}

/** 保存计划的结构化进度（勾选状态等） */
export function updatePlanProgress(id: number, progress: PlanProgress) {
  return put<StudyPlan>(`/api/plans/${id}/progress`, { progress: JSON.stringify(progress) })
}

/**
 * 构建按天生成学习材料的 SSE 地址。
 * <p>EventSource 无法自定义请求头，因此通过 token query 参数传递认证信息。</p>
 *
 * @param planId  计划 ID
 * @param day     天数序号
 * @param section 环节：review / mastery / practice
 */
export function buildDayGenerateUrl(planId: number, day: number, section: PlanSectionKey) {
  const authStore = useAuthStore()
  const params = new URLSearchParams()
  params.append('section', section)
  if (authStore.token) params.append('token', authStore.token)
  return `/api/plans/${planId}/days/${day}/generate?${params.toString()}`
}

