<template>
  <AppLayout>
    <div class="page-header">
      <h2>学习统计</h2>
      <el-button :icon="Refresh" circle title="刷新" :loading="loading" @click="loadStats" />
    </div>

    <!-- 加载失败时使用内联提示，避免浮动报错打扰用户 -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="warning"
      show-icon
      closable
      class="load-error"
      @close="loadError = ''"
    />

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="12" :md="6">
        <el-tooltip content="综合评分 = 课程覆盖 30 + 复习频率 30 + 活跃天数 20 + 连续天数 20" placement="top">
          <el-card class="stat-card">
            <el-statistic title="综合评分" :value="overview.overallScore" />
            <p class="stat-sub">已复习 {{ overview.reviewedCourses }}/{{ overview.totalCourses }} 门课程</p>
          </el-card>
        </el-tooltip>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-tooltip content="累计有复习记录的日期天数" placement="top">
          <el-card class="stat-card">
            <el-statistic title="活跃天数" :value="overview.activeDays" />
            <p class="stat-sub">共 {{ overview.totalReviews }} 次复习</p>
          </el-card>
        </el-tooltip>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-tooltip content="从今天（或昨天）起连续有复习记录的天数" placement="top">
          <el-card class="stat-card">
            <el-statistic title="连续复习" :value="overview.streakDays">
              <template #suffix>天</template>
            </el-statistic>
            <p class="stat-sub">保持连续，加油！</p>
          </el-card>
        </el-tooltip>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-tooltip content="已掌握错题数 / 错题总数" placement="top">
          <el-card class="stat-card">
            <el-statistic title="掌握率" :value="wrongStats.masteryRate ?? 0">
              <template #suffix>%</template>
            </el-statistic>
            <p class="stat-sub">{{ wrongStats.mastered }}/{{ wrongStats.total }} 题已掌握</p>
          </el-card>
        </el-tooltip>
      </el-col>
    </el-row>

    <el-card class="wrong-book-card">
      <template #header>
        <div class="card-header">
          <span>错题本</span>
          <span class="sub">待复习 {{ wrongStats.unmastered ?? 0 }} 道 · 已掌握 {{ wrongStats.mastered ?? 0 }} 道</span>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="filters">
        <el-select v-model="filterStatus" class="filter-item" placeholder="全部状态" style="width: 130px">
          <el-option label="全部状态" value="" />
          <el-option label="待复习" value="unmastered" />
          <el-option label="已掌握" value="mastered" />
        </el-select>
        <el-select v-model="filterCourse" class="filter-item" placeholder="全部课程" style="width: 160px">
          <el-option label="全部课程" value="" />
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="String(c.id)" />
        </el-select>
        <el-select v-model="filterTopic" class="filter-item" placeholder="全部知识点" style="width: 170px">
          <el-option label="全部知识点" value="" />
          <el-option v-for="t in topics" :key="t" :label="t" :value="t" />
        </el-select>
        <el-input
          v-model="filterKeyword"
          class="filter-item filter-search"
          placeholder="搜索题干 / 解析关键词"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <el-empty v-if="allWrong.length === 0" description="暂无错题，继续保持" />
      <el-empty v-else-if="filteredWrong.length === 0" description="没有符合筛选条件的错题">
        <el-button link type="primary" @click="resetFilters">重置筛选</el-button>
      </el-empty>
      <div v-else class="wrong-list">
        <div
          v-for="item in filteredWrong"
          :key="item.id"
          :class="['wrong-item', { mastered: item.isMastered }]"
          role="button"
          tabindex="0"
          @click="openDetail(item)"
          @keydown.enter="openDetail(item)"
        >
          <div class="wrong-header">
            <div class="wrong-tags">
              <el-tag v-if="item.isMastered" type="success">已掌握</el-tag>
              <el-tag v-else type="danger">待复习</el-tag>
              <el-tag v-if="item.wrongCount > 1" type="warning" effect="plain">错 {{ item.wrongCount }} 次</el-tag>
              <el-tag v-if="item.topic" type="info" effect="plain">{{ item.topic }}</el-tag>
              <el-tag v-if="courseName(item.courseId)" type="primary" effect="plain">
                {{ courseName(item.courseId) }}
              </el-tag>
            </div>
            <div class="wrong-actions" @click.stop>
              <el-button v-if="!item.isMastered" link type="success" @click="masterItem(item.id)">
                标记掌握
              </el-button>
              <el-button link type="danger" @click="deleteItem(item.id)">删除</el-button>
            </div>
          </div>
          <p class="question">{{ item.question }}</p>
          <p class="answer">你的答案：{{ item.userAnswer || '未作答' }} | 正确答案：{{ item.correctAnswer }}</p>
          <div class="item-footer">
            <span class="detail-link">点击查看详情并重做</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 错题详情 + 重做弹窗 -->
    <el-dialog v-model="detailVisible" title="错题详情" width="620px" class="detail-dialog">
      <div v-if="detailItem" class="detail-body">
        <div class="detail-tags">
          <el-tag v-if="detailItem.isMastered" type="success">已掌握</el-tag>
          <el-tag v-else type="danger">待复习</el-tag>
          <el-tag v-if="detailItem.wrongCount > 1" type="warning" effect="plain">
            错 {{ detailItem.wrongCount }} 次
          </el-tag>
          <el-tag v-if="detailItem.topic" type="info" effect="plain">{{ detailItem.topic }}</el-tag>
        </div>

        <p class="detail-question">{{ detailItem.question }}</p>

        <div v-if="detailOptions.length" class="option-list">
          <div
            v-for="opt in detailOptions"
            :key="opt.letter"
            :class="['option', optionClass(opt.letter)]"
            role="button"
            tabindex="0"
            @click="selectOption(opt.letter)"
            @keydown.enter="selectOption(opt.letter)"
          >
            <span class="opt-letter">{{ opt.letter }}</span>
            <span class="opt-text">{{ opt.text }}</span>
          </div>
        </div>
        <p v-else class="no-options">（该题未存储选项，请根据下方答案自行复盘）</p>

        <div v-if="redoResult" :class="['redo-result', redoResult.correct ? 'ok' : 'bad']">
          <p class="redo-title">
            {{ redoResult.correct ? '✓ 回答正确，已自动标记为掌握' : '✗ 回答错误，再回顾一下解析吧' }}
          </p>
          <p v-if="!redoResult.correct" class="redo-line">
            正确答案：<strong>{{ redoResult.correctAnswer }}</strong>
          </p>
          <p class="redo-exp">解析：{{ redoResult.explanation || '暂无解析' }}</p>
        </div>
        <div v-else class="history-info">
          <p>上次你的答案：<strong>{{ detailItem.userAnswer || '未作答' }}</strong> ｜ 正确答案：<strong>{{ detailItem.correctAnswer }}</strong></p>
          <p v-if="detailItem.explanation" class="history-exp">解析：{{ detailItem.explanation }}</p>
          <p class="redo-hint">选择一个选项后点击“提交重做”，答对将自动标记为已掌握</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="redoResult && !redoResult.correct" type="warning" plain @click="retryRedo">
          再做一次
        </el-button>
        <el-button
          v-if="!redoResult"
          type="primary"
          :loading="redoing"
          :disabled="!selectedOption"
          @click="submitRedo"
        >
          提交重做
        </el-button>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import * as statsApi from '@/api/stats'
