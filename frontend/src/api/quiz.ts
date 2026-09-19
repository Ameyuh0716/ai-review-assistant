import { post, AI_TIMEOUT } from './request'
import { useAuthStore } from '@/stores/auth'

export interface QuizQuestion {
  question: string
  options: string[]
  answer: string
  explanation: string
}

export interface QuizResult {
  score: number
  total: number
  correctCount: number
  details: Array<{
    index: number
    correct: boolean
    userAnswer: string
    correctAnswer: string
    explanation: string
  }>
}

/** 流式出题的最终 JSON 控制帧前缀：{"__quizFinal":true,"content":"<格式化后的 Markdown>"} */
export const QUIZ_FINAL_FRAME_PREFIX = '{"__quizFinal"'

/**
 * 构建流式出题的 SSE 地址。
 * <p>帧格式：模型原始 token（实时预览）+ 末尾一帧 {@link QUIZ_FINAL_FRAME_PREFIX} 开头的
 * JSON 控制帧（携带后端格式化后的标准 Markdown）。</p>
 */
export function buildQuizStreamUrl(courseId: number, topic: string | undefined, count: number) {
  const authStore = useAuthStore()
  const params = new URLSearchParams()
  params.append('courseId', String(courseId))
  if (topic && topic.trim()) params.append('topic', topic.trim())
  params.append('count', String(count))
  if (authStore.token) params.append('token', authStore.token)
  return `/api/quiz/generate-stream?${params.toString()}`
}

export function generateQuiz(courseId: number, topic?: string, count: number = 5) {
  // LLM 出题耗时较长，使用 AI 专用超时（同步兜底接口，主流程已改为流式）
  return post<string>('/api/quiz/generate', { courseId, topic, count }, { timeout: AI_TIMEOUT })
}

export function gradeQuiz(courseId: number, topic: string, questions: QuizQuestion[], answers: string[]) {
  return post<QuizGradingResult>('/api/quiz/grade', {
    courseId,
    topic,
    answers: questions.map((q, index) => ({
      questionNo: index + 1,
      question: q.question,
      options: q.options.map((opt, i) => `${String.fromCharCode(65 + i)}. ${opt}`).join('\n'),
      userAnswer: answers[index],
      correctAnswer: q.answer,
      explanation: q.explanation
    }))
  }).then(res => ({
    score: res.accuracyRate,
    total: res.totalQuestions,
    correctCount: res.correctCount,
    details: res.results.map(r => ({
      index: r.questionNo,
      correct: r.correct,
      userAnswer: r.userAnswer,
      correctAnswer: r.correctAnswer,
      explanation: r.explanation
    }))
  }))
}

interface QuizGradingResult {
  totalQuestions: number
  correctCount: number
  wrongCount: number
  accuracyRate: number
  results: Array<{
    questionNo: number
    question: string
    userAnswer: string
    correctAnswer: string
    correct: boolean
    explanation: string
  }>
}
