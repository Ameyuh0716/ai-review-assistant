<template>
  <AppLayout>
    <div class="page-header">
      <h2>复习计划</h2>
      <el-button type="primary" @click="generateDialogVisible = true">
        <el-icon><Calendar /></el-icon> 生成计划
      </el-button>
    </div>

    <el-row :gutter="16">
      <el-col v-for="plan in plans" :key="plan.id" :xs="24" :sm="12" :md="8">
        <el-card class="plan-card" shadow="hover">
          <div class="plan-header">
            <h3>{{ plan.courseName }}</h3>
            <div class="plan-actions">
              <el-button link type="primary" title="查看详情" class="zoom-btn" @click="openPreview(plan)">
                <el-icon><ZoomIn /></el-icon>
              </el-button>
              <el-button link type="danger" title="删除" @click="deletePlanItem(plan.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <p class="plan-time">
            <el-tag size="small" type="primary" effect="plain">{{ plan.availableDays }}</el-tag>
            <span class="plan-date">{{ formatTime(plan.createdAt) }}</span>
            <span v-if="planProgress(plan).percent > 0" class="plan-progress-tag">
              {{ planProgress(plan).percent }}%
            </span>
          </p>

          <!-- 可点击预览区：结构化日程列表 + 悬停浮层特效 -->
          <div
            class="plan-preview"
            role="button"
            tabindex="0"
            :aria-label="`查看 ${plan.courseName} 复习计划详情`"
            @click="openPreview(plan)"
            @keydown.enter.prevent="openPreview(plan)"
            @keydown.space.prevent="openPreview(plan)"
          >
            <ul v-if="plan.days && plan.days.length" class="plan-days">
              <li
                v-for="d in plan.days.slice(0, DAY_PREVIEW_LIMIT)"
                :key="d.day"
                :class="{ done: isDayDoneInPlan(plan, d.day) }"
              >
                <span class="d-badge">{{ d.day }}</span>
                <span class="d-title">{{ d.title || plan.courseName }}</span>
              </li>
              <li v-if="plan.days.length > DAY_PREVIEW_LIMIT" class="more">
                还有 {{ plan.days.length - DAY_PREVIEW_LIMIT }} 天…
              </li>
            </ul>
            <p v-else class="plan-plain">{{ plainPreview(plan.content) }}</p>

            <!-- 悬停遮罩与胶囊按钮：仅在支持 hover 的设备启用，触屏走点击路径 -->
            <div class="preview-overlay" aria-hidden="true">
              <span class="overlay-pill">
                <el-icon><ZoomIn /></el-icon>
                查看完整计划
              </span>
            </div>
          </div>

          <div class="plan-footer">
            <el-button text size="small" @click="openPreview(plan)">
              查看详情<el-icon class="footer-arrow"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="plans.length === 0" description="暂无复习计划" />

    <el-dialog
      v-model="previewVisible"
      :title="previewPlan?.courseName || '复习计划'"
      width="min(880px, 94vw)"
      class="plan-preview-dialog"
      @closed="onPreviewClosed"
    >
      <template v-if="previewPlan">
        <div class="preview-meta">
          <el-tag size="small" type="primary" effect="plain">{{ previewPlan.availableDays }}</el-tag>
          <span class="preview-date">创建于 {{ formatTime(previewPlan.createdAt) }}</span>
          <div class="meta-spacer" />
          <el-button v-if="planDays.length" link size="small" @click="showRaw = !showRaw">
            {{ showRaw ? '返回结构化视图' : '查看原始计划' }}
          </el-button>
        </div>

        <!-- 原始 Markdown 视图 -->
        <div v-if="showRaw" class="preview-content">
          <MarkdownRenderer :content="previewPlan.content" />
        </div>

        <!-- 结构化视图：按天勾选 + 逐项 AI 生成 -->
        <template v-else-if="planDays.length">
          <div class="plan-progress">
            <div class="progress-head">
              <span class="progress-text">{{ completedDays }}/{{ planDays.length }} 天已完成</span>
              <span class="progress-sub">
                {{ completedSections }}/{{ planDays.length * PLAN_SECTIONS.length }} 项学习任务已完成
              </span>
            </div>
            <el-progress :percentage="progressPercent" :stroke-width="8" :show-text="false" />
          </div>

          <div class="day-list">
            <div
              v-for="day in planDays"
              :key="day.day"
              class="day-card"
              :class="{ 'is-done': isDayDone(day.day) }"
            >
              <div class="day-head">
                <el-checkbox
                  class="day-check"
                  :model-value="isDayDone(day.day)"
                  @change="(v: any) => toggleDay(day.day, v)"
                />
                <span class="day-badge">第 {{ day.day }} 天</span>
                <span class="day-title">{{ day.title || previewPlan.courseName }}</span>
                <el-tag v-if="isDayDone(day.day)" size="small" type="success" effect="light">已完成</el-tag>
              </div>

              <ul v-if="day.items.length" class="day-items">
                <li v-for="(item, i) in day.items" :key="i">{{ item }}</li>
              </ul>

              <div class="section-list">
                <div v-for="sec in PLAN_SECTIONS" :key="sec.key" class="section">
                  <div class="section-head">
                    <el-checkbox
                      :model-value="isSectionDone(day.day, sec.key)"
                      @change="(v: any) => toggleSection(day.day, sec.key, v)"
                    />
                    <span class="section-name">{{ sec.label }}</span>
                    <span
                      v-if="generatingKey === sectionKey(day.day, sec.key)"
                      class="section-loading"
                    >
                      <el-icon class="is-loading"><Loading /></el-icon> 生成中…
                      <el-button link size="small" type="danger" @click="stopGenerate">停止</el-button>
                    </span>
                    <el-button
                      v-else
                      size="small"
                      :type="hasContent(day.day, sec.key) ? 'default' : 'primary'"
                      plain
                      @click="generate(day.day, sec.key)"
                    >
                      {{ hasContent(day.day, sec.key) ? '重新生成' : 'AI 生成' }}
                    </el-button>
                  </div>
                  <!-- 生成中的实时预览（原始 token 流） -->
                  <div v-if="generatingKey === sectionKey(day.day, sec.key)" class="section-streaming">
                    <pre>{{ streamingText }}<span class="stream-cursor">▊</span></pre>
                  </div>
                  <!-- 已生成内容 -->
                  <div v-else-if="hasContent(day.day, sec.key)" class="section-content">
                    <MarkdownRenderer :content="sectionContent(day.day, sec.key)" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 无分天结构：回退为纯文档展示 -->
        <div v-else class="preview-content">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="该计划未识别到分天结构，以下为完整内容"
            class="no-structure-tip"
          />
          <MarkdownRenderer :content="previewPlan.content" />
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="generateDialogVisible" title="生成复习计划" width="480px">
      <el-form :model="generateForm" label-width="80px">
        <el-form-item label="选择课程">
          <el-select v-model="generateForm.courseId" placeholder="选择课程" style="width: 100%">
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="复习天数">
          <el-slider v-model="generateForm.days" :min="1" :max="30" show-stops />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import * as courseApi from '@/api/course'
