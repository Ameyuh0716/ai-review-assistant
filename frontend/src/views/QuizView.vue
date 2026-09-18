<template>
  <AppLayout>
    <div class="page-header">
      <h2>交互练习</h2>
      <el-button type="primary" @click="generateDialogVisible = true">
        <el-icon><EditPen /></el-icon> AI 出题
      </el-button>
    </div>

    <el-card v-if="questions.length === 0" class="empty-card">
      <el-empty description="暂无题目，点击右上角让 AI 出题">
        <template #image>
          <el-icon :size="60" color="#cbd5e1"><EditPen /></el-icon>
        </template>
      </el-empty>
    </el-card>

    <template v-else>
      <el-card v-for="(q, index) in questions" :key="index" class="quiz-card">
        <div class="question-title">{{ index + 1 }}. {{ q.question }}</div>
        <el-radio-group v-model="answers[index]" class="options">
          <el-radio v-for="(opt, optIndex) in q.options" :key="optIndex" :label="String.fromCharCode(65 + optIndex)">
            {{ String.fromCharCode(65 + optIndex) }}. {{ opt }}
          </el-radio>
        </el-radio-group>
      </el-card>

      <div class="quiz-actions">
        <el-button type="success" size="large" :loading="grading" @click="submitQuiz">
          提交批改
        </el-button>
        <el-button size="large" @click="questions = []; answers = []; result = null">
          重置
        </el-button>
      </div>
    </template>

    <el-dialog v-model="generateDialogVisible" title="AI 出题" width="480px">
      <el-form :model="generateForm" label-width="80px">
        <el-form-item label="选择课程">
          <el-select v-model="generateForm.courseId" placeholder="选择课程" style="width: 100%">
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目主题">
          <el-input v-model="generateForm.topic" placeholder="例如：进程同步" />
        </el-form-item>
        <el-form-item label="题目数量">
          <el-slider v-model="generateForm.count" :min="1" :max="10" show-stops />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="批改结果" width="520px">
      <div v-if="result" class="result-summary">
        <el-statistic title="得分" :value="result.score" suffix="分" />
        <el-statistic title="正确率" :value="Math.round((result.correctCount / result.total) * 100)" suffix="%" />
      </div>
      <div v-if="result" class="result-details">
        <div v-for="(d, i) in result.details" :key="i" :class="['detail-item', d.correct ? 'correct' : 'wrong']">
          <p><strong>第 {{ i + 1 }} 题</strong> {{ d.correct ? '✓ 正确' : '✗ 错误' }}</p>
          <p>你的答案：{{ d.userAnswer || '未作答' }} | 正确答案：{{ d.correctAnswer }}</p>
          <p class="explanation">解析：{{ d.explanation }}</p>
        </div>
      </div>
    </el-dialog>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import * as courseApi from '@/api/course'
import * as quizApi from '@/api/quiz'
import type { Course } from '@/api/course'
import type { QuizQuestion, QuizResult } from '@/api/quiz'
import { EditPen } from '@element-plus/icons-vue'

const courses = ref<Course[]>([])
const questions = ref<QuizQuestion[]>([])
const answers = ref<string[]>([])
const result = ref<QuizResult | null>(null)
const resultVisible = ref(false)
const generating = ref(false)
const grading = ref(false)
const generateDialogVisible = ref(false)
const generateForm = ref({ courseId: undefined as number | undefined, topic: '', count: 5 })
const currentQuizMeta = ref({ courseId: undefined as number | undefined, topic: '' })

onMounted(() => {
  loadCourses()
})

async function loadCourses() {
  try {
    courses.value = await courseApi.listCourses()
  } catch (e) {
    console.error(e)
  }
}

async function handleGenerate() {
  if (!generateForm.value.courseId) {
    ElMessage.warning('请选择课程')
    return
  }
  generating.value = true
  try {
    const markdown = await quizApi.generateQuiz(
      generateForm.value.courseId,
      generateForm.value.topic,
      generateForm.value.count
    )
    questions.value = parseQuestions(markdown)
    answers.value = new Array(questions.value.length).fill('')
    currentQuizMeta.value = {
      courseId: generateForm.value.courseId,
      topic: generateForm.value.topic || courses.value.find(c => c.id === generateForm.value.courseId)?.name || ''
    }
    generateDialogVisible.value = false
  } finally {
    generating.value = false
  }
}