import * as courseApi from '@/api/course'
import type { StatsOverview, WrongStats, WrongAnswer, RedoResult } from '@/api/stats'
import type { Course } from '@/api/course'
import { Refresh, Search } from '@element-plus/icons-vue'

const overview = ref<StatsOverview>({
  userId: '',
  totalCourses: 0,
  reviewedCourses: 0,
  totalReviews: 0,
  activeDays: 0,
  streakDays: 0,
  lastReviewTime: null,
  overallScore: 0
})
const wrongStats = ref<WrongStats>({ total: 0, mastered: 0, unmastered: 0, masteryRate: 0 })
const allWrong = ref<WrongAnswer[]>([])
const courses = ref<Course[]>([])
const loading = ref(false)
const loadError = ref('')

// ---------- 筛选 ----------
const filterStatus = ref('')
const filterCourse = ref('')
const filterTopic = ref('')
const filterKeyword = ref('')

/** 全部可选知识点（去重，按题量降序） */
const topics = computed(() => {
  const map = new Map<string, number>()
  for (const item of allWrong.value) {
    const t = item.topic?.trim()
    if (t) map.set(t, (map.get(t) || 0) + 1)
  }
  return [...map.entries()].sort((a, b) => b[1] - a[1]).map(([t]) => t)
})

/** 应用筛选条件后的错题列表（前端过滤，交互即时响应） */
const filteredWrong = computed(() => {
  const keyword = filterKeyword.value.trim().toLowerCase()
  return allWrong.value.filter(item => {
    if (filterStatus.value === 'unmastered' && item.isMastered) return false
    if (filterStatus.value === 'mastered' && !item.isMastered) return false
    if (filterCourse.value && String(item.courseId ?? '') !== filterCourse.value) return false
    if (filterTopic.value && (item.topic || '') !== filterTopic.value) return false
    if (keyword) {
      const haystack = `${item.question} ${item.topic || ''} ${item.explanation || ''}`.toLowerCase()
      if (!haystack.includes(keyword)) return false
    }
    return true
  })
})

