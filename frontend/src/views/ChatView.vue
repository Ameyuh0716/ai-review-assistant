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

        <div
          ref="chatBoxRef"
          class="chat-box"
          :class="{ 'is-empty': messages.length === 0 && !streamingMsg }"
          @scroll="handleScroll"
        >
          <div v-if="messages.length === 0" class="welcome-msg">
            <div class="welcome-icon">
              <el-icon :size="26"><ChatDotRound /></el-icon>
            </div>
            <h3>有什么可以帮你的？</h3>
            <p>我可以回答问题、生成测验或制定学习计划</p>
            <div class="quick-actions">
              <button class="quick-card" type="button" @click="quickAsk('请帮我出3道关于___知识点的选择题')">
                <el-icon class="qc-icon"><EditPen /></el-icon>
                <span class="qc-title">生成测验</span>
                <span class="qc-desc">按知识点出选择题并批改</span>
              </button>
              <button class="quick-card" type="button" @click="quickAsk('请帮我制定一个___学科的7天复习计划')">
                <el-icon class="qc-icon"><Calendar /></el-icon>
                <span class="qc-title">学习计划</span>
                <span class="qc-desc">分天安排复习任务</span>
              </button>
            </div>
          </div>
          <div v-if="errorMsg" class="error-alert">
            <el-alert :title="errorMsg" type="error" closable @close="errorMsg = ''" />
          </div>

          <!-- 消息列表：用户消息右对齐气泡，AI 消息左对齐纯文本（DeepSeek 风格） -->
          <div
            v-for="(msg, index) in messages"
            :key="msg.id ?? index"
            :class="['msg-row', msg.role, { 'is-editing': editingIndex === index }]"
          >
            <div class="msg-main">
              <!-- 用户消息：可选气泡 + 编辑态 -->
              <template v-if="msg.role === 'user'">
                <div v-if="editingIndex === index" class="edit-box" @keydown="onEditKeydown">
                  <el-input
                    v-model="editingText"
                    type="textarea"
                    :autosize="{ minRows: 3, maxRows: 10 }"
                    resize="none"
                    autofocus
                  />
                  <div class="edit-actions">
                    <span class="edit-hint">
                      <kbd>Esc</kbd> 取消
                      <span class="sep">·</span>
                      <kbd>⌘/Ctrl</kbd><span class="plus">+</span><kbd>Enter</kbd> 保存
                    </span>
                    <div class="edit-btns">
                      <button class="edit-btn ghost" type="button" @click="cancelEdit">取消</button>
                      <button class="edit-btn primary" type="button" @click="submitEdit">
                        保存并发送
                      </button>
                    </div>
                  </div>
                </div>
                <div v-else class="bubble user-bubble">{{ msg.content }}</div>
              </template>

              <!-- AI 消息：无气泡，直接铺排内容 -->
              <template v-else>
                <!-- RAG 检索执行情况（历史消息通过检索日志回填）；就地重生成时隐藏旧标记 -->
                <div
                  v-if="msg.ragMeta && !(streamingTargetId === msg.id && isStreaming)"
                  :class="['rag-badge', { muted: !hasRagHit(msg) }]"
                >
                  <el-tooltip placement="top" effect="light">
                    <template #content>
                      <div class="rag-tip">
                        <p><strong>知识库检索详情</strong></p>
                        <p v-if="hasRagHit(msg)">
                          命中片段：{{ msg.ragMeta.resultCount }} / TopK {{ msg.ragMeta.topK }}
                        </p>
                        <p v-else-if="msg.ragMeta.resultCount > 0">
                          召回了 {{ msg.ragMeta.resultCount }} 个片段，但模型判定与问题不相关，未用于作答
                        </p>
                        <p v-else>未检索到知识库内容</p>
                        <p v-if="msg.ragMeta.candidateCount != null">向量初始召回：{{ msg.ragMeta.candidateCount }} 篇</p>
                        <p v-if="msg.ragMeta.topScore != null">最高相似度：{{ Number(msg.ragMeta.topScore).toFixed(3) }}</p>
                        <p v-if="msg.ragMeta.threshold != null">相似度阈值：{{ msg.ragMeta.threshold }}</p>
                        <p v-if="msg.ragMeta.latencyMs != null">检索耗时：{{ msg.ragMeta.latencyMs }} ms</p>
                        <p v-if="msg.ragMeta.keywordFallback">已触发关键词兜底召回</p>
                      </div>
                    </template>
                    <span class="rag-content">
                      <el-icon><Search /></el-icon>
                      <template v-if="hasRagHit(msg)">
                        知识库命中 {{ msg.ragMeta.resultCount }}/{{ msg.ragMeta.topK }}
                      </template>
                      <template v-else-if="msg.ragMeta.resultCount > 0">知识库无相关内容</template>
                      <template v-else>知识库未命中</template>
                      <span v-if="msg.ragMeta.topScore != null && hasRagHit(msg)" class="rag-sep">
                        · 相似度 {{ Number(msg.ragMeta.topScore).toFixed(2) }}
                      </span>
                      <span v-if="msg.ragMeta.latencyMs != null" class="rag-sep">
                        · {{ msg.ragMeta.latencyMs }}ms
                      </span>
                    </span>
                  </el-tooltip>
                </div>
                <!-- 就地重生成中：直接在该消息位置展示流式内容，不跳到列表底部 -->
                <template v-if="streamingTargetId === msg.id && isStreaming">
                  <MarkdownRenderer :content="streamingMsg" :streaming="true" />
                  <span class="cursor">▊</span>
                </template>
                <MarkdownRenderer v-else :content="msg.content" :streaming="false" />
              </template>
            </div>

            <!-- 操作栏：位于气泡/内容下方（不在气泡内部），图标 + 文字，常态可见 -->
            <div v-if="editingIndex !== index" class="msg-actions">
              <button class="act-btn" type="button" @click="copyText(msg.content)">
                <el-icon><CopyDocument /></el-icon><span>复制</span>
              </button>
              <button
                v-if="msg.role === 'user'"
                class="act-btn"
                type="button"
                :disabled="isStreaming"
                @click="startEdit(index)"
              >
                <el-icon><EditPen /></el-icon><span>编辑</span>
              </button>
              <button
                v-else
                class="act-btn"
                type="button"
                :disabled="isStreaming || !canRegenerate(index)"
                @click="regenerate(index)"
              >
                <el-icon><RefreshRight /></el-icon><span>重新生成</span>
              </button>
            </div>
          </div>

          <!-- 流式输出中的 AI 回复（就地重生成时改为渲染在原消息位置，故此处不再重复展示） -->
          <div v-if="streamingMsg && streamingTargetId === null" class="msg-row assistant">
            <div class="msg-main">
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
import { Plus, Close, ArrowDown, CopyDocument, EditPen, RefreshRight, Search, ChatDotRound, Calendar } from '@element-plus/icons-vue'

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
 * 正在“就地重新生成”的 AI 消息 ID。
 * <p>不为 null 时，流式内容直接渲染在该条消息的位置（编辑提问 / 重新生成场景），
 * 而不是追加到底部；且不会有任何历史消息被删除。</p>
 */