function parseQuestions(md: string): QuizQuestion[] {
  const list: QuizQuestion[] = []
  const blocks = md.split(/\n(?=#{1,3}\s*题目)/).filter(b => b.trim())
  for (const block of blocks) {
    // 标题行格式: ### 题目 N：科目名 (冒号后是科目, 不是题干)
    const titleMatch = block.match(/#{1,3}\s*题目\s*\d*[：:\s]*(.*)/)
    if (!titleMatch) continue

    // 去掉标题行, 剩余内容为题干 + 选项 + 答案
    const firstNewline = block.indexOf('\n')
    const rest = firstNewline >= 0 ? block.slice(firstNewline + 1) : ''

    // 题干: 从剩余内容开头到第一个选项行 (A. / B. ...) 之间的文本
    const firstOptIdx = rest.search(/(?:^|\n)\s*[A-D][.．、]\s/)
    const questionText = (firstOptIdx > 0 ? rest.slice(0, firstOptIdx) : rest)
      .replace(/\*\*答案[：:][\s\S]*$/, '')
      .trim()

    const opts: string[] = []
    const optMatches = rest.matchAll(/\n\s*([A-D])[.．、]\s*(.+?)(?=\n\s*[A-D][.．、]|\n\s*答案|\n\s*\*\*答案|$)/g)
    for (const m of optMatches) {
      opts.push(m[2].trim())
    }
    const answerMatch = rest.match(/\*\*答案[：:]\s*([A-D])\*\*/)
    const explainMatch = rest.match(/\*\*解析[：:]\*\*\s*([\s\S]*?)(?=\n#{1,3}\s*题目|$)/)
    if (questionText && opts.length >= 2) {
      list.push({
        question: questionText,
        options: opts,
        answer: answerMatch ? answerMatch[1] : '',
        explanation: explainMatch ? explainMatch[1].trim() : ''
      })
    }
  }
  return list
}

async function submitQuiz() {
  if (answers.value.some(a => !a)) {
    ElMessage.warning('请回答所有题目')
    return
  }
  if (!currentQuizMeta.value.courseId) {
    ElMessage.warning('题目信息缺失，请重新生成')
    return
  }
  grading.value = true
  try {
    result.value = await quizApi.gradeQuiz(
      currentQuizMeta.value.courseId,
      currentQuizMeta.value.topic,
      questions.value,
      answers.value
    )
    resultVisible.value = true
  } finally {
    grading.value = false
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.empty-card {
  border-radius: var(--radius-md);
  padding: 40px 0;
}
.quiz-card {
  margin-bottom: 16px;
  border-radius: var(--radius-md);
  transition: box-shadow var(--t-normal) var(--ease);
}
.quiz-card:hover {
  box-shadow: var(--shadow-md);
}
.question-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  line-height: 1.5;
}
.options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
/* 选项: 优雅卡片式交互 */
.options :deep(.el-radio) {
  display: flex;
  align-items: flex-start;
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 12px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card);
  transition: all var(--t-fast) var(--ease);
  white-space: normal;
}
.options :deep(.el-radio:hover) {
  border-color: var(--color-primary-light);
  background: var(--color-primary-50);
  transform: translateX(2px);
}
.options :deep(.el-radio.is-checked) {
  border-color: var(--color-primary);
  background: var(--color-primary-50);
  box-shadow: 0 0 0 3px var(--color-primary-100);
}
.options :deep(.el-radio__label) {
  font-size: 14px;
  line-height: 1.6;
  white-space: normal;
  color: var(--color-foreground);
}
.options :deep(.el-radio.is-checked .el-radio__label) {
  color: var(--color-primary-dark);
  font-weight: 500;
}
.quiz-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 24px;
}
.result-summary {
  display: flex;
  gap: 32px;
  justify-content: center;
  margin-bottom: 20px;
}
.detail-item {
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
  border-left: 3px solid transparent;
}
.detail-item.correct {
  background: var(--color-success-light);
  border-left-color: var(--color-success);
}
.detail-item.wrong {
  background: var(--color-danger-light);
  border-left-color: var(--color-danger);
}
.explanation {
  color: var(--color-muted-foreground);
  font-size: 13px;
  margin-top: 8px;
}
</style>