function resetFilters() {
  filterStatus.value = ''
  filterCourse.value = ''
  filterTopic.value = ''
  filterKeyword.value = ''
}

function courseName(courseId: number | null) {
  if (courseId == null) return ''
  return courses.value.find(c => c.id === courseId)?.name || ''
}

// ---------- 数据加载（各接口相互独立，单个失败不影响其余展示） ----------
onMounted(() => {
  loadStats()
})

async function loadStats() {
  loading.value = true
  loadError.value = ''
  const [overviewRes, wrongStatsRes, wrongListRes, coursesRes] = await Promise.allSettled([
    statsApi.getOverview(),
    statsApi.getWrongStats(),
    statsApi.listWrongAnswers(),
    courseApi.listCourses()
  ])
  if (overviewRes.status === 'fulfilled') overview.value = overviewRes.value
  if (wrongStatsRes.status === 'fulfilled') wrongStats.value = wrongStatsRes.value
  if (wrongListRes.status === 'fulfilled') allWrong.value = wrongListRes.value
  if (coursesRes.status === 'fulfilled') courses.value = coursesRes.value
  // 仅当核心接口全部失败时给出内联提示（局部失败静默降级，不弹浮动报错）
  if (overviewRes.status === 'rejected' && wrongListRes.status === 'rejected') {
    loadError.value = '统计数据加载失败，请检查网络后点击右上角刷新重试'
  }
  loading.value = false
}

async function masterItem(id: number) {
  try {
    await statsApi.masterWrongAnswer(id)
    ElMessage.success('已标记掌握')
    await loadStats()
    if (detailItem.value?.id === id) syncDetailItem()
  } catch (e) {
    console.error(e)
  }
}

async function deleteItem(id: number) {
  try {
    await ElMessageBox.confirm('确定删除这道错题？', '提示', { type: 'warning' })
    await statsApi.deleteWrongAnswer(id)
    ElMessage.success('已删除')
    if (detailItem.value?.id === id) detailVisible.value = false
    await loadStats()
  } catch (e) {
    // cancel
  }
}

// ---------- 详情 & 重做 ----------
const detailVisible = ref(false)
const detailItem = ref<WrongAnswer | null>(null)
const redoing = ref(false)
const selectedOption = ref('')
const redoResult = ref<RedoResult | null>(null)

/** 解析题干选项：兼容 JSON 对象/数组与「A. xxx」文本行两种存储格式 */
const detailOptions = computed<Array<{ letter: string; text: string }>>(() => {
  const raw = detailItem.value?.options
  if (!raw || !raw.trim()) return []
  const text = raw.trim()
  // JSON 形式
  if (text.startsWith('{') || text.startsWith('[')) {
    try {
      const obj = JSON.parse(text)
      if (Array.isArray(obj)) {
        return obj.map((item, i) => ({
          letter: String.fromCharCode(65 + i),
          text: String(item).replace(/^[A-Da-d][.．、:：)]\s*/, '')
        }))
      }
      return Object.entries(obj).map(([k, v]) => ({
        letter: String(k).toUpperCase(),
        text: String(v).replace(/^[A-Da-d][.．、:：)]\s*/, '')
      }))
    } catch {
      // 解析失败时按文本行处理
    }
  }
  // 文本行形式：「A. xxx」逐行拆分，无前缀的行拼接到上一项
  const result: Array<{ letter: string; text: string }> = []
  for (const line of text.split(/\r?\n/).map(l => l.trim()).filter(Boolean)) {
    const m = line.match(/^([A-Da-d])[.．、:：)]\s*(.+)$/)
    if (m) {
      result.push({ letter: m[1].toUpperCase(), text: m[2].trim() })
    } else if (result.length) {
      result[result.length - 1].text += ' ' + line
    }
  }
  return result
})

function openDetail(item: WrongAnswer) {
  detailItem.value = item
  selectedOption.value = ''
  redoResult.value = null
  detailVisible.value = true
}

function selectOption(letter: string) {
  // 已提交结果后不再允许改选
  if (redoResult.value) return
  selectedOption.value = letter
}

/** 选项样式：提交后标出正确/错误项，提交前标出选中项 */
function optionClass(letter: string) {
  const correctLetter = normalizeAnswer(detailItem.value?.correctAnswer || '')
  if (redoResult.value) {
    if (letter === correctLetter) return 'is-correct'
    if (letter === selectedOption.value && !redoResult.value.correct) return 'is-wrong'
    return 'is-dim'
  }
  return letter === selectedOption.value ? 'is-selected' : ''
}

