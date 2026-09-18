<template>
  <AppLayout>
    <div class="page-header">
      <h2>学习统计</h2>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <el-statistic title="综合评分" :value="overview.overallScore" />
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <el-statistic title="活跃天数" :value="overview.activeDays" />
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <el-statistic title="连续复习" :value="overview.streakDays">
            <template #suffix>天</template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <el-statistic title="掌握率" :value="wrongStats.masteryRate ?? 0">
            <template #suffix>%</template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="wrong-book-card">
      <template #header>
        <div class="card-header">
          <span>错题本</span>
          <span class="sub">共 {{ wrongAnswers.length }} 道</span>
        </div>
      </template>
      <el-empty v-if="wrongAnswers.length === 0" description="暂无错题，继续保持" />
      <div v-else class="wrong-list">
        <div v-for="item in wrongAnswers" :key="item.id" :class="['wrong-item', { mastered: item.isMastered }]">
          <div class="wrong-header">
            <div class="wrong-tags">
              <el-tag v-if="item.isMastered" type="success">已掌握</el-tag>
              <el-tag v-else type="danger">待复习</el-tag>
              <el-tag v-if="item.wrongCount > 1" type="warning" effect="plain">错 {{ item.wrongCount }} 次</el-tag>
              <el-tag v-if="item.topic" type="info" effect="plain">{{ item.topic }}</el-tag>
            </div>
            <div class="wrong-actions">
              <el-button v-if="!item.isMastered" link type="success" @click="masterItem(item.id)">
                标记掌握
              </el-button>
              <el-button link type="danger" @click="deleteItem(item.id)">删除</el-button>
            </div>
          </div>
          <p class="question">{{ item.question }}</p>
          <p class="answer">你的答案：{{ item.userAnswer }} | 正确答案：{{ item.correctAnswer }}</p>
          <p v-if="item.explanation" class="explanation">解析：{{ item.explanation }}</p>
        </div>
      </div>
    </el-card>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import * as statsApi from '@/api/stats'
import type { StatsOverview, WrongStats, WrongAnswer } from '@/api/stats'

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
const wrongAnswers = ref<WrongAnswer[]>([])

onMounted(() => {
  loadStats()
})

async function loadStats() {
  try {
    const [overviewRes, wrongStatsRes, wrongListRes] = await Promise.all([
      statsApi.getOverview(),
      statsApi.getWrongStats(),
      statsApi.listWrongAnswers()
    ])
    overview.value = overviewRes
    wrongStats.value = wrongStatsRes
    wrongAnswers.value = wrongListRes
  } catch (e) {
    console.error(e)
  }
}

async function masterItem(id: number) {
  try {
    await statsApi.masterWrongAnswer(id)
    ElMessage.success('已标记掌握')
    await loadStats()
  } catch (e) {
    console.error(e)
  }
}

async function deleteItem(id: number) {
  try {
    await ElMessageBox.confirm('确定删除这道错题？', '提示', { type: 'warning' })
    await statsApi.deleteWrongAnswer(id)
    ElMessage.success('已删除')
    await loadStats()
  } catch (e) {
    // cancel
  }
}
</script>

<style scoped>
.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
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
.wrong-item {
  padding: 16px;
  border-radius: var(--radius-md);
  background: var(--color-muted);
  margin-bottom: 12px;
}
.wrong-item.mastered {
  background: var(--color-success-light);
  opacity: 0.8;
}
.wrong-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
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
.explanation {
  color: var(--color-muted-foreground);
  font-size: 13px;
  margin: 0;
}
</style>
