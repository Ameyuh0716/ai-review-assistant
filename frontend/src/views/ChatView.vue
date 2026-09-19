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

        <div ref="chatBoxRef" class="chat-box" @scroll="handleScroll">
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
            :key="msg.id ?? index"
            :class="['msg', msg.role]"
          >
            <!-- RAG 检索执行情况（仅 AI 消息；历史消息通过检索日志回填） -->
            <div v-if="msg.role !== 'user' && msg.ragMeta" class="rag-badge">
              <el-tooltip placement="top" effect="light">
                <template #content>
                  <div class="rag-tip">
                    <p><strong>知识库检索详情</strong></p>
                    <p>命中片段：{{ msg.ragMeta.resultCount }} / TopK {{ msg.ragMeta.topK }}</p>
                    <p v-if="msg.ragMeta.candidateCount != null">向量初始召回：{{ msg.ragMeta.candidateCount }} 篇</p>
                    <p v-if="msg.ragMeta.topScore != null">最高相似度：{{ Number(msg.ragMeta.topScore).toFixed(3) }}</p>
                    <p v-if="msg.ragMeta.threshold != null">相似度阈值：{{ msg.ragMeta.threshold }}</p>
                    <p v-if="msg.ragMeta.latencyMs != null">检索耗时：{{ msg.ragMeta.latencyMs }} ms</p>
                    <p v-if="msg.ragMeta.keywordFallback">已触发关键词兜底召回</p>
                  </div>
                </template>
                <span class="rag-content">
                  <el-icon><Search /></el-icon>
                  <template v-if="msg.ragMeta.resultCount > 0">
                    知识库命中 {{ msg.ragMeta.resultCount }}/{{ msg.ragMeta.topK }}
                  </template>
                  <template v-else>知识库未命中</template>
                  <span v-if="msg.ragMeta.topScore != null" class="rag-sep">
                    · 相似度 {{ Number(msg.ragMeta.topScore).toFixed(2) }}
                  </span>
                  <span v-if="msg.ragMeta.latencyMs != null" class="rag-sep">
                    · {{ msg.ragMeta.latencyMs }}ms
                  </span>
                </span>
              </el-tooltip>
            </div>

            <!-- 编辑用户消息（DeepSeek 风格） -->
            <div v-if="editingIndex === index" class="edit-box">
              <el-input v-model="editingText" type="textarea" :rows="3" resize="none" />
              <div class="edit-actions">
                <el-button size="small" @click="cancelEdit">取消</el-button>
                <el-button size="small" type="primary" @click="submitEdit">保存并发送</el-button>
              </div>
            </div>
            <template v-else>
              <MarkdownRenderer :content="msg.content" :streaming="false" />
              <!-- 消息操作条：始终可见（兼容触屏），悬停高亮 -->
              <div class="msg-actions">
                <button class="act-btn" type="button" title="复制" @click="copyText(msg.content)">
                  <el-icon><CopyDocument /></el-icon>
                </button>
                <button
                  v-if="msg.role === 'user'"
                  class="act-btn"
                  type="button"
                  title="编辑并重新发送"
                  :disabled="isStreaming"
                  @click="startEdit(index)"
                >
                  <el-icon><EditPen /></el-icon>
                </button>
                <button
                  v-else
                  class="act-btn"
                  type="button"
                  title="重新生成"
                  :disabled="isStreaming || !canRegenerate(index)"
                  @click="regenerate(index)"
                >
                  <el-icon><RefreshRight /></el-icon>
                </button>
              </div>
            </template>
          </div>

          <div v-if="streamingMsg" class="msg ai typing">
            <div v-if="streamRagMeta" class="rag-badge">
              <span class="rag-content">
                <el-icon><Search /></el-icon>
                <template v-if="streamRagMeta.resultCount > 0">
                  知识库命中 {{ streamRagMeta.resultCount }}/{{ streamRagMeta.topK }}
                </template>
                <template v-else>知识库未命中</template>
                <span v-if="streamRagMeta.topScore != null" class="rag-sep">
                  · 相似度 {{ Number(streamRagMeta.topScore).toFixed(2) }}
                </span>
                <span v-if="streamRagMeta.latencyMs != null" class="rag-sep">
                  · {{ streamRagMeta.latencyMs }}ms
                </span>
              </span>
            </div>
            <MarkdownRenderer :content="streamingMsg" :streaming="true" />
            <span class="cursor">▊</span>
          </div>
        </div>

        <div class="input-area">
          <!-- 用户上翻阅读时不再强制拉回底部，改为提供显式返回入口 -->
          <transition name="fade-up">
            <button
              v-if="!autoFollow"
              class="scroll-to-bottom"
              type="button"
              aria-label="回到最新消息"
              @click="jumpToBottom"
            >
              <el-icon><ArrowDown /></el-icon>
              <span>回到最新</span>
            </button>
          </transition>
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
              @click="sendMessage()"
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
import * as ragApi from '@/api/rag'
import { buildStreamUrl } from '@/api/agent'
import type { Conversation, Message, RagMetaInfo } from '@/api/conversation'
import { Plus, Close, ArrowDown, CopyDocument, EditPen, RefreshRight, Search } from '@element-plus/icons-vue'

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

