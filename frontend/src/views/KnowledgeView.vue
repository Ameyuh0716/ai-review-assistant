<template>
  <AppLayout>
    <div class="page-header">
      <h2>知识库</h2>
      <div class="header-actions">
        <el-select v-model="selectedCourseId" placeholder="选择课程" clearable style="width: 200px">
          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.name"
            :value="course.id"
          />
        </el-select>
        <el-upload
          v-if="selectedCourseId"
          :action="uploadAction"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="customUpload"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
        >
          <el-button type="primary">
            <el-icon><Upload /></el-icon> 上传文档
          </el-button>
        </el-upload>
        <el-button v-if="selectedCourseId" type="danger" plain @click="clearKnowledge">
          <el-icon><Delete /></el-icon> 清空
        </el-button>
      </div>
    </div>

    <el-card v-if="selectedCourseId" class="knowledge-card">
      <div class="stats-row">
        <el-statistic title="复习资料" :value="documentCount" />
        <el-statistic title="文档分块数" :value="chunkCount" class="stat-gap" />
      </div>

      <el-empty v-if="!loading && documents.length === 0" description="暂无复习资料，点击右上角上传" />

      <!-- 资料列表：默认展示上传的复习资料，点击卡片展开分块明细 -->
      <div v-else v-loading="loading" class="doc-list">
        <div v-for="doc in documents" :key="doc.name" class="doc-item">
          <div
            class="doc-head"
            role="button"
            tabindex="0"
            :aria-expanded="expandedDoc === doc.name"
            @click="toggleDoc(doc.name)"
            @keydown.enter.prevent="toggleDoc(doc.name)"
            @keydown.space.prevent="toggleDoc(doc.name)"
          >
            <span class="doc-icon" aria-hidden="true">
              <el-icon><Document /></el-icon>
            </span>
            <div class="doc-info">
              <span class="doc-name">{{ doc.name }}</span>
              <span class="doc-meta">{{ doc.chunkCount }} 个分块</span>
            </div>
            <!-- 预览入口：查看上传的完整源文件（阻止冒泡，避免同时触发展开分块） -->
            <el-button
              class="doc-preview-btn"
              size="small"
              plain
              @click.stop="openPreview(doc)"
            >
              <el-icon><View /></el-icon> 预览原文
            </el-button>
            <span class="doc-toggle">
              {{ expandedDoc === doc.name ? '收起分块' : '查看分块' }}
              <el-icon :class="['chevron', { open: expandedDoc === doc.name }]"><ArrowRight /></el-icon>
            </span>
          </div>

          <!-- 分块明细：默认折叠，展开后逐块展示原文 -->
          <transition name="chunk-expand">
            <div v-if="expandedDoc === doc.name" class="chunk-wrap">
              <div
                v-for="(chunk, i) in doc.chunks"
                :key="chunk.id"
                class="chunk-block"
              >
                <span class="chunk-index">分块 {{ (chunk.chunkIndex ?? i) + 1 }}</span>
                <p class="chunk-text">{{ chunk.content }}</p>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </el-card>

    <el-empty v-else description="请先选择一个课程" />

    <!-- 源文件预览：展示上传的完整原文（Markdown 渲染 / 纯文本等宽展示） -->
    <el-dialog
      v-model="previewVisible"
      :title="previewDoc?.name || '资料预览'"
      width="min(880px, 94vw)"
      class="doc-preview-dialog"
    >
      <div v-loading="previewLoading" class="preview-body">
        <div v-if="previewDoc" class="preview-head">
          <el-tag size="small" type="primary" effect="plain">{{ previewDoc.chunkCount }} 个分块</el-tag>
          <span class="preview-size">{{ previewLength }} 字</span>
          <span v-if="!previewDoc.hasFullContent" class="preview-legacy">
            （历史资料：由分块拼接展示）
          </span>
        </div>
        <!-- Markdown 资料走渲染视图，保留标题/列表等结构 -->
        <div v-if="isMarkdown(previewDoc)" class="preview-markdown">
          <MarkdownRenderer :content="previewContent" />
        </div>
        <!-- 其他格式（txt/pdf/docx 解析结果）按原文等宽展示，保留换行与缩进 -->
        <pre v-else class="preview-plain">{{ previewContent }}</pre>
        <el-empty v-if="!previewLoading && !previewContent" description="内容为空" />
      </div>
    </el-dialog>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import * as courseApi from '@/api/course'
import * as knowledgeApi from '@/api/knowledge'
import type { Course } from '@/api/course'
import type { KnowledgeDocument, UploadResult } from '@/api/knowledge'
import { Upload, Delete, Document, ArrowRight, View } from '@element-plus/icons-vue'
import type { UploadRequestOptions } from 'element-plus'

