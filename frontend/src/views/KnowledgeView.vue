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
        <el-statistic title="文档分块数" :value="chunkCount" />
      </div>

      <el-table :data="chunks" v-loading="loading" stripe>
        <el-table-column label="分块序号" width="100">
          <template #default="{ row }">
            {{ (row.chunkIndex ?? 0) + 1 }} / {{ row.chunkTotal ?? 1 }}
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="300">
          <template #default="{ row }">
            <div class="chunk-content">{{ row.content }}</div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next"
        class="pagination"
        @change="loadChunks"
      />
    </el-card>

    <el-empty v-else description="请先选择一个课程" />
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import * as courseApi from '@/api/course'
import * as knowledgeApi from '@/api/knowledge'
import type { Course } from '@/api/course'
import type { DocumentChunk, UploadResult } from '@/api/knowledge'
import { Upload, Delete } from '@element-plus/icons-vue'
import type { UploadRequestOptions } from 'element-plus'

const route = useRoute()

const courses = ref<Course[]>([])
const selectedCourseId = ref<number | undefined>(undefined)
const chunks = ref<DocumentChunk[]>([])
const chunkCount = ref(0)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const uploadAction = computed(() => `${import.meta.env.VITE_API_BASE_URL || ''}/api/document/upload`)

onMounted(() => {
  loadCourses()
})

watch(selectedCourseId, () => {
  if (selectedCourseId.value) {
    page.value = 1
    loadChunks()
    loadCount()
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

async function loadChunks() {
  if (!selectedCourseId.value) return
  loading.value = true
  try {
    const res = await knowledgeApi.listChunks(selectedCourseId.value, page.value, size.value)
    chunks.value = res.records
    total.value = res.total
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function loadCount() {
  if (!selectedCourseId.value) return
  try {
    chunkCount.value = await knowledgeApi.getChunkCount(selectedCourseId.value)
  } catch (e) {
    console.error(e)
  }
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
  loadChunks()
  loadCount()
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
    chunks.value = []
    chunkCount.value = 0
    total.value = 0
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
  margin-bottom: 20px;
}
.chunk-content {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--color-muted-foreground);
  font-size: 14px;
}
.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