const streamingTargetId = ref<number | null>(null)

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
  // 中止进行中的流：否则旧的流式内容会继续拼接到新会话界面上
  abortStream()
  try {
    const conv = await conversationApi.createConversation('新对话')
    // 新会话插入列表顶部并立即选中
    conversations.value = [conv, ...conversations.value]
    currentConversationId.value = conv.id
    currentTitle.value = conv.title || '新对话'
    messages.value = []
    autoFollow.value = true
    localStorage.setItem('currentConversationId', String(conv.id))
    sidebarVisible.value = false
  } catch (e) {
    console.error(e)
    errorMsg.value = '创建对话失败，请稍后重试'
  }
}

/**
 * 中止进行中的流式请求（不保留已生成的部分内容）。
 * <p>切换会话、新建对话时调用：避免旧流的 token 继续拼接到新会话界面；
 * 服务端已收到的内容仍会按原 conversationId 保存，不会丢失。</p>
 */
function abortStream() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  streamingMsg.value = ''
  streamRagMeta.value = null
  streamingTargetId.value = null
  isStreaming.value = false
}

async function switchConversation(conv: Conversation) {
  // 中止进行中的流：避免旧会话的流式内容串到新会话界面上
  abortStream()
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
 * @param text               用户消息文本
 * @param reuseUserMessage   为 true 时视为“编辑/重新生成”：服务端不重复保存用户消息
 * @param assistantMessageId 需要就地覆盖的 AI 消息 ID：新回复写入原消息行，
 *                           不删除任何历史记录、不改变消息顺序
 * @param historyBeforeId    上下文截断点：只取该消息之前的历史作为上下文
 */
function startStream(text: string, reuseUserMessage: boolean, assistantMessageId?: number, historyBeforeId?: number) {
  if (isStreaming.value) return
  const fullMsg = chatStore.currentCourse && !text.includes(chatStore.currentCourse.name)
    ? `[课程: ${chatStore.currentCourse.name}] ${text}`
    : text

  isStreaming.value = true
  streamingMsg.value = ''
  streamRagMeta.value = null
  streamingTargetId.value = assistantMessageId ?? null
  // 主动发送时强制回到最新，确保用户能看到自己的提问
  scrollToBottom(true)

  const url = buildStreamUrl(
    fullMsg,
    currentConversationId.value || undefined,
    reuseUserMessage,
    chatStore.currentCourse?.id,
    assistantMessageId,
    historyBeforeId
  )
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
  streamingTargetId.value = null
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

/**
 * 模型在“知识库无相关内容”时的固定回复（定义于 prompts/rag-system.txt 与 explain-system.txt）。
 * 用于反查徽标状态，避免出现“回答提示无相关内容、徽标却显示命中”的自相矛盾。
 */
const NO_CONTENT_MARKERS = ['暂无相关内容', '暂无相关课程资料']

/**
 * 判断这条 AI 回复是否表示“知识库未命中”。
 * <p>仅在回复足够短时判定——无相关内容的回复本身就是一句短提示，
 * 这样可避免长答案中引述该句话时被误判。</p>
 */
function isNoContentAnswer(content?: string) {
  if (!content) return false
  const text = content.replace(/\s+/g, '')
  if (text.length > 100) return false
  return NO_CONTENT_MARKERS.some(m => text.includes(m))
}

/**
 * 徽标是否应显示为“命中”：既要有召回结果，回答也不能是“无相关内容”。
 * <p>检索分数只能证明“召回到了东西”，是否真的相关由模型在作答时判定；
 * 两者不一致时以回答为准，否则会出现“徽标说命中、正文说没有”的矛盾。</p>
 */
function hasRagHit(msg: Message) {
  return !!msg.ragMeta && msg.ragMeta.resultCount > 0 && !isNoContentAnswer(msg.content)
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

/** 课程前缀（选中课程时自动拼接），编辑态需剥离展示、保存时恢复 */
const COURSE_PREFIX_RE = /^\[课程[:：][^\]]*\]\s*/

/** 进入编辑态：把用户消息替换为可编辑文本框 */
function startEdit(index: number) {
  if (isStreaming.value) return
  const msg = messages.value[index]
  if (!msg || msg.role !== 'user') return
  editingIndex.value = index
  // 编辑框内不展示 [课程: xxx] 前缀，避免干扰用户修改正文
  editingText.value = msg.content.replace(COURSE_PREFIX_RE, '')
  // 聚焦并把光标移到末尾（autofocus 在反复进出编辑态时不稳定，这里显式处理）
  nextTick(() => {
    const ta = document.querySelector<HTMLTextAreaElement>('.edit-box .el-textarea__inner')
    if (!ta) return
    ta.focus()
    ta.setSelectionRange(ta.value.length, ta.value.length)
  })
}

function cancelEdit() {
  editingIndex.value = null
  editingText.value = ''
}

/**
 * 恢复消息的课程前缀：优先沿用原消息的前缀，否则使用当前选中课程。
 * 与服务端约定保持一致（数据库存的是带前缀的完整文本）。
 */
function applyCoursePrefix(text: string, original: string) {
  const matched = (original || '').match(COURSE_PREFIX_RE)
  if (matched) return matched[0] + text
  const course = chatStore.currentCourse
  if (course && !text.includes(course.name)) return `[课程: ${course.name}] ${text}`
  return text
}

/**
 * 保存编辑：就地更新该条消息内容，并仅重生成它对应的 AI 回复。
 * <p><b>不会删除任何历史记录</b>：
 * 之前的提问与回复原样保留；该条提问后面的对话也全部保留；
 * 只有紧跟在它后面的那条 AI 回复会被就地覆盖为新内容（因为旧答案已不匹配新问题）。</p>
 */
async function submitEdit() {
  const index = editingIndex.value
  if (index == null) return
  const text = editingText.value.trim()
  if (!text) {
    ElMessage.warning('内容不能为空')
    return
  }
  const target = messages.value[index]
  const reply = messages.value[index + 1]
  const targetId = target && isPersistedId(target.id) ? target.id : undefined
  const replyId = reply && reply.role === 'assistant' && isPersistedId(reply.id) ? reply.id : undefined
  const fullText = applyCoursePrefix(text, target?.content || '')
  editingIndex.value = null
  try {
    if (targetId) {
      // 就地更新原消息内容（不删除、不新增）
      await conversationApi.updateMessage(targetId, fullText)
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('保存失败，请重试')
    return
  }
  // 本地同步展示内容；其余消息（编辑点之前与之后）全部保留不动
  if (target) target.content = fullText
  // 重新生成：回复就地覆盖到原消息行，上下文只取编辑点之前的历史
  const messagePersisted = !!targetId
  startStream(fullText, messagePersisted, replyId, targetId)
}

/** 编辑态键盘操作：Esc 取消编辑，⌘/Ctrl + Enter 保存并发送 */
function onEditKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault()
    cancelEdit()
  } else if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
    e.preventDefault()
    submitEdit()
  }
}

