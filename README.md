# 📚 期末复习智能助手（AI Review Assistant）

> 基于 Spring Boot 3.2 + Spring AI + RAG 构建的智能期末复习 Agent，支持知识库管理、RAG 增强问答、交互式练习、复习计划制定、错题本和学习统计。

---

## 📋 目录

- [功能特性](#功能特性)
- [技术架构](#技术架构)
- [技术栈](#技术栈)
- [快速启动](#快速启动)
- [页面说明](#页面说明)
- [API 接口文档](#api-接口文档)
- [项目结构](#项目结构)
- [数据库设计](#数据库设计)
- [环境配置](#环境配置)
- [测试](#测试)
- [部署](#部署)
- [开发计划](#开发计划)

---

## 功能特性

| 模块 | 功能 | 说明 |
|------|------|------|
| 💬 **智能对话** | Agent 多轮对话 | LLM 意图识别 + 6大工具协作，SSE 流式逐字输出 |
| 📖 **课程管理** | 课程 CRUD | 按用户隔离，支持新建/编辑/删除 |
| 🗄️ **知识库** | 文档上传向量化 | 支持 txt/md/pdf/docx，自动分块 + PgVector 向量存储 |
| 📝 **交互练习** | AI 出题 + 自动批改 | 选择题交互作答，一键批改，正确率统计，答错自动入错题本 |
| 📋 **复习计划** | AI 生成计划 | 根据课程和天数自动生成每日复习安排 |
| 📕 **错题本** | 错题收集回顾 | 答错自动入库，支持标记掌握/删除，统计掌握率 |
| 📊 **学习统计** | 进度追踪 | 综合评分、活跃天数、连续复习天数、掌握度评估 |
| 🔐 **用户系统** | JWT 双令牌认证 | Access Token(15min) + Refresh Token(7天) |
| 🛡️ **安全** | 接口限流 + 权限控制 | Bucket4j 令牌桶限流，Spring Security + RBAC |
| 🔄 **Agent 工具链** | 多步任务编排 | "先...再...然后..." 形式的多工具协作执行 |

---

## 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    前端层 (Vue 3 SPA, frontend/)                 │
│  Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router  │
│  开发: Vite Dev Server(5173) → 代理 /api → 后端(8080)            │
│  生产: Vite 构建产物 → Spring Boot 托管 (src/main/resources/static)│
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP / SSE
┌───────────────────────────┴─────────────────────────────────────┐
│                      控制器层 (REST API /api/**)                 │
│  AuthController │ AgentController │ CoursesController │ ...     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────┴─────────────────────────────────────┐
│                    安全层 (Spring Security + JWT)                │
│  JwtAuthenticationFilter → SecurityFilterChain → RBAC           │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────┴─────────────────────────────────────┐
│                     业务层 (Service + Agent)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ ReviewAssistant│  │   RagService │  │   ToolRegistry        │  │
│  │    Agent       │  │  (向量检索+   │  │  ┌─────────────────┐ │  │
│  │  (意图识别+    │  │   关键词重排)  │  │  │ QUESTION │ QUIZ │ │  │
│  │   工具调度)    │  └──────┬───────┘  │  │ PLAN     │ CHAT │ │  │
│  └──────┬───────┘         │          │  │ SUMMARY  │EXPLAIN│ │  │
│         │                 │          │  └─────────────────┘ │  │
│         │                 │          └───────────────────────┘  │
│  ┌──────┴─────────────────┴──────────────────────────────────┐  │
│  │              Spring AI (ChatClient)                        │  │
│  │         DashScope / OpenAI / Ollama 多模型支持              │  │
│  └───────────────────────────────────────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────┴─────────────────────────────────────┐
│                       数据层                                     │
│  PostgreSQL + pgvector │ MyBatis-Plus │ Caffeine Cache          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 技术栈

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **框架** | Spring Boot | 3.2.10 | 应用主体框架 |
| **AI** | Spring AI + Alibaba | 1.0.0 | LLM 集成、向量存储、Prompt 模板 |
| **AI模型** | DashScope (通义千问) | - | qwen-plus / qwen-turbo |
| **安全** | Spring Security + JWT | - | 认证授权、双令牌机制 |
| **ORM** | MyBatis-Plus | 3.5.6 | 数据库访问、代码生成 |
| **数据库** | PostgreSQL + pgvector | 16 | 业务数据 + 向量存储 |
| **缓存** | Caffeine | 3.1.8 | 本地缓存（LLM 结果、RAG 结果） |
| **限流** | Bucket4j | 8.14.0 | 令牌桶限流 |
| **文档** | SpringDoc OpenAPI | 2.6.0 | Swagger API 文档 |
| **前端** | Thymeleaf + Bootstrap 5 | - | 模板引擎 + UI 框架 |
| **Markdown** | marked.js + DOMPurify | - | AI 回复渲染 + XSS 防护 |
| **监控** | Actuator + Micrometer + Prometheus | - | 健康检查、指标导出 |
| **容器** | Docker + Docker Compose | - | PostgreSQL 部署 |
| **测试** | JUnit 5 + Mockito + Testcontainers | - | 单元/集成测试 |

---

## 快速启动

### 前置条件

- JDK 17+
- Maven 3.8+
- Node.js 18+（前端构建）
- Docker & Docker Compose
- DashScope API Key（[获取地址](https://dashscope.console.aliyun.com/)）

### 方式一：启动脚本（推荐）

```bash
# 1. 配置环境变量
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxx

# 2. 一键启动（自动构建前端 + 后端）
./run.sh start

# 3. 访问
open http://localhost:8080/login
```

### 方式二：手动启动（生产模式，Spring Boot 托管前端）

```bash
# 1. 启动 PostgreSQL
docker-compose up -d

# 2. 构建前端（产物输出到 src/main/resources/static）
cd frontend && npm install && npm run build && cd ..

# 3. 编译打包后端
./mvnw clean package -DskipTests

# 4. 运行
java -jar target/ai-review-assistant-0.0.1-SNAPSHOT.jar
```

### 方式三：开发模式（前后端分离联调）

```bash
# 终端 1：启动后端
./mvnw spring-boot:run

# 终端 2：启动前端 Vite Dev Server（5173 端口，/api 自动代理到 8080）
cd frontend && npm install && npm run dev

# 访问 http://localhost:5173 ，前端热更新，API 走代理联调
```

### 启动脚本命令

```bash
./run.sh start     # 启动 PostgreSQL + 应用
./run.sh stop      # 停止应用
./run.sh restart   # 重启
./run.sh build     # 编译打包
./run.sh test      # 运行测试
./run.sh logs      # 查看实时日志
./run.sh status    # 查看服务状态
./run.sh help      # 帮助
```

---

## 页面说明

| 页面 | 地址 | 说明 |
|------|------|------|
| 🔑 登录注册 | `/login` | JWT 认证，注册后自动登录 |
| 💬 智能对话 | `/` | SSE 流式对话，会话侧栏，Markdown 渲染 |
| 📖 课程管理 | `/courses` | 课程 CRUD，跳转对话 |
| 🗄️ 知识库 | `/knowledge` | 上传文档，查看知识分块（分页） |
| 📝 交互练习 | `/quiz` | AI 出题，选择作答，自动批改 |
| 📋 复习计划 | `/plan` | AI 生成每日复习计划 |
| 📊 学习统计 | `/stats` | 综合评分，错题本管理 |
| 📡 API 文档 | `/swagger-ui/index.html` | OpenAPI 3.0 交互式文档 |
| 🏥 健康检查 | `/actuator/health` | 服务健康状态 |

---

## API 接口文档

### 认证接口

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | ❌ |
| POST | `/api/auth/login` | 用户登录（返回 Access + Refresh Token） | ❌ |
| POST | `/api/auth/refresh` | 刷新 Access Token | ❌ |
| GET | `/api/auth/me` | 获取当前用户信息 | ✅ |

### Agent 对话

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/agent/chat` | 普通对话（自动意图识别） | ❌ |
| GET | `/api/agent/stream` | SSE 流式对话 | ❌ |

### 课程管理

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/courses` | 查询我的课程列表 | ✅ |
| GET | `/api/courses/{id}` | 查询课程详情 | ✅ |
| POST | `/api/courses` | 新建课程 | ✅ |
| PUT | `/api/courses/{id}` | 更新课程 | ✅ |
| DELETE | `/api/courses/{id}` | 删除课程 | ✅ |

### 知识库

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/document/upload` | 上传文档并自动向量化 | ✅ |
| POST | `/api/document/import` | 直接导入文本内容 | ✅ |
| GET | `/api/knowledge-base/{id}/chunks?page=1&pageSize=20` | 查询知识分块（分页） | ✅ |
| GET | `/api/knowledge-base/{id}/count` | 统计分块数量 | ✅ |
| DELETE | `/api/knowledge-base/{id}` | 删除课程下所有分块 | ✅ |

### 练习题

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/quiz/generate` | 生成练习题（body: courseId/topic/count） | ✅ |
| POST | `/api/quiz/grade` | 提交答案并自动批改（答错入错题本） | ✅ |

### 复习计划

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/plan?course=xx&days=7` | 生成复习计划 | ✅ |
| POST | `/api/plan/generate` | 生成复习计划（body: courseId/days） | ✅ |

### 错题本

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/wrong-book` | 查询错题列表 | ✅ |
| PUT | `/api/wrong-book/{id}/master` | 标记错题已掌握 | ✅ |
| DELETE | `/api/wrong-book/{id}` | 删除错题 | ✅ |
| GET | `/api/wrong-book/stats` | 错题统计 | ✅ |

### 学习统计

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/stats/overview` | 用户整体学习进度 | ✅ |
| GET | `/api/stats/course/{id}` | 课程学习进度 | ✅ |

---

## 项目结构

```
├── frontend/                            # ⭐ 前端项目（Vue 3 SPA，独立管理）
│   ├── src/
│   │   ├── api/                         # API 封装（axios + JWT 刷新拦截器）
│   │   ├── components/                  # 公共组件（NavBar/AppLayout/MarkdownRenderer）
│   │   ├── router/                      # Vue Router（干净路径路由）
│   │   ├── stores/                      # Pinia 状态（auth/chat）
│   │   ├── views/                       # 页面（Login/Chat/Courses/Knowledge/Quiz/Plan/Stats）
│   │   ├── main.ts                      # 入口
│   │   └── styles/                      # 全局样式（设计系统 CSS 变量）
│   ├── public/                          # 静态资源（favicon）
│   ├── vite.config.ts                   # Vite 配置（dev 代理 /api → 8080）
│   └── package.json
│
└── src/main/
    ├── java/com/aiservice/aireviewassistant/
    │   ├── AiReviewAssistantApplication.java    # 启动类
    │   ├── agent/tool/                          # Agent 工具层（6大工具）
    │   ├── annotation/                          # 自定义注解（RateLimit）
    │   ├── aspect/                              # AOP 切面（Bucket4j 限流）
    │   ├── common/                              # 统一响应体 ApiResponse
    │   ├── config/                              # 配置类（JWT/Security/VectorStore...）
    │   ├── controller/                          # REST API 控制器（/api/**）
    │   ├── dto/                                 # 数据传输对象
    │   ├── entity/                              # 数据库实体
    │   ├── exception/                           # 全局异常处理
    │   ├── mapper/                              # MyBatis-Plus Mapper
    │   ├── metrics/                             # 监控指标
    │   ├── security/                            # JWT 认证过滤器
    │   └── service/                             # 业务逻辑层（Agent/RAG 核心）
    │
    └── resources/
        ├── application.yml                     # 主配置
        ├── application-prod.yml                # 生产环境配置
        ├── prompts/                            # Prompt 模板
        ├── static/                             # ⭐ Vue 构建产物（生产托管）
        └── logback-spring.xml                  # 日志配置
```

---

## 数据库设计

### ER 图

```
┌──────────────┐     1:N     ┌──────────────┐     1:N     ┌──────────────┐
│   app_user   │────────────▶│   courses    │────────────▶│wrong_answer  │
│──────────────│             │──────────────│             │    _book     │
│ id (PK)      │             │ id (PK)      │             │──────────────│
│ username     │             │ user_id (FK) │             │ id (PK)      │
│ password     │             │ name         │             │ user_id (FK) │
│ nickname     │             │ description  │             │ course_id(FK)│
│ role         │             │ created_at   │             │ question     │
└──────┬───────┘             └──────────────┘             │ correct_ans  │
       │                                                  │ is_mastered  │
       │ 1:N                                              └──────────────┘
       ▼
┌──────────────┐     1:N     ┌──────────────┐
│ conversation │────────────▶│   message    │
│──────────────│             │──────────────│
│ id (PK)      │             │ id (PK)      │
│ user_id      │             │ conversation │
│ course_id    │             │   _id (FK)   │
└──────────────┘             │ role / intent│
                             └──────────────┘

┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  agent_log   │  │rag_search_log│  │ vector_store │
│ (调用日志)    │  │ (检索日志)    │  │ (PgVector)   │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 表说明

| 表名 | 说明 | 关键索引 |
|------|------|---------|
| `app_user` | 用户表 | `username` UNIQUE |
| `courses` | 课程表 | `user_id` |
| `conversation` | 会话表 | `user_id`, `course_id` |
| `message` | 消息表 | `conversation_id` |
| `review_records` | 复习记录 | `course_id`, `user_id` |
| `wrong_answer_book` | 错题本 | `user_id`, `is_mastered` |
| `agent_log` | Agent 调用日志 | `intent`, `created_at` |
| `rag_search_log` | RAG 检索日志 | `created_at` |
| `vector_store` | PgVector 向量 | HNSW 索引 (COSINE) |

---

## 环境配置

### 环境变量

| 变量 | 说明 | 必填 | 默认值 |
|------|------|------|--------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key | ✅ | - |
| `DB_USERNAME` | 数据库用户名 | ✅ | postgres |
| `DB_PASSWORD` | 数据库密码 | ✅ | postgres |
| `JWT_SECRET` | JWT 签名密钥（≥32字节） | 生产必须 | 内置开发密钥 |
| `AI_PROVIDER` | 模型供应商 | ❌ | dashscope |
| `AI_MODEL` | 模型名称 | ❌ | qwen-plus |
| `SERVER_PORT` | 服务端口 | ❌ | 8080 |

### 多模型切换

```yaml
# DashScope (通义千问)
ai.model.provider: dashscope
ai.model.name: qwen-plus

# OpenAI
ai.model.provider: openai
ai.model.name: gpt-4

# Ollama (本地)
ai.model.provider: ollama
ai.model.name: llama3
```

---

## 测试

```bash
# 运行全部测试（60个）
./mvnw test

# 运行指定测试类
./mvnw test -Dtest="ReviewAssistantAgentTest"

# 运行工具测试
./mvnw test -Dtest="*ToolTest"
```

### 测试覆盖

| 测试类 | 数量 | 说明 |
|--------|------|------|
| AgentMetricsTest | 7 | 指标收集 |
| ChainExecutorTest | 4 | 工具链执行 |
| ChatToolTest | 3 | 聊天工具 |
| ExplainToolTest | 4 | 解释工具 |
| PlanToolTest | 5 | 计划工具 |
| QuizToolTest | 6 | 出题工具 |
| QuestionToolTest | 2 | 问答工具 |
| SummaryToolTest | 4 | 摘要工具 |
| AgentServiceCacheTest | 4 | 缓存验证 |
| ReviewAssistantAgentTest | 10 | Agent 核心 |
| RagServiceImplTest | 5 | RAG 引擎 |
| ActuatorEndpointTest | 4 | 监控端点 |
| ApplicationTests | 1 | 上下文加载 |

---

## 部署

### 生产启动

```bash
export DB_USERNAME=xxx
export DB_PASSWORD=xxx
export JWT_SECRET=your-64-char-random-secret
export DASHSCOPE_API_KEY=sk-xxx

java -jar target/ai-review-assistant-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

### Docker Compose 完整部署

```yaml
version: '3.8'
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: review_db
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_URL=jdbc:postgresql://postgres:5432/review_db
      - DB_USERNAME=${DB_USERNAME}
      - DB_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - DASHSCOPE_API_KEY=${DASHSCOPE_API_KEY}
    depends_on:
      - postgres

volumes:
  pgdata:
```

---

## 开发计划

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段一 | 用户系统 + 课程管理 + JWT 认证 | ✅ 完成 |
| 阶段二 | 前端重构（6个页面 + SSE流式 + Markdown） | ✅ 完成 |
| 阶段三 | Agent 增强（批改 + 错题本 + 统计） | ✅ 完成 |
| 优化一轮 | 安全加固 + Refresh Token + RAG流式 + 分页 | ✅ 完成 |
| 阶段四 | RAG 质量提升（语义分块 + Rerank 模型） | 🔲 计划中 |
| 阶段五 | 工程化（CI/CD + 多环境 + E2E 测试） | 🔲 计划中 |

---

## License

MIT License