const route = useRoute()

const courses = ref<Course[]>([])
const selectedCourseId = ref<number | undefined>(undefined)
/** 课程下的复习资料（含各自的分块） */
const documents = ref<KnowledgeDocument[]>([])
const documentCount = ref(0)
const chunkCount = ref(0)
const loading = ref(false)
/** 当前展开查看分块的资料名称；null 表示全部收起 */
const expandedDoc = ref<string | null>(null)

// ---------- 源文件预览 ----------
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewDoc = ref<KnowledgeDocument | null>(null)
const previewContent = ref('')

/** 预览内容字符数（千分位展示） */
const previewLength = computed(() => previewContent.value.length.toLocaleString())

/** 是否为 Markdown 资料（按扩展名判断，走渲染视图） */
function isMarkdown(doc: KnowledgeDocument | null) {
  const name = (doc?.name || '').toLowerCase()
  return name.endsWith('.md') || name.endsWith('.markdown')
}

/** 打开源文件预览：拉取完整原文 */
async function openPreview(doc: KnowledgeDocument) {
  if (!selectedCourseId.value) return
  previewDoc.value = doc
  previewContent.value = ''
  previewVisible.value = true
  previewLoading.value = true
  try {
    const res = await knowledgeApi.getDocumentContent(selectedCourseId.value, doc.name)
    previewContent.value = res.content
  } catch (e) {
    console.error(e)
    ElMessage.error('预览失败，请稍后重试')
  } finally {
    previewLoading.value = false
  }
}

const uploadAction = computed(() => `${import.meta.env.VITE_API_BASE_URL || ''}/api/document/upload`)

onMounted(() => {
  loadCourses()
})

watch(selectedCourseId, () => {
  if (selectedCourseId.value) {
    // 切换课程时收起上一课程展开的资料
    expandedDoc.value = null
    loadDocuments()
  }
})

async function loadCourses() {
  try {
    courses.value = await courseApi.listCourses()
    const queryCourseId = route.query.courseId
    if (queryCourseId) {
      selectedCourseId.value = parseInt(queryCourseId as string)
    } else if (courses.value.length > 0) {
      selectedCourseId.value = courses.value[0].id
    }
  } catch (e) {
    console.error(e)
  }
}