/** 当前流式回复的 RAG 检索元数据（收到 __rag 控制帧后填充） */
const streamRagMeta = ref<RagMetaInfo | null>(null)

/** 正在编辑的用户消息下标；null 表示无编辑 */
const editingIndex = ref<number | null>(null)
const editingText = ref('')

/**
 * 是否自动跟随最新消息。
 * <p>用户手动上翻阅读时置为 false，此时流式输出不再强制把视口拉回底部；
 * 用户回到底部或点击“回到最新”后恢复为 true。</p>
 */
const autoFollow = ref(true)

/** 距底部小于该像素数即视为“已贴底”，可继续自动跟随 */
const BOTTOM_TOLERANCE_PX = 80

/** 合并同一帧内的多次滚动请求，避免流式逐字更新时频繁触发重排 */
let scrollPending = false

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
  // 当前已是空的新对话时避免重复创建
  if (currentConversationId.value && messages.value.length === 0 && !streamingMsg.value) {
    ElMessage.info('当前已经是新对话')
    return
  }
  try {
    const conv = await conversationApi.createConversation('新对话')
    // 新会话插入列表顶部并立即选中
    conversations.value = [conv, ...conversations.value]
    currentConversationId.value = conv.id
    currentTitle.value = conv.title || '新对话'
    messages.value = []
    streamingMsg.value = ''
    autoFollow.value = true
    localStorage.setItem('currentConversationId', String(conv.id))
    sidebarVisible.value = false
  } catch (e) {
    console.error(e)
    errorMsg.value = '创建对话失败，请稍后重试'
  }
}

