<template>
  <AppLayout show-toggle @toggle-sidebar="sidebarVisible = !sidebarVisible">
    <div class="chat-layout">
      <aside class="sidebar" :class="{ open: sidebarVisible }">
        <div class="sidebar-header">
          <span class="sidebar-title">对话列表</span>
          <el-button type="primary" size="small" @click="createNewChat">
            <el-icon><Plus /></el-icon>新建
          </el-button>
        </div>
        <div class="conv-list" v-loading="loadingConversations">
          <div v-if="conversations.length === 0" class="empty">暂无对话</div>
          <div
            v-for="conv in conversations"
            :key="conv.id"
            :class="['conv-item', { active: currentConversationId === conv.id }]"
            @click="switchConversation(conv)"
          >
            <span class="title">{{ conv.title || '新对话' }}</span>
            <span class="time">{{ formatTime(conv.updatedAt) }}</span>
            <el-button
              class="del-btn"
              link
              size="small"
              @click.stop="deleteConversationItem(conv.id)"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
      </aside>

      <div class="chat-area">
        <div class="chat-header">
          <span class="chat-title">{{ currentTitle }}</span>
          <el-tag v-if="chatStore.currentCourse" type="primary" closable @close="clearCourse">
            {{ chatStore.currentCourse.name }}
          </el-tag>
        </div>

        <div ref="chatBoxRef" class="chat-box">
          <div v-if="messages.length === 0" class="welcome-msg">
            <h3>有什么可以帮你的？</h3>
            <p>我可以回答问题、生成测验或制定学习计划</p>
            <div class="quick-actions">
              <el-button round @click="quickAsk('请帮我出3道关于___知识点的选择题')">
                生成测验
              </el-button>
              <el-button round @click="quickAsk('请帮我制定一个___学科的7天复习计划')">
                学习计划
              </el-button>
            </div>
          </div>
          <div v-if="errorMsg" class="error-alert">
            <el-alert :title="errorMsg" type="error" closable @close="errorMsg = ''" />
          </div>

          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['msg', msg.role]"
          >
            <MarkdownRenderer :content="msg.content" :streaming="false" />
          </div>

          <div v-if="streamingMsg" class="msg ai typing">
            <MarkdownRenderer :content="streamingMsg" :streaming="true" />
            <span class="cursor">▊</span>
          </div>
        </div>

        <div class="input-area">
          <div class="input-row">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="1"
              resize="none"
              placeholder="输入你的问题... (Enter 发送, Shift+Enter 换行)"
              @keydown.enter.prevent="handleEnter"
            />
            <el-button
              v-if="!isStreaming"
              type="primary"
              size="large"
              :disabled="!inputText.trim()"
              @click="sendMessage"
            >
              发送
            </el-button>
            <el-button v-else type="danger" size="large" plain @click="stopGeneration">
              停止
            </el-button>
          </div>
          <div class="input-hint">Enter 发送，Shift+Enter 换行</div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import * as conversationApi from '@/api/conversation'
import { buildStreamUrl } from '@/api/agent'
import type { Conversation, Message } from '@/api/conversation'
import { Plus, Close } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const chatStore = useChatStore()

const conversations = ref<Conversation[]>([])
const currentConversationId = ref<number | null>(null)
const currentTitle = ref('新对话')
const messages = ref<Message[]>([])
const inputText = ref('')
const isStreaming = ref(false)
const streamingMsg = ref('')
const loadingConversations = ref(false)
const sidebarVisible = ref(false)
const errorMsg = ref('')
const chatBoxRef = ref<HTMLDivElement>()
let eventSource: EventSource | null = null

onMounted(() => {
  chatStore.loadCourseFromStorage()
  initConversations()
})

watch(messages, () => {
  scrollToBottom()
}, { deep: true })

watch(streamingMsg, () => {
  scrollToBottom()
})

async function initConversations() {
  loadingConversations.value = true
  try {
    const list = await conversationApi.listConversations()
    conversations.value = list
    const savedId = localStorage.getItem('currentConversationId')
    if (savedId) {
      const found = list.find(c => String(c.id) === savedId)
      if (found) {
        await switchConversation(found)
        return
      }
    }
    if (list.length > 0) {
      await switchConversation(list[0])
    }
  } catch (e) {
    console.error(e)
  } finally {
    loadingConversations.value = false
  }
}

async function loadConversations() {
  try {
    conversations.value = await conversationApi.listConversations()
  } catch (e: any) {
    console.error(e)
    errorMsg.value = e?.message || '加载对话列表失败'
  }
}

async function createNewChat() {
  if (!currentConversationId.value && messages.value.length === 0) {
    ElMessage.info('当前已经是最新对话')
    return
  }
  currentConversationId.value = null
  currentTitle.value = '新对话'
  messages.value = []
  streamingMsg.value = ''
  localStorage.removeItem('currentConversationId')
}

async function switchConversation(conv: Conversation) {
  currentConversationId.value = conv.id
  currentTitle.value = conv.title || '对话'
  localStorage.setItem('currentConversationId', String(conv.id))
  sidebarVisible.value = false
  try {
    const msgs = await conversationApi.listMessages(conv.id)
    messages.value = msgs.map(m => ({
      ...m,
      content: m.intent === 'QUIZ' || m.intent === 'quiz' ? hideQuizAnswers(m.content) : m.content
    }))
  } catch (e) {
    console.error(e)
  }
}

async function deleteConversationItem(id: number) {
  try {
    await ElMessageBox.confirm('删除此对话？', '提示', { type: 'warning' })
    await conversationApi.deleteConversation(id)
    ElMessage.success('已删除')
    if (currentConversationId.value === id) {
      currentConversationId.value = null
      currentTitle.value = '新对话'
      messages.value = []
      localStorage.removeItem('currentConversationId')
    }
    await loadConversations()
  } catch (e) {
    // cancel
  }
}