import * as planApi from '@/api/plan'
import { PLAN_SECTIONS } from '@/api/plan'
import type { Course } from '@/api/course'
import type { PlanDayState, PlanProgress, PlanSectionKey, StudyPlan } from '@/api/plan'
import { Calendar, Delete, ZoomIn, Loading, ArrowRight } from '@element-plus/icons-vue'

/** 卡片预览区展示的日程条数上限，超出显示“还有 N 天” */
const DAY_PREVIEW_LIMIT = 5

const courses = ref<Course[]>([])
const plans = ref<StudyPlan[]>([])
const generateDialogVisible = ref(false)
const generating = ref(false)
const previewVisible = ref(false)
const previewPlan = ref<StudyPlan | null>(null)
const generateForm = ref({ courseId: undefined as number | undefined, days: 7 })

/** 是否展示原始 Markdown（false = 结构化视图） */
const showRaw = ref(false)

// ---------- 结构化视图状态 ----------
/** 各天各环节的勾选与生成内容 */
const dayStates = ref<PlanProgress>({})
/** 正在生成的环节 key：`${day}-${section}`；null 表示空闲 */
const generatingKey = ref<string | null>(null)
/** 生成中的实时文本 */
const streamingText = ref('')
let dayEventSource: EventSource | null = null
let saveTimer: ReturnType<typeof setTimeout> | null = null

