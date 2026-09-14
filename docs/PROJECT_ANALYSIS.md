# 🎯 期末复习智能助手 — 项目分析总结（面试准备）

> 本文档帮助你深入理解项目的架构设计、技术亮点和面试话术，应对 AI 应用开发方向的面试提问。

---

## 📌 一、项目定位一句话

**基于 Spring AI + RAG 的智能学习辅助系统**，核心是「LLM + 向量数据库 + Agent 工具编排」的 AI 应用落地实践。

---

## 📌 二、技术架构全景

### 2.1 分层架构

```
┌─ 前端 ─────────────────────────────────────────────────┐
│  Thymeleaf + Bootstrap 5 + marked.js + DOMPurify       │
│  SSE 流式通信 / JWT 双令牌认证 / 响应式布局              │
├─ 安全层 ────────────────────────────────────────────────┤
│  Spring Security + JWT (Access 15min + Refresh 7天)     │
│  Bucket4j 令牌桶限流 / RBAC 角色控制                     │
├─ API 层 ────────────────────────────────────────────────┤
│  RESTful API + SSE Streaming + SpringDoc OpenAPI        │
├─ 业务层 ────────────────────────────────────────────────┤
│  Agent 核心 → 意图识别 → 工具调度 → 结果汇总             │
│  RAG 引擎 → 向量检索 → 关键词重排序 → Prompt 增强        │
├─ AI 层 ─────────────────────────────────────────────────┤
│  Spring AI ChatClient → DashScope/OpenAI/Ollama         │
│  VectorStore (PgVector) + EmbeddingModel                │
├─ 数据层 ────────────────────────────────────────────────┤
│  PostgreSQL + pgvector / MyBatis-Plus / Caffeine Cache  │
└─────────────────────────────────────────────────────────┘
```

### 2.2 请求处理流程

```
用户输入 → SecurityFilter (JWT验证)
         → AgentController
         → ReviewAssistantAgent.chat()
            ├─ 1. 获取/创建会话 (Conversation)
            ├─ 2. 保存用户消息 (Message)
            ├─ 3. 检测工具链请求 (ChainExecutor)
            ├─ 4. 意图识别 (本地规则 + LLM)
            │     ├─ Fast: 关键词匹配 → 直接命中
            │     └─ LLM: 构造 Schema Prompt → 解析 JSON
            ├─ 5. ToolRegistry 获取工具
            ├─ 6. 工具参数校验 (validate)
            ├─ 7. 工具执行 (execute / stream)
            │     └─ 如 QuestionTool → RagService
            │         ├─ PgVector 相似度检索
            │         ├─ 关键词重排序
            │         └─ Prompt 增强 → ChatClient 调用
            ├─ 8. 保存 AI 回复 + 复习记录
            └─ 9. 记录 Agent 日志 + 指标
```

---

## 📌 三、核心模块深度解析

### 3.1 Agent 意图识别（双层架构）

**面试问题**：你是怎么做意图识别的？

**回答要点**：

```java
// 双层意图识别：先本地规则快筛，复杂场景走 LLM
private IntentResult recognizeIntent(String message, Integer convId, List<Message> history) {
    if (hasHistory(history)) {
        return analyzeIntentWithLlm(message, history);  // 有上下文，走 LLM
    }
    return analyzeIntent(message, history);  // 无上下文，先走本地规则
}
```

**第一层：本地规则（毫秒级响应）**
- 出题关键词：题目/练习/测试/出题/面试题
- 计划关键词：计划/安排/规划
- 问题关键词：?/什么/怎么/如何/为什么
- 问候识别：你好/hello/hi

**第二层：LLM 意图识别（语义理解）**
- 构造 Tool Schema Prompt，让 LLM 从工具列表中选择
- 动态注入最近5条历史消息支持上下文消歧
- JSON 解析 + 校验，失败时回退到关键词匹配

**亮点**：历史消息传入意图识别 Prompt，解决指代消解问题（"再来一个"→ 复用上次意图）。

---

### 3.2 RAG 检索增强（向量检索 + 重排序）

