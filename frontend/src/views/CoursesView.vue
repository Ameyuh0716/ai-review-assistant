<template>
  <AppLayout>
    <div class="page-header">
      <h2>课程管理</h2>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon> 新建课程
      </el-button>
    </div>

    <el-row :gutter="16">
      <el-col v-for="course in courses" :key="course.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="course-card" shadow="hover">
          <div class="course-header">
            <h3>{{ course.name }}</h3>
            <div class="course-actions">
              <el-button link type="primary" @click="selectCourse(course)">
                <el-icon><ChatDotRound /></el-icon>
              </el-button>
              <el-button link type="primary" @click="openDialog(course)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button link type="danger" @click="handleDelete(course.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <p class="course-desc">{{ course.description || '暂无描述' }}</p>
          <div class="course-footer">
            <router-link :to="`/knowledge?courseId=${course.id}`">
              <el-button text size="small">管理知识库</el-button>
            </router-link>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="courses.length === 0" description="暂无课程，点击右上角新建" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑课程' : '新建课程'" width="480px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：操作系统" />
        </el-form-item>
        <el-form-item label="课程描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import { useChatStore } from '@/stores/chat'
import * as courseApi from '@/api/course'
import type { Course } from '@/api/course'
import { Plus, Edit, Delete, ChatDotRound } from '@element-plus/icons-vue'

const router = useRouter()
const chatStore = useChatStore()

const courses = ref<Course[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = ref<Partial<Course>>({ name: '', description: '' })

const rules: FormRules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }]
}

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

function openDialog(course?: Course) {
  isEdit.value = !!course
  form.value = course ? { ...course } : { name: '', description: '' }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value && form.value.id) {
        await courseApi.updateCourse(form.value.id, form.value)
        ElMessage.success('更新成功')
      } else {
        await courseApi.createCourse(form.value)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      await loadCourses()
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，是否继续？', '提示', { type: 'warning' })
    await courseApi.deleteCourse(id)
    ElMessage.success('已删除')
    await loadCourses()
  } catch (e) {
    // cancel
  }
}

function selectCourse(course: Course) {
  chatStore.setCourse({ id: course.id, name: course.name })
  ElMessage.success(`已选择课程：${course.name}，前往对话`)
  router.push('/')
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
.course-card {
  margin-bottom: 16px;
  border-radius: var(--radius-md);
  position: relative;
  overflow: hidden;
  transition: transform var(--t-normal) var(--ease), box-shadow var(--t-normal) var(--ease);
}
/* 左侧渐变装饰条: 悬停时点亮 */
.course-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--gradient-brand);
  opacity: 0;
  transition: opacity var(--t-normal) var(--ease);
}
.course-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}
.course-card:hover::before {
  opacity: 1;
}
.course-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.course-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.2px;
}
.course-actions {
  display: flex;
  gap: 4px;
}
.course-desc {
  color: var(--color-muted-foreground);
  font-size: 14px;
  min-height: 40px;
  margin: 0 0 12px;
}
.course-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