async function switchConversation(conv: Conversation) {
  currentConversationId.value = conv.id
  currentTitle.value = conv.title || '对话'
  // 切换会话时重置跟随状态，并按新会话内容跳到最新
  autoFollow.value = true
  localStorage.setItem('currentConversationId', String(conv.id))
  sidebarVisible.value = false
  editingIndex.value = null
  try {
    const msgs = await conversationApi.listMessages(conv.id)
    messages.value = msgs.map(m => ({
      ...m,
      content: m.intent === 'QUIZ' || m.intent === 'quiz' ? hideQuizAnswers(m.content) : m.content
    }))
    // 历史消息回填 RAG 检索标记（依赖 rag_search_log）
    await attachRagMeta(conv.id)
    // 历史消息渲染后可能有代码块/表格撑高，强制贴底
    scrollToBottom(true)
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
      autoFollow.value = true
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

function sendMessage(explicitText?: string) {
  const text = (explicitText ?? inputText.value).trim()
  if (!text || isStreaming.value) return

  inputText.value = ''
  messages.value.push({
    id: Date.now(),
    conversationId: currentConversationId.value || 0,
    role: 'user',
    content: text,
    createdAt: new Date().toISOString()
  })
  startStream(text, false)
}

/**
 * 发起 SSE 流式对话。
 *
 * @param text             用户消息文本
 * @param reuseUserMessage 为 true 时视为“重新生成”：服务端不重复保存用户消息
 */
function startStream(text: string, reuseUserMessage: boolean) {
  if (isStreaming.value) return
  const fullMsg = chatStore.currentCourse && !text.includes(chatStore.currentCourse.name)
    ? `[课程: ${chatStore.currentCourse.name}] ${text}`
    : text

  isStreaming.value = true
  streamingMsg.value = ''
  streamRagMeta.value = null
  // 主动发送时强制回到最新，确保用户能看到自己的提问
  scrollToBottom(true)

  const url = buildStreamUrl(fullMsg, currentConversationId.value || undefined, reuseUserMessage)
  eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    // 注意: 不能用 !event.data 判断, 因为纯换行 token 的 data 就是 "\n"(真值) 或 ""(空行标记)
    if (event.data === undefined || event.data === null) return
    const raw = event.data
    // 控制帧（会话元数据 / RAG 检索元数据）以 JSON 发送，正文 token 原样拼接
    if (raw.startsWith('{')) {
      const control = tryParseControlFrame(raw)
      if (control) {
        if (control.conversationId) {
          currentConversationId.value = control.conversationId
          localStorage.setItem('currentConversationId', String(control.conversationId))
          loadConversations()
        }
        if (control.__rag) {
          streamRagMeta.value = {
            resultCount: Number(control.resultCount) || 0,
            topK: Number(control.topK) || 0,
            threshold: control.threshold,
            latencyMs: control.latencyMs,
            keywordFallback: control.keywordFallback,
            topScore: control.topScore ?? null,
            candidateCount: control.candidateCount
          }
        }
        return
      }
    }
    // 直接拼接: SSE 已将 token 内的换行拆分为多个 data 行并由 EventSource 还原为 \n
    // 追加额外 \n 会破坏 markdown 连续文本 (每个 token 独立成段)
    streamingMsg.value += raw
  }

  eventSource.onerror = () => {
    // 服务端结束推送或连接异常：以服务端数据为准刷新消息（同时拿到真实消息 ID，供编辑/重新生成使用）
    finishStream(true)
  }
}

/**
 * 尝试把一段文本解析为 SSE 控制帧。
 * <p>仅当 JSON 对象包含 conversationId 或 __rag 字段时才视为控制帧，
 * 避免把模型输出的 JSON 正文误吞。</p>
 */
function tryParseControlFrame(text: string): any | null {
  const trimmed = text.trim()
  if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return null
  try {
    const obj = JSON.parse(trimmed)
    if (obj && typeof obj === 'object' && ('conversationId' in obj || '__rag' in obj)) {
      return obj
    }
  } catch {
    // 非 JSON，按正文处理
  }
  return null
}

/** 用户点击“停止”：中止生成，保留已流式输出的部分内容（不与服务端同步） */
function stopGeneration() {
  finishStream(false)
}

/**
 * 结束一次流式会话。
 *
 * @param syncWithServer 为 true 时从服务端重新拉取消息（自然结束/异常断开，服务端已落库）；
 *                       为 false 时（用户主动停止）保留本地已生成的部分内容
 */
function finishStream(syncWithServer: boolean) {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (!syncWithServer && streamingMsg.value) {
    messages.value.push({
      id: Date.now(),
      conversationId: currentConversationId.value || 0,
      role: 'assistant',
      content: streamingMsg.value,
      createdAt: new Date().toISOString(),
      ragMeta: streamRagMeta.value ?? undefined
    })
  }
  streamingMsg.value = ''
  streamRagMeta.value = null
  isStreaming.value = false
  loadConversations()
  if (syncWithServer) {
    refreshMessages()
  }
}

/** 从服务端重新拉取当前会话消息，并为 AI 消息回填 RAG 标记 */
async function refreshMessages() {
  const convId = currentConversationId.value
  if (!convId) return
  try {
    const msgs = await conversationApi.listMessages(convId)
    messages.value = msgs.map(m => ({
      ...m,
      content: m.intent === 'QUIZ' || m.intent === 'quiz' ? hideQuizAnswers(m.content) : m.content
    }))
    await attachRagMeta(convId)
  } catch (e) {
    console.error(e)
  }
}

/** 拉取会话的 RAG 检索日志，把检索命中情况附着到对应的 AI 消息上（刷新后仍可见） */
async function attachRagMeta(convId: number) {
  try {
    const logs = await ragApi.listRagLogs(convId)
    if (!logs || logs.length === 0) return
    // 日志按时间倒序返回，用队列逐个匹配最近的用户消息
    const pool = [...logs].sort((a, b) => b.id - a.id)
    for (let i = messages.value.length - 1; i >= 1; i--) {
      const assistant = messages.value[i]
      if (assistant.role !== 'assistant') continue
      const userMsg = messages.value[i - 1]
      if (!userMsg || userMsg.role !== 'user') continue
      const normalized = normalizeQuery(userMsg.content)
      const idx = pool.findIndex(log => queryMatches(normalized, normalizeQuery(log.query)))
      if (idx >= 0) {
        const log = pool[idx]
        pool.splice(idx, 1)
        assistant.ragMeta = {
          resultCount: log.resultCount,
          topK: log.topK,
          threshold: log.similarityThreshold,
          latencyMs: log.latencyMs
        }
      }
    }
  } catch (e) {
    console.warn('[rag] 加载检索日志失败', e)
  }
}

/** 去掉课程前缀与首尾空白，便于将检索日志 query 与用户消息内容对齐 */
function normalizeQuery(text: string) {
  return (text || '').replace(/^\[课程:[^\]]*\]\s*/, '').replace(/[？?。.!！\s]+$/, '').trim()
}