**面试问题**：RAG 是怎么做的？检索质量怎么保证？

**回答要点**：

```java
// RAG 三步流程
private SearchResult searchDocuments(String question) {
    // 1. 向量检索（PgVector COSINE 相似度）
    int searchTopK = rerankEnabled ? topK * candidateMultiplier : topK;
    docs = vectorStore.similaritySearch(
        SearchRequest.builder().query(question).topK(searchTopK).similarityThreshold(threshold).build()
    );

    // 2. 关键词重排序（提升精确匹配的权重）
    if (rerankEnabled && docs.size() > topK) {
        docs = rerankByKeywords(question, docs, topK);
    }

    // 3. 组装上下文
    return new SearchResult(count, chunksJson, contextBuilder.toString());
}
```

**重排序算法**：
```java
// 向量分数 + 关键词命中加权
double keywordScore = 0;
for (String keyword : keywords) {
    if (keyword.length() > 1 && text.contains(keyword)) {
        keywordScore += 0.05;  // 每命中一个关键词加 0.05
    }
}
double finalScore = vectorScore + keywordScore;
```

**Prompt 增强**：
```
# RAG System Prompt
你是一个专业的课程复习助手。请根据以下参考资料回答用户问题。
如果参考资料中没有相关信息，请如实告知。

## 参考资料
{检索到的文档分块}
```

**数据流**：
```
用户文档 → DocumentServiceImpl.splitDocument()
         → 按段落分块（500字/块，50块上限）
         → embeddingModel.encode() → 1536维向量
         → PgVectorStore.add() → PostgreSQL vector_store 表
```

---

### 3.3 Agent 工具注册表（Spring 自动收集）

**面试问题**：Agent 工具是怎么管理的？

**回答要点**：

```java
@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new HashMap<>();

    // Spring 自动注入所有 AgentTool 实现
    public ToolRegistry(Collection<AgentTool> toolCollection) {
        for (AgentTool tool : toolCollection) {
            tools.put(tool.getName(), tool);
        }
    }

    // 动态生成工具 Schema 供意图识别使用
    public String buildToolSchemas() {
        StringBuilder sb = new StringBuilder("可用工具：\n");
        for (AgentTool tool : tools.values()) {
            sb.append(tool.getName()).append(" - ").append(tool.getDescription()).append("\n");
            sb.append("   参数：").append(tool.getParameterSchema()).append("\n");
        }
        return sb.toString();
    }
}
```

**设计亮点**：
- 新增工具只需实现 `AgentTool` 接口 + `@Component`，自动注册
- 工具 Schema 自动生成，LLM 意图识别时动态注入
- `ToolContext` 封装用户消息、会话ID、参数、历史消息

**工具链执行器**：
```java
// 支持 "先...再...然后..." 形式的多步请求
boolean isChainRequest(String message) {
    return message.contains("先") && (message.contains("再") || message.contains("然后"));
}

// 主题继承：后续步骤自动继承前一步的主题
String inheritedTopic = params.get("topic");
```

---

### 3.4 JWT 双令牌机制

**面试问题**：JWT 认证是怎么设计的？怎么解决安全性问题？

**回答要点**：

```
登录 → 签发 Access Token (15min) + Refresh Token (7天)
     ↓
请求携带 Access Token
     ↓
JwtAuthenticationFilter 解析 → 注入 SecurityContext
     ↓
Access 过期 → 前端自动用 Refresh 换取新 Access
     ↓
Refresh 过期 → 跳转登录页
```

**关键设计**：
- Access Token 仅15分钟有效，泄露窗口小
- Refresh Token 标记 `type=refresh`，不用于请求认证
- 前端过期前2分钟自动刷新，用户无感知
- 401 时先尝试 refresh，失败才跳登录

```java
// JwtAuthenticationFilter 只处理 access token
if (jwtProperties.isValid(token) && "access".equals(jwtProperties.getType(token))) {
    // 注入认证信息
}
```

---

### 3.5 SSE 流式对话

**面试问题**：流式输出是怎么实现的？

**回答要点**：