/** 后端解析出的每日结构 */
const planDays = computed(() => previewPlan.value?.days ?? [])

/** 已完成天数 */
const completedDays = computed(() => planDays.value.filter(d => isDayDone(d.day)).length)

/** 已勾选的学习任务项数（天 × 3 环节） */
const completedSections = computed(() => {
  let count = 0
  for (const day of planDays.value) {
    for (const sec of PLAN_SECTIONS) {
      if (isSectionDone(day.day, sec.key)) count++
    }
  }
  return count
})

/** 整体完成百分比 */
const progressPercent = computed(() => {
  const total = planDays.value.length * PLAN_SECTIONS.length
  if (total === 0) return 0
  return Math.round((completedSections.value / total) * 100)
})

onMounted(() => {
  loadCourses()
  loadPlans()
})

// 组件卸载时清理流与定时器，避免内存泄漏
onBeforeUnmount(() => {
  closeStream()
  if (saveTimer) clearTimeout(saveTimer)
})

async function loadCourses() {
  try {
    courses.value = await courseApi.listCourses()
  } catch (e) {
    console.error(e)
  }
}

async function loadPlans() {
  try {
    plans.value = await planApi.listPlans()
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
    const content = await planApi.generatePlan(generateForm.value.courseId, generateForm.value.days)
    const course = courses.value.find(c => c.id === generateForm.value.courseId)
    await planApi.savePlan(
      course?.name || '课程',
      `${generateForm.value.days}天`,
      content
    )
    ElMessage.success('生成成功')
    generateDialogVisible.value = false
    await loadPlans()
  } finally {
    generating.value = false
  }
}

/** 打开结构化预览 */
function openPreview(plan: StudyPlan) {
  previewPlan.value = plan
  dayStates.value = parseProgress(plan.progress)
  showRaw.value = false
  previewVisible.value = true
}

/** 关闭预览时清理流与待保存进度 */
function onPreviewClosed() {
  closeStream()
  // 立即落盘未保存的勾选，避免防抖窗口内关闭导致丢失
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
    persistProgress()
  }
}

/** 解析进度 JSON 字符串 */
function parseProgress(raw?: string | null): PlanProgress {
  if (!raw) return {}
  try {
    const obj = JSON.parse(raw)
    return obj && typeof obj === 'object' ? obj : {}
  } catch {
    return {}
  }
}

// ---------- 卡片进度与预览 ----------

/**
 * 统计计划完成度（用于卡片右上角进度标）。
 * <p>以「天 × 3 环节」为总任务量，统计已勾选环节的百分比与已完成天数。</p>
 *
 * @param plan 计划对象（days 由服务端解析填充）
 */
function planProgress(plan: StudyPlan) {
  const days = plan.days ?? []
  const total = days.length * PLAN_SECTIONS.length
  if (total === 0) return { percent: 0, doneDays: 0, totalDays: 0 }
  const prog = parseProgress(plan.progress)
  let doneSections = 0
  let doneDays = 0
  for (const d of days) {
    const state = prog[String(d.day)]
    if (!state) continue
    if (state.done) doneDays++
    for (const sec of PLAN_SECTIONS) {
      if (state[sec.key]?.done) doneSections++
    }
  }
  return {
    percent: Math.round((doneSections / total) * 100),
    doneDays,
    totalDays: days.length
  }
}

/** 判断卡片中某天是否已完成（用于日程条目的完成态样式） */
function isDayDoneInPlan(plan: StudyPlan, day: number) {
  return !!parseProgress(plan.progress)[String(day)]?.done
}