/** 加载资料列表（默认展示），同时更新分块统计 */
async function loadDocuments() {
  if (!selectedCourseId.value) return
  loading.value = true
  try {
    const res = await knowledgeApi.listDocuments(selectedCourseId.value)
    documents.value = res.documents
    documentCount.value = res.documentCount
    chunkCount.value = res.totalChunks
    // 展开的资料若已不存在（如被清空），自动收起
    if (expandedDoc.value && !res.documents.some(d => d.name === expandedDoc.value)) {
      expandedDoc.value = null
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

/** 展开 / 收起某份资料的分块明细 */
function toggleDoc(name: string) {
  expandedDoc.value = expandedDoc.value === name ? null : name
}

function beforeUpload(file: File) {
  // 按扩展名校验：部分系统/浏览器下 file.type 为空字符串（例如 macOS 的 .md），
  // 用 MIME 白名单会把正常文件误判为不支持格式
  const name = (file.name || '').toLowerCase()
  const supported = knowledgeApi.ALLOWED_UPLOAD_EXTENSIONS.some(ext => name.endsWith(ext))
  if (!supported) {
    ElMessage.error(`仅支持 ${knowledgeApi.ALLOWED_UPLOAD_EXTENSIONS.join('、')} 格式`)
    return false
  }
  // 与后端 multipart 限制保持一致，避免大文件传到一半才被拒绝
  if (file.size > knowledgeApi.MAX_UPLOAD_SIZE) {
    const limit = knowledgeApi.MAX_UPLOAD_SIZE / 1024 / 1024
    ElMessage.error(`文件大小不能超过 ${limit}MB（当前 ${(file.size / 1024 / 1024).toFixed(1)}MB）`)
    return false
  }
  return true
}

/**
 * 自定义上传：走项目统一的 axios 实例，
 * 从而复用登录态自动刷新与后端返回的具体错误信息。
 */
async function customUpload(options: UploadRequestOptions) {
  const courseId = selectedCourseId.value
  if (!courseId) {
    options.onError(toUploadError(new Error('请先选择课程')))
    return
  }
  try {
    const res = await knowledgeApi.uploadDocument(courseId, options.file)
    options.onSuccess(res)
  } catch (e) {
    options.onError(toUploadError(e))
  }
}

/** Element Plus 期望的上传错误对象类型（onError 的参数） */
type UploadError = Parameters<UploadRequestOptions['onError']>[0]

/**
 * 把 axios 错误补全为 Element Plus 期望的上传错误对象。
 * 直接复用原对象（仅补齐缺少的字段），从而保留 response，
 * 便于后续提取后端返回的具体失败原因。
 */
function toUploadError(err: unknown): UploadError {
  const e = (err instanceof Error ? err : new Error(String(err))) as UploadError
  const status = (err as { response?: { status?: number } })?.response?.status
  e.status = typeof status === 'number' ? status : 0
  e.method = 'post'
  e.url = uploadAction.value
  return e
}

function handleUploadSuccess(response: UploadResult) {
  ElMessage.success(response?.message || '上传成功')
  loadDocuments()
}

/** 从错误对象中提取后端返回的具体原因，避免用户只看到一句无法排查的“上传失败” */
function extractErrorMessage(err: unknown): string {
  const e = err as {
    response?: { status?: number; data?: { message?: string } }
    message?: string
  }
  const status = e?.response?.status
  if (status === 401 || status === 403) {
    return '登录已过期，请重新登录后重试'
  }
  return e?.response?.data?.message || e?.message || '上传失败，请稍后重试'
}

function handleUploadError(err: unknown) {
  ElMessage.error(extractErrorMessage(err))
}

async function clearKnowledge() {
  if (!selectedCourseId.value) return
  try {
    await ElMessageBox.confirm('清空后该课程知识库将无法回答相关问题，是否继续？', '提示', { type: 'warning' })
    await knowledgeApi.clearKnowledgeBase(selectedCourseId.value)
    ElMessage.success('已清空')
    documents.value = []
    documentCount.value = 0
    chunkCount.value = 0
    expandedDoc.value = null
  } catch (e) {
    // cancel
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.knowledge-card {
  border-radius: var(--radius-md);
}
.stats-row {
  display: flex;
  gap: 40px;
  margin-bottom: 20px;
}

/* ---------- 资料列表 ---------- */
.doc-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.doc-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card);
  overflow: hidden;
  transition: border-color var(--t-fast) var(--ease), box-shadow var(--t-fast) var(--ease),
    transform var(--t-fast) var(--ease);
}
.doc-item:hover {
  border-color: var(--color-primary-200);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.doc-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.doc-head:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}
/* 资料图标：品牌渐变底 + 白色图标，强化“一份资料”的识别 */
.doc-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  color: #fff;
  background: var(--gradient-brand);
  box-shadow: var(--shadow-xs);
}
.doc-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.doc-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-meta {
  font-size: 12px;
  color: var(--color-muted-foreground);
}
.doc-toggle {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-primary);
}
/* 预览原文按钮：常态低干扰，悬停变紫；触屏也能直接点击 */
.doc-preview-btn {
  flex-shrink: 0;
  border-color: var(--color-primary-200);
  color: var(--color-primary);
  font-size: 12px;
}
.doc-preview-btn:hover {
  background: var(--color-primary-50);
  border-color: var(--color-primary);
}

/* ---------- 源文件预览弹窗 ---------- */
.preview-body {
  min-height: 120px;
}
.preview-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.preview-size {
  font-size: 12px;
  color: var(--color-muted-foreground);
}
.preview-legacy {
  font-size: 12px;
  color: var(--color-warning, #d97706);
}
/* Markdown 视图：限制高度内部滚动，保留文档结构 */
.preview-markdown {
  max-height: 64vh;
  overflow-y: auto;
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card);
  font-size: 14px;
  line-height: 1.8;
}
/* 纯文本视图：等宽字体、保留换行与缩进 */
.preview-plain {
  margin: 0;
  max-height: 64vh;
  overflow-y: auto;
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-foreground);
}
.chevron {
  transition: transform var(--t-fast) var(--ease);
}
.chevron.open {
  transform: rotate(90deg);
}

/* ---------- 分块明细（默认折叠） ---------- */
.chunk-wrap {
  padding: 0 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px dashed var(--color-border);
  padding-top: 12px;
  margin: 0 0;
}
.chunk-block {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-muted);
}
.chunk-index {
  display: inline-block;
  margin-bottom: 6px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-100);
}
.chunk-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--color-muted-foreground);
  white-space: pre-wrap;
  word-break: break-word;
}
/* 展开动画：高度与透明度过渡 */
.chunk-expand-enter-active,
.chunk-expand-leave-active {
  transition: opacity var(--t-normal) var(--ease), transform var(--t-normal) var(--ease);
}
.chunk-expand-enter-from,
.chunk-expand-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
@media (prefers-reduced-motion: reduce) {
  .doc-item,
  .chevron,
  .chunk-expand-enter-active,
  .chunk-expand-leave-active {
    transition: none;
  }
}
</style>