**后端（Spring WebFlux）**：
```java
// SSE 流式端点
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestParam String message, ...) {
    return agent.chatStream(message, conversationId, userId);
}

// Agent 内部调用 ChatClient.stream()
Flux<String> responseFlux = chatClient.prompt()
    .system(systemPrompt)
    .user(userMessage)
    .stream()      // ← 关键：返回 Flux 而非 call()
    .content();
```

**前端（SSE + 流式渲染）**：
```javascript
const resp = await fetch(url, { headers: authHeaders() });
const reader = resp.body.getReader();
const decoder = new TextDecoder();

while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    // 解析 SSE data: 格式
    for (const line of lines) {
        if (line.startsWith('data:')) {
            aiEl.innerHTML = renderMd(rawText += chunk);
        }
    }
}
```

---

### 3.6 文档处理与向量化

**面试问题**：文档是怎么处理和存储的？

**回答要点**：

```java
// 文档分块策略：按段落分块，每块约500字
private List<String> splitDocument(String content) {
    String[] paragraphs = content.split("\n\n");
    StringBuilder currentChunk = new StringBuilder();
    for (String paragraph : paragraphs) {
        if (currentChunk.length() + paragraph.length() > 500) {
            if (currentChunk.length() > 50) chunks.add(currentChunk.toString());
            currentChunk = new StringBuilder();
        }
        currentChunk.append(paragraph);
    }
    // 限制最多50个块
    return chunks.subList(0, Math.min(chunks.size(), 50));
}
```

**存储结构**：
```sql
-- PgVector 向量表（Spring AI 自动管理）
CREATE TABLE vector_store (
    id UUID PRIMARY KEY,
    content TEXT,           -- 分块文本
    embedding VECTOR(1536), -- text-embedding-v1 生成的向量
    metadata JSONB          -- {courseId, chunkIndex, chunkTotal}
);
-- HNSW 索引，COSINE 距离
```

**多格式支持**：
- PDF → Apache PDFBox 解析
- Word → Apache POI 解析
- txt/md → 直接读取

---

## 📌 四、技术亮点总结

### ⭐ 亮点1：Agent 工具化设计

| 特性 | 实现 |
|------|------|
| 工具自动注册 | Spring `Collection<AgentTool>` 自动收集 |
| Schema 自动生成 | `buildToolSchemas()` 动态生成 LLM 可用工具描述 |
| 工具链编排 | `ChainExecutor` 支持 "先...再...然后..." 多步执行 |
| 上下文传递 | `ToolContext` 封装消息/会话/参数/历史 |
| 参数校验 | `validate()` + `getValidationError()` 前置校验 |

### ⭐ 亮点2：RAG 检索增强

| 特性 | 实现 |
|------|------|
| 向量检索 | PgVector + HNSW 索引 + COSINE 距离 |
| 重排序 | 向量分数 + 关键词命中加权 |
| 配置化 | top-k / 阈值 / 重排序开关 全部可配置 |
| 缓存 | Caffeine 缓存 RAG 结果，降低重复调用 |
| 日志 | RAG 检索日志记录每次召回结果，可追溯质量 |

### ⭐ 亮点3：安全设计

| 特性 | 实现 |
|------|------|
| JWT 双令牌 | Access(15min) + Refresh(7天) |
| 自动刷新 | 前端过期前2分钟自动 refresh |
| 输入校验 | `@NotBlank @Size @Pattern` Bean Validation |
| XSS 防护 | DOMPurify 清理 marked.js 输出 |
| 限流 | Bucket4j 令牌桶，429 Too Many Requests |
| RBAC | Spring Security 角色控制 |

### ⭐ 亮点4：可观测性

| 特性 | 实现 |
|------|------|
| Agent 日志 | 每次调用记录意图/参数/延迟/成功/失败 |
| RAG 日志 | 每次检索记录查询/召回数/结果/延迟 |
| 指标收集 | Micrometer + Prometheus 格式导出 |
| 健康检查 | Actuator `/actuator/health` |
| 前端埋点 | Caffeine 缓存命中率统计 |

