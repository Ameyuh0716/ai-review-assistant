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
          :headers="uploadHeaders"
          :data="{ courseId: selectedCourseId }"
          :show-file-list="false"
          :before-upload="beforeUpload"
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
        <el-table-column type="index" width="60" />
        <el-table-column label="内容" min-width="300">
          <template #default="{ row }">
            <div class="chunk-content">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="metadata.chunkIndex" label="分块序号" width="100" />
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
import type { DocumentChunk } from '@/api/knowledge'
import { Upload, Delete } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const courses = ref<Course[]>([])
const selectedCourseId = ref<number | undefined>(undefined)
const chunks = ref<DocumentChunk[]>([])
const chunkCount = ref(0)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const uploadAction = computed(() => `${import.meta.env.VITE_API_BASE_URL || ''}/api/document/upload`)
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${authStore.token || ''}`
}))

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
  const allowed = ['text/plain', 'text/markdown', 'application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document']
  if (!allowed.includes(file.type)) {
    ElMessage.error('仅支持 txt、md、pdf、docx 格式')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

function handleUploadSuccess() {
  ElMessage.success('上传成功')
  loadChunks()
  loadCount()
}

function handleUploadError() {
  ElMessage.error('上传失败')
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
  font-size: 22px;
  font-weight: 700;
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
