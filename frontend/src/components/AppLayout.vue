<template>
  <div class="app-layout">
    <NavBar :show-toggle="showToggle" @toggle-sidebar="emit('toggle-sidebar')" />
    <main class="main-content">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import NavBar from './NavBar.vue'

const props = defineProps<{
  showToggle?: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-sidebar'): void
}>()
</script>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  position: relative;
  background: var(--color-background);
}
/* 氛围背景: 顶部两侧柔和光晕 (紫罗兰 + 翠绿) */
.app-layout::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background:
    radial-gradient(560px 320px at 0% 0%, rgba(124, 58, 237, 0.07), transparent 65%),
    radial-gradient(520px 300px at 100% 8%, rgba(5, 150, 105, 0.06), transparent 65%);
}
.main-content {
  position: relative;
  z-index: 1;
  flex: 1;
  padding: 24px;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
}
</style>