### ⭐ 亮点5：前端交互

| 特性 | 实现 |
|------|------|
| SSE 流式打字 | 逐字渲染，首字延迟低 |
| Markdown 渲染 | marked.js + DOMPurify 安全渲染 |
| 会话侧栏 | 历史会话切换/删除 |
| 课程上下文 | 课程标签自动关联对话 |
| 分页加载 | 知识库分块分页 |

---

## 📌 五、面试高频问题 & 回答

### Q1: 你这个项目的亮点是什么？

**回答模板**：

> 我做的是一个 AI 驱动的期末复习助手，核心亮点有三个：
>
> **第一是 Agent 工具化设计**。我设计了一套工具注册表机制，新增工具只需实现接口加 `@Component` 注解，Spring 自动收集注册。意图识别采用双层架构——本地关键词快筛 + LLM 语义理解，有历史消息时优先走 LLM 解决指代消解问题。还实现了工具链编排，支持 "先总结再出题然后制定计划" 的多步请求，主题在步骤间自动继承。
>
> **第二是 RAG 检索增强**。基于 PgVector 实现向量检索，用 HNSW 索引加速，COSINE 距离衡量相似度。在向量检索基础上增加了关键词重排序，提升精确匹配的权重。RAG 结果用 Caffeine 缓存降低重复调用，每次检索都记录日志方便追溯质量。
>
> **第三是安全和工程化**。JWT 双令牌机制（Access 15分钟 + Refresh 7天），前端自动刷新对用户透明。Bucket4j 限流返回 429，XSS 防护用 DOMPurify。60个单元测试覆盖全部工具和核心逻辑。

---

### Q2: 意图识别是怎么做的？为什么用双层？

**回答**：

> 第一层是本地关键词匹配，毫秒级响应，覆盖高频场景（出题、计划、问答、闲聊）。第二层是 LLM 意图识别，我把所有工具的 Schema 描述 + 最近5条历史消息构造一个 Prompt，让 LLM 从工具列表中选择最合适的意图并提取参数。
>
> 用双层是因为：简单场景走本地规则可以秒回，避免 LLM 调用延迟；复杂场景（如指代消解 "再来一个"）需要上下文理解，必须走 LLM。如果 LLM 失败，还有关键词回退兜底。

---

### Q3: RAG 怎么保证检索质量？

**回答**：

> 三个手段：
> 1. **向量检索**：用 text-embedding-v1 把文档和查询都转成1536维向量，PgVector 做 COSINE 相似度检索，HNSW 索引加速
> 2. **重排序**：在向量结果基础上，统计查询关键词在文档中的命中次数，每命中一个加 0.05 分，和向量分数加权求和后重排序
> 3. **缓存**：Caffeine 缓存 RAG 结果，相同问题直接返回，降低延迟和成本
>
> 检索日志记录每次查询的召回结果和延迟，方便后续分析优化。

---

### Q4: JWT 双令牌机制是怎么设计的？

**回答**：

> Access Token 15分钟有效，用于 API 认证；Refresh Token 7天有效，专门用于刷新 Access Token。两个 Token 都是 JWT 格式，但 Refresh Token 额外带一个 `type=refresh` 标记。
>
> JwtAuthenticationFilter 只处理 type=access 的 Token。前端在 Token 过期前2分钟自动用 Refresh Token 调 `/api/auth/refresh` 获取新的 Token 对。401 时先尝试 refresh，失败才跳登录页。这样用户体验上是无感续期的。

---

### Q5: 流式输出是怎么实现的？

**回答**：

> 后端用 Spring WebFlux 的 `Flux<String>`，ChatClient 调用 `.stream()` 而不是 `.call()`，返回 SSE（Server-Sent Events）格式的数据流。
>
> 前端用 `fetch` + `ReadableStream` 逐块读取 SSE 数据，每收到一个 `data:` 事件就追加到消息 DOM 并用 marked.js 渲染 Markdown，实现打字机效果。用户感知到首字延迟很低，体验流畅。

---

### Q6: 项目中遇到的最大困难是什么？

