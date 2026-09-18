<template>
  <div class="markdown-body" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown, renderStreamingMarkdown } from '@/utils/markdown'

const props = defineProps<{
  content: string
  streaming?: boolean
}>()

const html = computed(() => {
  return props.streaming
    ? renderStreamingMarkdown(props.content)
    : renderMarkdown(props.content)
})
</script>

<style scoped>
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin-top: 20px;
  margin-bottom: 10px;
  color: var(--color-foreground);
}
.markdown-body :deep(p) {
  margin: 8px 0;
  line-height: 1.8;
}
.markdown-body :deep(pre) {
  background: #1a1a2e;
  color: #e0e0e0;
  padding: 16px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
.markdown-body :deep(code) {
  background: rgba(37, 99, 235, 0.08);
  color: var(--color-primary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
}
.markdown-body :deep(pre code) {
  background: none;
  color: inherit;
  padding: 0;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--color-primary);
  margin: 12px 0;
  padding: 8px 16px;
  background: var(--color-muted);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  color: var(--color-muted-foreground);
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 14px 0;
  font-size: 13px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--color-border);
  padding: 8px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: var(--color-muted);
  font-weight: 600;
}
</style>