/** 无分天结构时的纯文本预览：去掉 markdown 标记后截取前 100 字 */
function plainPreview(content: string) {
  return (content || '')
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/[*_`>#-]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 100)
}

// ---------- 状态读取 ----------

function sectionKey(day: number, section: PlanSectionKey) {
  return `${day}-${section}`
}

function sectionState(day: number, section: PlanSectionKey) {
  return dayStates.value[String(day)]?.[section] ?? {}
}

function hasContent(day: number, section: PlanSectionKey) {
  return !!sectionState(day, section).content
}

function sectionContent(day: number, section: PlanSectionKey) {
  return sectionState(day, section).content ?? ''
}

function isSectionDone(day: number, section: PlanSectionKey) {
  return !!sectionState(day, section).done
}

function isDayDone(day: number) {
  return !!dayStates.value[String(day)]?.done
}

// ---------- 状态修改 ----------

/** 确保某天的状态对象存在，并返回可写引用 */
function ensureDay(day: number): PlanDayState {
  const key = String(day)
  if (!dayStates.value[key]) {
    dayStates.value[key] = {}
  }
  return dayStates.value[key]
}

/** 勾选整天：联动所有环节（勾选时同时勾上已有内容的环节） */
function toggleDay(day: number, val: unknown) {
  const done = !!val
  const state = ensureDay(day)
  state.done = done
  for (const sec of PLAN_SECTIONS) {
    const sectionStateObj = state[sec.key]
    if (done) {
      if (sectionStateObj?.content) sectionStateObj.done = true
    } else if (sectionStateObj) {
      sectionStateObj.done = false
    }
  }
  scheduleSave()
}

/** 勾选单个环节：全部环节完成时自动勾选整天 */
function toggleSection(day: number, section: PlanSectionKey, val: unknown) {
  const done = !!val
  const state = ensureDay(day)
  if (!state[section]) state[section] = {}
  state[section]!.done = done
  state.done = PLAN_SECTIONS.every(sec => state[sec.key]?.done)
  scheduleSave()
}

/** 防抖保存进度，避免连续勾选产生大量请求 */
function scheduleSave() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveTimer = null
    persistProgress()
  }, 400)
}

/** 将本地进度写回服务端，并同步本地 plan 对象 */
async function persistProgress() {
  const plan = previewPlan.value
  if (!plan) return
  const snapshot: PlanProgress = JSON.parse(JSON.stringify(dayStates.value))
  try {
    await planApi.updatePlanProgress(plan.id, snapshot)
    const raw = JSON.stringify(snapshot)
    if (previewPlan.value?.id === plan.id) previewPlan.value.progress = raw
    const idx = plans.value.findIndex(p => p.id === plan.id)
    if (idx >= 0) plans.value[idx].progress = raw
  } catch (e) {
    console.error(e)
    ElMessage.error('进度保存失败，请重试')
  }
}

// ---------- AI 生成 ----------

/** 对某天的某环节发起流式生成 */
function generate(day: number, section: PlanSectionKey) {
  const plan = previewPlan.value
  if (!plan) return
  if (generatingKey.value) {
    ElMessage.warning('正在生成中，请稍候')
    return
  }
  const key = sectionKey(day, section)
  generatingKey.value = key
  streamingText.value = ''
  const url = planApi.buildDayGenerateUrl(plan.id, day, section)
  dayEventSource = new EventSource(url)
  dayEventSource.onmessage = async (event) => {
    if (event.data === undefined || event.data === null) return
    // 完成控制帧：服务端已保存生成内容，主动关闭连接并刷新
    if (event.data.startsWith('{"__done"')) {
      closeStream()
      await reloadPlan()
      return
    }
    streamingText.value += event.data
  }
  dayEventSource.onerror = async () => {
    // 异常断开（或错误提示结束）：重新拉取详情，生成内容已落库
    const hadContent = streamingText.value.length > 0
    closeStream()
    if (hadContent) {
      await reloadPlan()
    }
  }
}

/** 用户主动停止生成 */
function stopGenerate() {
  closeStream()
}

/** 关闭事件流并复位生成状态 */
function closeStream() {
  if (dayEventSource) {
    dayEventSource.close()
    dayEventSource = null
  }
  generatingKey.value = null
  streamingText.value = ''
}

/** 重新拉取计划详情，合并服务端生成内容与本地勾选状态 */
async function reloadPlan() {
  const plan = previewPlan.value
  if (!plan) return
  try {
    const fresh = await planApi.getPlan(plan.id)
    if (previewPlan.value?.id !== plan.id) return
    previewPlan.value = fresh
    dayStates.value = mergeProgress(parseProgress(fresh.progress), dayStates.value)
    const idx = plans.value.findIndex(p => p.id === plan.id)
    if (idx >= 0) plans.value[idx] = { ...plans.value[idx], ...fresh }
  } catch (e) {
    console.error(e)
  }
}

/**
 * 合并进度：内容以服务端为准（刚生成的内容在服务端），
 * 勾选状态优先保留本地（用户可能刚点过且尚未保存完成）。
 */
function mergeProgress(server: PlanProgress, local: PlanProgress): PlanProgress {
  const merged: PlanProgress = JSON.parse(JSON.stringify(server))
  for (const [day, localState] of Object.entries(local)) {
    const mergedState = merged[day] ?? (merged[day] = {})
    if (localState.done !== undefined) mergedState.done = localState.done
    for (const sec of PLAN_SECTIONS) {
      const localSection = localState[sec.key]
      if (!localSection) continue
      const mergedSection = mergedState[sec.key] ?? (mergedState[sec.key] = {})
      if (localSection.done !== undefined) mergedSection.done = localSection.done
      if (!mergedSection.content) mergedSection.content = localSection.content
    }
  }
  return merged
}

async function deletePlanItem(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该计划？', '提示', { type: 'warning' })
    await planApi.deletePlan(id)
    ElMessage.success('已删除')
    await loadPlans()
  } catch (e) {
    // cancel
  }
}

function formatTime(d?: string) {
  if (!d) return ''
  const t = new Date(d)
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
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
/* ===== 计划卡片：与课程卡片保持一致的视觉语言 ===== */
.plan-card {
  margin-bottom: 16px;
  border-radius: var(--radius-md);
  position: relative;
  overflow: hidden;
  transition: transform var(--t-normal) var(--ease-spring), box-shadow var(--t-normal) var(--ease);
}
/* 左侧渐变装饰条: 悬停时点亮（与课程卡片一致） */
.plan-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--gradient-brand);
  opacity: 0;
  transition: opacity var(--t-normal) var(--ease);
  z-index: 2;
}
.plan-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}
.plan-card:hover::before {
  opacity: 1;
}
.plan-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.plan-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.2px;
}
.plan-actions {
  display: flex;
  gap: 4px;
}
/* 放大图标：卡片悬停时轻微放大回弹 */
.zoom-btn :deep(.el-icon) {
  transition: transform var(--t-normal) var(--ease-spring);
}
.plan-card:hover .zoom-btn :deep(.el-icon) {
  transform: scale(1.18) rotate(6deg);
}
.plan-time {
  color: var(--color-muted-foreground);
  font-size: 12px;
  margin: 0 0 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.plan-date {
  color: var(--color-muted-foreground);
}
/* 完成度徽标：右对齐，与天数标签形成信息层次 */
.plan-progress-tag {
  margin-left: auto;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-100);
}
/* ---------- 可点击预览区 ---------- */
.plan-preview {
  position: relative;
  min-height: 132px;
  max-height: 176px;
  overflow: hidden;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-muted);
  cursor: pointer;
  transition: background var(--t-fast) var(--ease), box-shadow var(--t-fast) var(--ease);
  -webkit-tap-highlight-color: transparent;
}
.plan-preview:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
/* 按下时的轻微形变：提供触觉反馈 */
.plan-preview:active {
  transform: scale(0.985);
  transition-duration: var(--t-fast);
}
/* 日程条目：编号徽标 + 标题，完成态变绿并无衬线划除 */
.plan-days {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.plan-days li {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-muted-foreground);
}
.d-badge {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-100);
  transition: transform var(--t-fast) var(--ease-spring), background var(--t-fast) var(--ease);
}
.d-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-days li.done .d-badge {
  color: var(--color-success);
  background: var(--color-success-light);
  border-color: var(--color-success);
}
.plan-days li.done .d-title {
  color: var(--color-success);
  text-decoration: line-through;
  text-decoration-color: rgba(5, 150, 105, 0.35);
}
.plan-days li.more {
  justify-content: center;
  font-size: 12px;
  opacity: 0.8;
}
.plan-plain {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-muted-foreground);
}
/* ---------- 悬停浮层：渐变遮罩 + 胶囊按钮上浮 ---------- */
.preview-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, rgba(124, 58, 237, 0.03) 0%, rgba(124, 58, 237, 0.16) 100%);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--t-normal) var(--ease);
}
.overlay-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--gradient-primary);
  box-shadow: var(--shadow-primary);
  opacity: 0;
  transform: translateY(10px) scale(0.94);
  transition: transform var(--t-normal) var(--ease-spring), opacity var(--t-normal) var(--ease);
}
/* 仅在支持 hover 的设备启用悬浮特效（触屏走点击路径，避免状态“粘滞”） */
@media (hover: hover) and (pointer: fine) {
  .plan-preview:hover {
    background: var(--color-primary-50);
  }
  .plan-preview:hover .preview-overlay {
    opacity: 1;
  }
  .plan-preview:hover .overlay-pill {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  .plan-preview:hover .d-badge {
    transform: scale(1.1);
  }
}
.plan-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.plan-footer :deep(.el-button) {
  font-size: 13px;
}
.footer-arrow {
  margin-left: 2px;
  transition: transform var(--t-fast) var(--ease);
}
.plan-footer :deep(.el-button:hover) .footer-arrow {
  transform: translateX(3px);
}
/* 降低动效偏好：关闭位移与缩放，仅保留颜色反馈 */
@media (prefers-reduced-motion: reduce) {
  .plan-card,
  .plan-card:hover,
  .plan-preview:active,
  .zoom-btn :deep(.el-icon),
  .plan-card:hover .zoom-btn :deep(.el-icon),
  .overlay-pill,
  .d-badge,
  .footer-arrow {
    transform: none;
    transition: none;
  }
}
.preview-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.meta-spacer {
  flex: 1;
}
.preview-date {
  color: var(--color-muted-foreground);
  font-size: 13px;
}
.preview-content {
  max-height: 65vh;
  overflow-y: auto;
  padding-right: 6px;
  font-size: 14px;
  line-height: 1.75;
}
.no-structure-tip {
  margin-bottom: 12px;
}
/* ---------- 结构化视图 ---------- */
.plan-progress {
  margin-bottom: 16px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-100);
}
.progress-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
  gap: 8px;
}
.progress-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary-dark);
}
.progress-sub {
  font-size: 12px;
  color: var(--color-muted-foreground);
}
.day-list {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.day-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  background: var(--color-card);
  transition: border-color var(--t-fast) var(--ease), box-shadow var(--t-fast) var(--ease);
}
.day-card:hover {
  border-color: var(--color-primary-200);
  box-shadow: var(--shadow-sm);
}
/* 已完成的整天使用柔和绿底提示 */
.day-card.is-done {
  background: var(--color-success-light);
  border-color: var(--color-success);
}
.day-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.day-badge {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: var(--gradient-primary);
}
.day-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-foreground);
}
.day-items {
  margin: 10px 0 4px;
  padding-left: 22px;
  color: var(--color-muted-foreground);
  font-size: 13px;
  line-height: 1.8;
}
.section-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.section {
  border-radius: var(--radius-sm);
  background: var(--color-muted);
  padding: 10px 12px;
}
.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-foreground);
  flex: 1;
}
.section-loading {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-primary);
}
.section-streaming {
  margin-top: 8px;
  max-height: 200px;
  overflow-y: auto;
}
.section-streaming pre {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-card);
  border: 1px dashed var(--color-primary-200);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-muted-foreground);
}
.stream-cursor {
  color: var(--color-primary);
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.section-content {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  font-size: 13px;
  line-height: 1.7;
  max-height: 320px;
  overflow-y: auto;
}
</style>