**回答模板**（选一个适合你的）：

> **意图识别的准确性**。一开始只用关键词匹配，很多模糊的问法识别不准。后来加了 LLM 意图识别，但又引入了延迟问题。最终设计了双层架构：简单场景走本地规则（毫秒级），复杂场景走 LLM（秒级），并且把历史消息注入 Prompt 解决指代消解。这样既保证了准确率又控制了延迟。
>
> **RAG 召回质量**。纯向量检索有时会漏掉关键词精确匹配的文档。通过增加关键词重排序解决了这个问题——在向量分数基础上加关键词命中权重，优先保留精确匹配的文档。

---

### Q7: 如果要扩展，你会怎么做？

**回答**：

> **短期**：
> - 接入 Rerank 模型（如 bge-reranker）替代关键词重排序，提升检索质量
> - 文档分块策略优化：引入 chunk overlap（重叠窗口），支持语义分块
> - 加入 Redis 做分布式限流和 Token 黑名单
>
> **中期**：
> - 知识图谱：从文档中抽取实体关系，构建课程知识图谱
> - 自适应出题：根据答题正确率动态调整出题难度
> - 艾宾浩斯遗忘曲线：基于复习间隔自动推送复习提醒
>
> **长期**：
> - 多模态：支持图片、公式、表格的理解
> - Agent 自我反思：出题后自动评估题目质量并优化 Prompt

---

## 📌 六、技术栈深度理解

### Spring AI

| 概念 | 说明 | 项目中的使用 |
|------|------|-------------|
| ChatClient | 统一对话客户端 | Agent 所有 LLM 调用的入口 |
| PromptTemplate | Prompt 模板引擎 | 10个模板文件，支持变量渲染 |
| VectorStore | 向量存储抽象 | PgVectorStore 实现 |
| EmbeddingModel | 文本向量化 | text-embedding-v1 (1536维) |
| ChatOptions | 模型参数 | temperature / model / maxTokens |

### PgVector

| 概念 | 说明 |
|------|------|
| HNSW 索引 | 分层导航小世界图，近似最近邻搜索，O(log n) 复杂度 |
| COSINE 距离 | 衡量向量方向相似度，与长度无关 |
| VECTOR(1536) | PostgreSQL 向量类型，存储 embedding |
| JSONB metadata | 灵活存储分块元数据（课程ID、位置等） |

### Spring Security

| 概念 | 说明 | 项目中的使用 |
|------|------|-------------|
| SecurityFilterChain | 安全过滤器链 | JWT Filter 插入 UsernamePasswordAuthenticationFilter 之前 |
| SecurityContext | 安全上下文 | 存储当前认证用户信息 |
| @RequestAttribute | 请求属性 | Controller 获取 currentUserId |
| RBAC | 角色访问控制 | ADMIN / USER 角色 |

### MyBatis-Plus

| 概念 | 说明 | 项目中的使用 |
|------|------|-------------|
| LambdaQueryWrapper | 类型安全查询 | `lambdaQuery().eq(User::getUsername, name)` |
| IService | 服务层抽象 | 继承即拥有 CRUD 方法 |
| BaseMapper | 数据层抽象 | 继承即拥有基础 SQL |
| @TableName | 表映射 | 实体类与数据库表映射 |

---

## 📌 七、项目数据统计

| 指标 | 数量 |
|------|------|
| Java 源文件 | ~50 个 |
| 前端页面 | 7 个 |
| API 接口 | 25+ 个 |
| 数据库表 | 9 个 |
| Prompt 模板 | 10 个 |
| 单元测试 | 60 个 |
| Agent 工具 | 6 个 |
| 配置类 | 13 个 |

---

## 📌 八、一句话总结（简历/自我介绍用）

> 独立开发了基于 Spring AI + RAG 的智能复习 Agent，实现了 LLM 意图识别 + 6大工具协作的 Agent 架构、PgVector 向量检索 + 重排序的 RAG 引擎、JWT 双令牌认证体系，60个单元测试覆盖核心逻辑，支持 DashScope/OpenAI/Ollama 多模型切换。
