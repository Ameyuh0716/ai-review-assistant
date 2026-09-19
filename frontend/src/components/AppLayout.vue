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
  /* 背景透明：氛围光晕由 App.vue 的 .app-ambient 统一提供，
     避免 fixed 元素落在过渡容器内（祖先 transform 会改变其锚点导致切换时滑动） */
  background: transparent;
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