function normalizeAnswer(answer: string) {
  const m = answer.trim().match(/^([A-Da-d])/)
  return m ? m[1].toUpperCase() : ''
}

async function submitRedo() {
  if (!detailItem.value || !selectedOption.value) return
  redoing.value = true
  try {
    const res = await statsApi.redoWrongAnswer(detailItem.value.id, selectedOption.value)
    redoResult.value = res
    if (res.correct) {
      ElMessage.success('回答正确，已标记为掌握')
    }
    await loadStats()
    // 同步详情弹窗中的字段（错误次数/掌握状态），避免界面显示陈旧数据
    syncDetailItem()
  } catch (e) {
    console.error(e)
  } finally {
    redoing.value = false
  }
}

/** 从最新列表中同步详情弹窗的数据对象 */
function syncDetailItem() {
  if (!detailItem.value) return
  const fresh = allWrong.value.find(item => item.id === detailItem.value!.id)
  if (fresh) detailItem.value = fresh
}

function retryRedo() {
  selectedOption.value = ''
  redoResult.value = null
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
.load-error {
  margin-bottom: 16px;
}
.stats-row {
  margin-bottom: 24px;
}
.stat-card {
  border-radius: var(--radius-md);
  text-align: center;
  position: relative;
  overflow: hidden;
  transition: transform var(--t-normal) var(--ease), box-shadow var(--t-normal) var(--ease);
}
/* 顶部渐变装饰条: 增加活力 */
.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-brand);
  opacity: 0.9;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}
.stat-sub {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-muted-foreground);
}
.wrong-book-card {
  border-radius: var(--radius-md);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header .sub {
  color: var(--color-muted-foreground);
  font-size: 13px;
}
/* 筛选栏: 紧凑一行，窄屏自动换行 */
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
.filter-search {
  width: 220px;
  flex: 1;
  min-width: 180px;
}
.wrong-item {
  padding: 16px;
  border-radius: var(--radius-md);
  background: var(--color-muted);
  border: 1px solid transparent;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all var(--t-fast) var(--ease);
}
.wrong-item:hover {
  border-color: var(--color-primary-200);
  background: var(--color-primary-50);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}
.wrong-item:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.wrong-item.mastered {
  background: var(--color-success-light);
  opacity: 0.85;
}
.wrong-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}
.wrong-tags {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}
.question {
  font-weight: 600;
  margin: 0 0 8px;
}
.answer {
  color: var(--color-muted-foreground);
  font-size: 13px;
  margin: 0 0 8px;
}
.item-footer {
  display: flex;
  justify-content: flex-end;
}
.detail-link {
  font-size: 12px;
  color: var(--color-primary);
  opacity: 0;
  transition: opacity var(--t-fast) var(--ease);
}
.wrong-item:hover .detail-link,
.wrong-item:focus-visible .detail-link {
  opacity: 1;
}
/* 详情弹窗 */
.detail-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.detail-question {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
  margin: 0 0 16px;
}
.option-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.option {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card);
  cursor: pointer;
  transition: all var(--t-fast) var(--ease);
}
.option:hover {
  border-color: var(--color-primary-light);
  background: var(--color-primary-50);
}
.option:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.option.is-selected {
  border-color: var(--color-primary);
  background: var(--color-primary-50);
  box-shadow: 0 0 0 3px var(--color-primary-100);
}
.option.is-correct {
  border-color: var(--color-success);
  background: var(--color-success-light);
}
.option.is-wrong {
  border-color: var(--color-danger);
  background: var(--color-danger-light);
}
.option.is-dim {
  opacity: 0.6;
}
.opt-letter {
  font-weight: 700;
  color: var(--color-primary-dark);
  flex-shrink: 0;
}
.opt-text {
  line-height: 1.6;
}
.no-options {
  color: var(--color-muted-foreground);
  font-size: 13px;
  margin: 0 0 16px;
}
.history-info {
  font-size: 13px;
  color: var(--color-muted-foreground);
  border-top: 1px dashed var(--color-border);
  padding-top: 12px;
}
.history-exp {
  margin: 6px 0;
}
.redo-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--color-primary);
}
.redo-result {
  padding: 12px 14px;
  border-radius: var(--radius-md);
  border-left: 3px solid transparent;
}
.redo-result.ok {
  background: var(--color-success-light);
  border-left-color: var(--color-success);
}
.redo-result.bad {
  background: var(--color-danger-light);
  border-left-color: var(--color-danger);
}
.redo-title {
  font-weight: 600;
  margin: 0 0 6px;
}
.redo-line {
  margin: 0 0 6px;
}
.redo-exp {
  margin: 0;
  font-size: 13px;
  color: var(--color-muted-foreground);
}
</style>