function clearCourse() {
  chatStore.setCourse(null)
}

function quickAsk(text: string) {
  inputText.value = text
}

function handleEnter(e: Event | KeyboardEvent) {
  const ke = e as KeyboardEvent
  if (!ke.shiftKey) {
    sendMessage()
  } else {
    inputText.value += '\n'
  }
}

function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  const fullMsg = chatStore.currentCourse && !text.includes(chatStore.currentCourse.name)
    ? `[课程: ${chatStore.currentCourse.name}] ${text}`
    : text

  messages.value.push({
    id: Date.now(),
    conversationId: currentConversationId.value || 0,
    role: 'user',
    content: text,
    createdAt: new Date().toISOString()
  })
  inputText.value = ''
  isStreaming.value = true
  streamingMsg.value = ''

  const url = buildStreamUrl(fullMsg, currentConversationId.value || undefined)
  eventSource = new EventSource(url)
  let metaParsed = false

  eventSource.onmessage = (event) => {
    if (!event.data) return
    if (!metaParsed) {
      metaParsed = true
      try {
        const meta = JSON.parse(event.data)
        if (meta.conversationId) {
          currentConversationId.value = meta.conversationId
          loadConversations()
          return
        }
      } catch (e) {
        // not meta
      }
    }
    streamingMsg.value += event.data + '\n'
  }

  eventSource.onerror = () => {
    stopGeneration()
  }
}

function stopGeneration() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (streamingMsg.value) {
    messages.value.push({
      id: Date.now(),
      conversationId: currentConversationId.value || 0,
      role: 'assistant',
      content: streamingMsg.value,
      createdAt: new Date().toISOString()
    })
    streamingMsg.value = ''
  }
  isStreaming.value = false
  loadConversations()
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBoxRef.value) {
      chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
    }
  })
}

function formatTime(d?: string) {
  if (!d) return ''
  const t = new Date(d)
  return `${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')} ${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`
}

function hideQuizAnswers(text: string): string {
  if (!text) return text
  let t = text.replace(/\*\*答案[：:]\s*[A-Da-d]\*\*/g, '')
  t = t.replace(/(?:^|\n)\s*答案[：:]\s*[A-Da-d]\s*/g, '\n')
  t = t.replace(/\*\*解析[：:]\*\*\s*[\s\S]*?(?=(?:\n#{1,3}\s*题目\s*\d+[：:])|(?:\n---\s*$)|(?:\n---\s*\n#{1,3}\s*题目)|$)/g, '')
  t = t.replace(/(?:^|\n)解析[：:]\s*[\s\S]*?(?=(?:\n#{1,3}\s*题目\s*\d+[：:])|(?:\n---\s*$)|(?:\n---\s*\n#{1,3}\s*题目)|$)/g, '\n')
  t = t.replace(/\n{3,}/g, '\n\n').trim()
  return t
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - var(--navbar-height) - 48px);
  background: var(--color-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  overflow: hidden;
}
.sidebar {
  width: var(--sidebar-width);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sidebar-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-muted-foreground);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.conv-item {
  padding: 12px 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  margin-bottom: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all var(--t-fast) var(--ease);
}
.conv-item:hover,
.conv-item.active {
  background: var(--color-primary-50);
}
.conv-item .title {
  font-size: 14px;
  color: var(--color-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.conv-item .time {
  font-size: 11px;
  color: var(--color-muted-foreground);
  margin-left: 8px;
  white-space: nowrap;
}
.conv-item .del-btn {
  opacity: 0;
  margin-left: 8px;
  color: var(--color-muted-foreground);
  transition: opacity var(--t-fast) var(--ease);
}
.conv-item:hover .del-btn {
  opacity: 1;
}
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-header {
  padding: 14px 24px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chat-title {
  font-weight: 600;
  font-size: 15px;
}
.chat-box {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.msg {
  max-width: 75%;
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  line-height: 1.7;
  font-size: 14px;
  word-break: break-word;
}
.msg.user {
  background: var(--color-primary);
  color: #fff;
  margin-left: auto;
  border-bottom-right-radius: var(--radius-sm);
}
.msg.ai {
  background: var(--color-muted);
  color: var(--color-foreground);
  border: 1px solid var(--color-border);
  border-bottom-left-radius: var(--radius-sm);
}
.msg.ai.typing .cursor {
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.welcome-msg {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 48px 32px;
  text-align: center;
  align-self: center;
  margin: auto;
}
.welcome-msg h3 {
  margin-bottom: 8px;
  font-size: 20px;
  font-weight: 600;
}
.welcome-msg p {
  color: var(--color-muted-foreground);
  margin-bottom: 24px;
}
.quick-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.input-area {
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
}
.input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.input-row :deep(.el-textarea__inner) {
  min-height: 44px !important;
  max-height: 120px;
}
.input-hint {
  text-align: center;
  font-size: 11px;
  color: var(--color-muted-foreground);
  margin-top: 8px;
}
.empty {
  text-align: center;
  color: var(--color-muted-foreground);
  padding: 32px 16px;
  font-size: 14px;
}
.error-alert {
  margin: 16px 24px;
}
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: -300px;
    top: var(--navbar-height);
    bottom: 0;
    z-index: 100;
    background: var(--color-card);
    box-shadow: var(--shadow-lg);
    transition: left 0.3s var(--ease);
  }
  .sidebar.open {
    left: 0;
  }
  .msg {
    max-width: 90%;
  }
}
</style>