/** 是否可以重新生成：该 AI 消息前面存在一条用户消息 */
function canRegenerate(index: number): boolean {
  const prev = messages.value[index - 1]
  return !!prev && prev.role === 'user'
}

/**
 * 重新生成：不删除任何历史记录，仅把新的 AI 回复就地覆盖到原消息行。
 * <p>上下文只取该提问之前的历史，使重生成结果与“重生成点之后的旧对话”无关。</p>
 */
async function regenerate(index: number) {
  if (isStreaming.value) return
  const assistant = messages.value[index]
  const prevUser = messages.value[index - 1]
  if (!assistant || assistant.role !== 'assistant' || !prevUser || prevUser.role !== 'user') return
  const assistantId = isPersistedId(assistant.id) ? assistant.id : undefined
  const userId = isPersistedId(prevUser.id) ? prevUser.id : undefined
  // 用户消息已存在：reuse=true 不重复保存；回复就地覆盖原行
  startStream(prevUser.content, true, assistantId, userId)
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
  // 空状态（仅展示欢迎页）无需滚动，避免把居中内容顶出视口
  if (messages.value.length === 0 && !streamingMsg.value) return
  if (scrollPending) return
  scrollPending = true
  nextTick(() => {
    scrollPending = false
    const el = chatBoxRef.value
    if (!el) return
    // 二次校验：等待 DOM 更新期间用户可能已上翻，此时不应再把视口拉回底部
    if (!force && !autoFollow.value) return
    // 内容不足一屏（或仅溢出几像素）时无需滚动
    if (el.scrollHeight - el.clientHeight < 4) return
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
  padding: 28px 24px 12px;
  display: flex;
  flex-direction: column;
  gap: 26px;
  /* 内容区限制宽度并居中：与 DeepSeek 一致，长行更易读 */
  scrollbar-gutter: stable;
}
/*
  空状态（仅欢迎页）：垂直居中。
  使用 safe center —— 内容溢出时回退为 flex-start，避免"居中导致顶部被裁且无法滚动"。
*/
.chat-box.is-empty {
  justify-content: safe center;
}
/* ---------- 消息行：用户右对齐、AI 左对齐 ---------- */
.msg-row {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 780px;
  margin: 0 auto;
  animation: msg-in var(--t-normal) var(--ease-spring) both;
}
.msg-row.user {
  align-items: flex-end;
}
.msg-row.assistant {
  align-items: flex-start;
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
.msg-main {
  max-width: 100%;
  line-height: 1.75;
  font-size: 15px;
  word-break: break-word;
}
.msg-row.user .msg-main {
  max-width: 78%;
}
/* 编辑态：解除气泡宽度限制，让编辑卡片有舒展的空间 */
.msg-row.user.is-editing .msg-main {
  width: 100%;
  max-width: 100%;
}
/* 用户气泡：淡雅浅紫底 + 深色文字（DeepSeek 淡雅风格，避免高饱和造成视觉疲劳） */
.bubble.user-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-100);
  color: var(--color-foreground);
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
/* AI 内容：无气泡，直接铺排，与页面背景融合 */
.msg-row.assistant .msg-main {
  padding: 2px 0;
  color: var(--color-foreground);
}
/* 流式光标 */
.cursor {
  display: inline-block;
  color: var(--color-primary);
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
/* ---------- 操作栏：位于气泡下方外部，图标 + 文字 ---------- */
.msg-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  opacity: 0.65;
  transition: opacity var(--t-fast) var(--ease);
}
.msg-row:hover .msg-actions,
.msg-row:focus-within .msg-actions {
  opacity: 1;
}
.act-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 9px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-muted-foreground);
  font-size: 12.5px;
  cursor: pointer;
  transition: all var(--t-fast) var(--ease);
}
.act-btn .el-icon {
  font-size: 14px;
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
/* 未命中 / 内容不相关：降饱和为中性灰，避免紫色的“命中”暗示 */
.rag-badge.muted {
  background: var(--color-muted);
  border-color: var(--color-border);
  color: var(--color-muted-foreground);
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
/* ---------- 编辑态：内联编辑卡片（沿用输入区语言：大圆角 + 品牌描边 + 柔和外发光） ---------- */
.edit-box {
  width: min(680px, 100%);
  margin-left: auto;
  padding: 12px 14px 11px;
  border: 1.5px solid var(--color-primary-200);
  border-radius: 16px;
  background: var(--color-card);
  box-shadow: 0 0 0 4px var(--color-primary-50), var(--shadow-sm);
  animation: edit-in var(--t-normal) var(--ease-spring) both;
  transition: border-color var(--t-fast) var(--ease), box-shadow var(--t-fast) var(--ease);
}
/* 聚焦整卡高亮：外层描边 + 外发光（内层文本域因此不再重复画焦点环） */
.edit-box:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-100), var(--shadow-md);
}
@keyframes edit-in {
  from {
    opacity: 0;
    transform: translateY(-6px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
/* 文本域：去掉 Element 默认灰边框，改为浅底无边框；聚焦时转白底由卡片承担高亮 */
.edit-box :deep(.el-textarea__inner) {
  padding: 11px 13px;
  border: none;
  border-radius: 12px;
  background: var(--color-muted);
  box-shadow: none;
  color: var(--color-foreground);
  font-size: 15px;
  line-height: 1.7;
  transition: background var(--t-fast) var(--ease);
}
.edit-box :deep(.el-textarea__inner:hover) {
  background: var(--color-primary-50);
}
/* 聚焦时仅由外层卡片承担高亮，故这里抹掉全局 textarea 焦点环（全局规则用了 !important） */
.edit-box :deep(.el-textarea__inner:focus) {
  background: var(--color-card);
  box-shadow: none !important;
}
.edit-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}
/* 快捷键提示：kbd 键帽样式，弱化但可读 */
.edit-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-muted-foreground);
  font-size: 11.5px;
  user-select: none;
}
.edit-hint kbd {
  padding: 0 5px;
  height: 18px;
  line-height: 16px;
  border: 1px solid var(--color-border);
  border-bottom-width: 2px;
  border-radius: 5px;
  background: var(--color-muted);
  color: var(--color-muted-foreground);
  font-family: inherit;
  font-size: 10.5px;
}
.edit-hint .sep,
.edit-hint .plus {
  margin: 0 2px;
  opacity: 0.6;
}
.edit-btns {
  display: flex;
  gap: 8px;
}
.edit-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--t-fast) var(--ease);
}
.edit-btn.ghost {
  background: transparent;
  border-color: var(--color-border);
  color: var(--color-muted-foreground);
}
.edit-btn.ghost:hover {
  background: var(--color-muted);
  border-color: #D9D5E8;
  color: var(--color-foreground);
}
.edit-btn.primary {
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: var(--shadow-xs);
}
.edit-btn.primary:hover {
  box-shadow: var(--shadow-primary);
  transform: translateY(-1px);
}
.edit-btn.primary:active {
  transform: translateY(0);
  box-shadow: var(--shadow-xs);
}
.edit-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
/* ---------- 欢迎区：DeepSeek 风格（居中图标 + 标题 + 建议卡片） ---------- */
.welcome-msg {
  margin: 0 auto;
  text-align: center;
  max-width: 620px;
  padding: 8px;
  animation: msg-in var(--t-slow) var(--ease-spring) both;
}
.welcome-icon {
  width: 52px;
  height: 52px;
  margin: 0 auto 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  color: #fff;
  background: var(--gradient-brand);
  box-shadow: var(--shadow-primary);
}
.welcome-msg h3 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.welcome-msg p {
  color: var(--color-muted-foreground);
  margin: 0 0 26px;
  font-size: 14px;
}
.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
/* 建议卡片：图标 + 标题 + 说明，悬停上浮 */
.quick-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card);
  text-align: left;
  cursor: pointer;
  transition: all var(--t-normal) var(--ease);
}
.quick-card .qc-icon {
  font-size: 17px;
  color: var(--color-primary);
  margin-bottom: 3px;
}
.quick-card .qc-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-foreground);
}
.quick-card .qc-desc {
  font-size: 12px;
  color: var(--color-muted-foreground);
  line-height: 1.5;
}
.quick-card:hover {
  border-color: var(--color-primary-200);
  background: var(--color-primary-50);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}
