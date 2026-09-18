import { post, AI_TIMEOUT } from './request'

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

export function generateQuiz(courseId: number, topic?: string, count: number = 5) {
  // LLM 出题耗时较长，使用 AI 专用超时
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
