<template>
  <nav class="navbar">
    <div class="navbar-inner">
      <div class="navbar-left">
        <el-button v-if="showToggle" class="sidebar-toggle" text @click="emit('toggle-sidebar')">
          <el-icon><Menu /></el-icon>
        </el-button>
        <router-link to="/" class="navbar-brand">
          <span class="brand-icon">
            <el-icon><ChatDotRound /></el-icon>
          </span>
          AI Review Assistant
        </router-link>
      </div>
      <div class="nav-links">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path" :class="['nav-link', { active: route.path === item.path }]">
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
        <div class="nav-user">
          <el-avatar :size="32">{{ avatarText }}</el-avatar>
          <span class="nav-nickname">{{ authStore.displayName }}</span>
        </div>
        <el-button v-if="authStore.isLoggedIn" type="danger" plain size="small" @click="authStore.logout">
          退出
        </el-button>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  ChatDotRound,
  Notebook,
  Collection,
  EditPen,
  Calendar,
  TrendCharts,
  Menu
} from '@element-plus/icons-vue'

const props = defineProps<{
  showToggle?: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-sidebar'): void
}>()

const route = useRoute()
const authStore = useAuthStore()

const navItems = [
  { path: '/', label: '对话', icon: ChatDotRound },
  { path: '/courses', label: '课程', icon: Notebook },
  { path: '/knowledge', label: '知识库', icon: Collection },
  { path: '/quiz', label: '测验', icon: EditPen },
  { path: '/plan', label: '计划', icon: Calendar },
  { path: '/stats', label: '统计', icon: TrendCharts }
]

const avatarText = computed(() => {
  const name = authStore.user?.nickname || authStore.user?.username || '访'
  return name.charAt(0).toUpperCase()
})
</script>

<style scoped>
.navbar {
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--color-border);
  height: var(--navbar-height);
  display: flex;
  align-items: center;
  justify-content: center;
  position: sticky;
  top: 0;
  z-index: 100;
}
.navbar-inner {
  width: 100%;
  max-width: 1400px;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.navbar-brand {
  font-weight: 700;
  font-size: 18px;
  color: var(--color-foreground);
  letter-spacing: -0.5px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-links {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-md);
  color: var(--color-muted-foreground);
  font-size: 14px;
  font-weight: 500;
  transition: all var(--t-fast) var(--ease);
}
.nav-link:hover,
.nav-link.active {
  color: var(--color-primary);
  background: var(--color-primary-50);
}
.nav-user {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 12px;
  padding-left: 12px;
  border-left: 1px solid var(--color-border);
}
.nav-nickname {
  font-size: 14px;
  color: var(--color-foreground);
  font-weight: 500;
}
.sidebar-toggle {
  display: none;
  color: var(--color-primary);
  font-size: 20px;
}
@media (max-width: 768px) {
  .nav-links .nav-link span {
    display: none;
  }
  .sidebar-toggle {
    display: flex;
  }
}
</style>
