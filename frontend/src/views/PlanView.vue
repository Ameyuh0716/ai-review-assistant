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
            <h3>{{ plan.title }}</h3>
            <el-button link type="danger" @click="deletePlanItem(plan.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <p class="plan-time">{{ formatTime(plan.createdAt) }}</p>
          <div class="plan-content">
            <MarkdownRenderer :content="plan.content" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="plans.length === 0" description="暂无复习计划" />

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
import { ref, onMounted } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import * as courseApi from '@/api/course'
import * as planApi from '@/api/plan'
import type { Course } from '@/api/course'
import type { StudyPlan } from '@/api/plan'
import { Calendar, Delete } from '@element-plus/icons-vue'

const courses = ref<Course[]>([])
const plans = ref<StudyPlan[]>([])
const generateDialogVisible = ref(false)
const generating = ref(false)
const generateForm = ref({ courseId: undefined as number | undefined, days: 7 })

onMounted(() => {
  loadCourses()
  loadPlans()
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
      generateForm.value.courseId,
      `${course?.name || '课程'} ${generateForm.value.days}天复习计划`,
      content
    )
    ElMessage.success('生成成功')
    generateDialogVisible.value = false
    await loadPlans()
  } finally {
    generating.value = false
  }
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
  font-size: 22px;
  font-weight: 700;
}
.plan-card {
  margin-bottom: 16px;
  border-radius: var(--radius-md);
}
.plan-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.plan-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.plan-time {
  color: var(--color-muted-foreground);
  font-size: 12px;
  margin: 0 0 12px;
}
.plan-content {
  max-height: 300px;
  overflow-y: auto;
  color: var(--color-muted-foreground);
  font-size: 14px;
}
</style>