.quick-card:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
@media (max-width: 560px) {
  .quick-actions {
    grid-template-columns: 1fr;
  }
}
.input-area {
  /* 作为“回到最新”悬浮按钮的定位锚点 */
  position: relative;
  padding: 14px 24px 16px;
  border-top: 1px solid var(--color-border);
  background: var(--color-card);
}
/* 输入行：与消息区同宽居中，大圆角输入框（DeepSeek 风格） */
.input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  max-width: 780px;
  margin: 0 auto;
}
.input-row :deep(.el-textarea__inner) {
  min-height: 48px !important;
  max-height: 160px;
  padding: 13px 16px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.6;
  background: var(--color-muted);
  box-shadow: 0 0 0 1px var(--color-border) inset;
  transition: box-shadow var(--t-fast) var(--ease), background var(--t-fast) var(--ease);
}
.input-row :deep(.el-textarea__inner:hover) {
  background: var(--color-card);
}
/* 聚焦：品牌色描边 + 柔和外发光 */
.input-row :deep(.el-textarea__inner:focus) {
  background: var(--color-card);
  box-shadow: 0 0 0 1.5px var(--color-primary) inset, 0 0 0 3px var(--color-primary-100);
}
.input-row :deep(.el-button) {
  height: 48px;
  min-width: 76px;
  border-radius: 16px;
  font-weight: 600;
}
/* 用户上翻阅读时的"回到最新"入口：悬浮在输入区上方居中，避免遮挡消息两侧的操作栏 */
.scroll-to-bottom {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 12px);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transform: translateX(-50%);
  transition: box-shadow var(--t-fast) var(--ease), background var(--t-fast) var(--ease);
}
.scroll-to-bottom:hover {
  background: var(--color-primary-50);
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
  transform: translateX(-50%) translateY(6px);
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
  /* 窄屏：用户气泡放宽，消息区留白收窄 */
  .msg-row.user .msg-main {
    max-width: 88%;
  }
  .chat-box {
    padding: 20px 14px 8px;
  }
  /* 窄屏：隐藏快捷键提示，按钮占满整行便于点按 */
  .edit-hint {
    display: none;
  }
  .edit-actions {
    justify-content: flex-end;
  }
  .edit-btns {
    flex: 1;
  }
  .edit-btn {
    flex: 1;
    height: 38px;
  }
}
</style>