/**
 * 判断用户消息与检索日志 query 是否指向同一条提问。
 * <p>意图识别可能只把“概念词”传给检索（如“子网掩码”），而消息是完整句子，
 * 因此采用相等或包含关系做容错匹配。</p>
 */
function queryMatches(userContent: string, logQuery: string) {
  if (!userContent || !logQuery) return false
  return userContent === logQuery || userContent.includes(logQuery) || logQuery.includes(userContent)
}

// ---------- 消息操作：复制 / 编辑 / 重新生成 ----------

/** 复制文本到剪贴板（含非安全上下文兜底） */
async function copyText(text: string) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('已复制')
  } catch (e) {
    console.error(e)
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

/** 进入编辑态：把用户消息替换为可编辑文本框 */
function startEdit(index: number) {
  if (isStreaming.value) return
  const msg = messages.value[index]
  if (!msg || msg.role !== 'user') return
  editingIndex.value = index
  editingText.value = msg.content
}

function cancelEdit() {
  editingIndex.value = null
  editingText.value = ''
}

/** 保存编辑：截断该消息及其之后的消息，再以新内容重新发送 */
async function submitEdit() {
  const index = editingIndex.value
  if (index == null) return
  const text = editingText.value.trim()
  if (!text) {
    ElMessage.warning('内容不能为空')
    return
  }
  const target = messages.value[index]
  editingIndex.value = null
  try {
    if (target && isPersistedId(target.id)) {
      await conversationApi.truncateMessages(target.id)
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('操作失败，请重试')
    return
  }
  messages.value.splice(index)
  sendMessage(text)
}

/** 是否可以重新生成：该 AI 消息前面存在一条用户消息 */
function canRegenerate(index: number): boolean {
  const prev = messages.value[index - 1]
  return !!prev && prev.role === 'user'
}

/** 重新生成：截断该 AI 回复及其后续消息，复用上一条用户消息重新生成（不重复插入提问） */
async function regenerate(index: number) {
  if (isStreaming.value) return
  const assistant = messages.value[index]
  const prevUser = messages.value[index - 1]
  if (!assistant || assistant.role !== 'assistant' || !prevUser || prevUser.role !== 'user') return
  try {
    if (isPersistedId(assistant.id)) {
      await conversationApi.truncateMessages(assistant.id)
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('操作失败，请重试')
    return
  }
  // 仅移除本地 AI 回复及其后续内容，保留用户提问
  messages.value.splice(index)
  startStream(prevUser.content, true)
}

/** 判断消息 ID 是否为服务端持久化 ID（本地临时 ID 为 Date.now() 时间戳） */
function isPersistedId(id: number) {
  return typeof id === 'number' && id > 0 && id < 1e12
}

function handleScroll() {
  const el = chatBoxRef.value
  if (!el) return
  // 以“距底部距离”判定是否继续跟随：拖到最底或点回到底部后会自动恢复跟随
  autoFollow.value = el.scrollHeight - el.scrollTop - el.clientHeight <= BOTTOM_TOLERANCE_PX
}

/**
 * 滚动到底部。
 *
 * @param force 为 true 时忽略“用户已上翻”状态强制贴底（发送消息、切换会话时使用）
 */
function scrollToBottom(force = false) {
  if (!force && !autoFollow.value) return
  if (scrollPending) return
  scrollPending = true
  nextTick(() => {
    scrollPending = false
    const el = chatBoxRef.value
    if (!el) return
    // 二次校验：等待 DOM 更新期间用户可能已上翻，此时不应再把视口拉回底部
    if (!force && !autoFollow.value) return
    el.scrollTop = el.scrollHeight
    autoFollow.value = true
  })
}

/** 点击“回到最新”：回到底部并恢复自动跟随 */
function jumpToBottom() {
  autoFollow.value = true
  scrollToBottom(true)
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
  background: linear-gradient(180deg, rgba(245, 243, 255, 0.45) 0%, rgba(255, 255, 255, 0) 40%);
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
  letter-spacing: 0.8px;
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
  position: relative;
  border: 1px solid transparent;
  transition: all var(--t-fast) var(--ease);
}
.conv-item:hover {
  background: var(--color-primary-50);
}
.conv-item.active {
  background: var(--color-primary-50);
  border-color: var(--color-primary-100);
  box-shadow: inset 3px 0 0 var(--color-primary);
}
.conv-item.active .title {
  color: var(--color-primary-dark);
  font-weight: 600;
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
  position: relative;
  animation: msg-in var(--t-normal) var(--ease-spring) both;
}
@keyframes msg-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.msg.user {
  background: var(--gradient-primary);
  color: #fff;
  margin-left: auto;
  border-bottom-right-radius: var(--radius-sm);
  box-shadow: var(--shadow-primary);
}
/* AI 回复: 流式中(.ai) 与 历史消息(.assistant) 使用同一卡片样式 */
.msg.ai,
.msg.assistant {
  background: var(--color-muted);
  color: var(--color-foreground);
  border: 1px solid var(--color-border);
  border-bottom-left-radius: var(--radius-sm);
}
.msg.ai.typing .cursor {
  animation: blink 1s infinite;
  color: var(--color-primary);
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
/* RAG 检索标记: 附着在 AI 消息顶部，展示知识库命中情况 */
.rag-badge {
  display: inline-flex;
  align-items: center;
  margin-bottom: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-100);
  color: var(--color-primary-dark);
  font-size: 12px;
  line-height: 1.6;
  cursor: default;
  user-select: none;
}
.rag-content {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.rag-content .el-icon {
  font-size: 13px;
}
.rag-sep {
  color: var(--color-muted-foreground);
}
.rag-tip p {
  margin: 0 0 4px;
  font-size: 12px;
  line-height: 1.7;
}
.rag-tip p:last-child {
  margin-bottom: 0;
}
/* 消息操作条: 常态可见（触屏可用），悬停高亮 */
.msg-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 6px;
  opacity: 0.55;
  transition: opacity var(--t-fast) var(--ease);
}
.msg:hover .msg-actions,
.msg:focus-within .msg-actions {
  opacity: 1;
}
.act-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted-foreground);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--t-fast) var(--ease);
}
.act-btn:hover:not(:disabled) {
  background: var(--color-primary-50);
  color: var(--color-primary);
}
.act-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
.act-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
/* 用户消息气泡为渐变紫底，操作按钮使用反白色 */
.msg.user .act-btn {
  color: rgba(255, 255, 255, 0.85);
}
.msg.user .act-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}
/* 编辑态: 消息气泡内展开的输入框 */
.edit-box {
  width: min(560px, 58vw);
}
.edit-box :deep(.el-textarea__inner) {
  border-radius: var(--radius-md);
  font-size: 14px;
  line-height: 1.6;
}
.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.msg.user .edit-actions :deep(.el-button:not(.el-button--primary)) {
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.4);
  color: #fff;
}
.welcome-msg {
  position: relative;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  padding: 56px 40px;
  text-align: center;
  align-self: center;
  margin: auto;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
/* 欢迎区顶部渐变装饰条 */
.welcome-msg::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gradient-brand);
}
.welcome-msg h3 {
  margin-bottom: 8px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.welcome-msg p {
  color: var(--color-muted-foreground);
  margin-bottom: 28px;
}
.quick-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}
.quick-actions :deep(.el-button) {
  border-color: var(--color-primary-200);
  color: var(--color-primary);
  transition: all var(--t-normal) var(--ease);
}
.quick-actions :deep(.el-button:hover) {
  background: var(--color-primary-50);
  border-color: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(124, 58, 237, 0.18);
}
.input-area {
  /* 作为“回到最新”悬浮按钮的定位锚点 */
  position: relative;
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
  background: linear-gradient(180deg, rgba(248, 248, 252, 0) 0%, rgba(245, 243, 255, 0.5) 100%);
}
/* 用户上翻阅读时的“回到最新”入口：悬浮在输入区上方，不遮挡消息内容 */
.scroll-to-bottom {
  position: absolute;
  right: 24px;
  bottom: calc(100% + 12px);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transition: transform var(--t-fast) var(--ease), box-shadow var(--t-fast) var(--ease);
}
.scroll-to-bottom:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-lg);
}
.scroll-to-bottom:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.scroll-to-bottom .el-icon {
  font-size: 15px;
}
.fade-up-enter-active,
.fade-up-leave-active {
  transition: opacity var(--t-fast) var(--ease), transform var(--t-fast) var(--ease);
}
.fade-up-enter-from,
.fade-up-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
.input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.input-row :deep(.el-textarea__inner) {
  min-height: 44px !important;
  max-height: 120px;
  border-radius: 12px;
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
