-- ============================================================
--  数据库索引优化
--  日期：2026-09-19
--
--  【背景】
--  原 init.sql 为每个「会被过滤的列」建了一个单列索引。这在数据量小的时候没问题，
--  但数据增长后会暴露三个问题：
--    1. 单列索引只能过滤，不能同时满足 ORDER BY → 每次都要额外排序（Sort / Disk Spill）；
--    2. 少数索引对应的列代码里「只写不查」（或已被唯一约束覆盖）→ 纯写入负担；
--    3. 部分高频查询（JSON 元数据、模糊搜索、会话首条消息）完全没有索引 → 全表扫描。
--
--  【优化原则】
--    a) 复合索引遵循「等值列在前、排序列在后」，让过滤与排序共用同一个索引；
--    b) 单列索引若已被某个复合索引的前缀覆盖，则删除（避免重复维护）；
--    c) 只用写入的列不建索引；
--    d) 模糊匹配（LIKE '%x%'）用 pg_trgm GIN 索引，B-tree 对前导通配符无效。
--
--  【实测】
--  在独立库 idx_bench 中以 240 万行数据（含重度用户/超长会话倾斜）对比，
--  22 条真实业务查询总耗时 335ms → 2ms，详见 db/benchmark/。
--
--  【执行】
--  psql -d review_db -f db/migration/2026-09-19-index-optimization.sql
--  ⚠️ 若表已很大（千万行级），请把 CREATE INDEX 改为 CREATE INDEX CONCURRENTLY
--     （不阻塞读写，但不能在事务块中执行）。本脚本中各语句彼此独立，
--     psql 默认逐条自动提交，可直接替换。
-- ============================================================

\pset pager off

-- ============================================================
-- 0. 扩展：模糊搜索需要 pg_trgm
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- 1. 删除冗余 / 无用的索引
-- ============================================================
-- app_user：idx_app_user_username 与 UNIQUE 约束自动生成的 app_user_username_key 完全重复
DROP INDEX IF EXISTS idx_app_user_username;

-- courses：列 name 从不单独查询（始终与 user_id 一起），由 (user_id, name) 复合索引取代
DROP INDEX IF EXISTS idx_courses_name;

-- conversation：course_id 只写不查（仅用于统计回溯，无 WHERE 过滤），索引纯属写入负担
DROP INDEX IF EXISTS idx_conversation_course;

-- agent_log：intent 只写不查（统计在应用层按 intent 分组，不依赖 SQL 过滤）
DROP INDEX IF EXISTS idx_agent_log_intent;

-- study_plan：created_at 从不单独查询，由 (user_id, created_at) 复合索引取代
DROP INDEX IF EXISTS idx_study_plan_created_at;

-- wrong_answer_book：is_mastered 始终与 user_id 一起出现，单列索引选择性极差（只有两个值）
DROP INDEX IF EXISTS idx_wrong_book_mastered;

-- wrong_answer_book：course_id 也只在与 user_id 同时过滤时出现（错题列表按用户+可选课程筛选），
-- 单列索引无法被有效利用
DROP INDEX IF EXISTS idx_wrong_book_course;

-- 以下单列索引均被「同名前缀的复合索引」覆盖，删除以避免重复维护
DROP INDEX IF EXISTS idx_message_conversation;      -- → (conversation_id, created_at) / (conversation_id, id)
DROP INDEX IF EXISTS idx_conversation_user;         -- → (user_id, updated_at DESC)
DROP INDEX IF EXISTS idx_agent_log_conversation;    -- → (conversation_id, created_at DESC)
DROP INDEX IF EXISTS idx_rag_search_conversation;   -- → (conversation_id, created_at DESC)
DROP INDEX IF EXISTS idx_review_user;               -- → (user_id, created_at DESC)
DROP INDEX IF EXISTS idx_review_course;             -- → (course_id, user_id, created_at)
DROP INDEX IF EXISTS idx_wrong_book_user;           -- → (user_id, last_wrong_at DESC)
DROP INDEX IF EXISTS idx_knowledge_document_course; -- → (course_id, id)
DROP INDEX IF EXISTS idx_courses_user;              -- → (user_id, updated_at DESC) / (user_id, name)
DROP INDEX IF EXISTS idx_study_plan_user;           -- → (user_id, created_at DESC)
DROP INDEX IF EXISTS idx_knowledge_document_course_name; -- → (course_id, file_name, id DESC)

-- ============================================================
-- 2. message：对话消息（随聊天线性增长，是本项目最大的表）
-- ============================================================
-- 会话时间线：GET /api/messages?conversationId=x 按 created_at 升序返回
-- 历史上下文：loadHistory 取最近 10 条 —— 过滤 + 排序共用一个索引，消除 Sort
CREATE INDEX IF NOT EXISTS idx_message_conv_created
    ON message (conversation_id, created_at);

-- 会话首条消息（编辑后同步标题）与消息截断（编辑/删除）都按 id 定位
-- 原实现只有 (conversation_id)，取首条时需把该会话全部消息排序 → 实测 193ms
CREATE INDEX IF NOT EXISTS idx_message_conv_id
    ON message (conversation_id, id);

-- ============================================================
-- 3. conversation：会话列表与过期清理
-- ============================================================
-- 会话侧栏：eq(user_id) + ORDER BY updated_at DESC
CREATE INDEX IF NOT EXISTS idx_conversation_user_updated
    ON conversation (user_id, updated_at DESC);

-- 每日定时清理过期会话：WHERE updated_at < now() - interval '30 days'
-- （原脚本漏建，实测全表扫描 17ms 且随会话数线性增长）
CREATE INDEX IF NOT EXISTS idx_conversation_updated_at
    ON conversation (updated_at);

-- ============================================================
-- 4. agent_log / rag_search_log：观测日志
-- ============================================================
-- 日志页面：eq(conversation_id) + ORDER BY created_at DESC LIMIT 50
CREATE INDEX IF NOT EXISTS idx_agent_log_conv_created
    ON agent_log (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_search_conv_created
    ON rag_search_log (conversation_id, created_at DESC);

-- 注：idx_agent_log_created_at / idx_rag_search_created_at 保留
--     （统计接口按时间范围聚合，会用到）

-- ============================================================
-- 5. review_records：复习记录（学习统计的数据源）
-- ============================================================
-- 用户维度：eq(user_id) + ORDER BY created_at
-- （listByUser、getUserOverallProgress；原实现只有单列 user_id → 实测 65ms + 磁盘排序）
CREATE INDEX IF NOT EXISTS idx_review_user_created
    ON review_records (user_id, created_at DESC);

-- 学习进度：eq(course_id) + eq(user_id) + ORDER BY created_at ASC
CREATE INDEX IF NOT EXISTS idx_review_course_user_created
    ON review_records (course_id, user_id, created_at);

-- 课程维度：eq(course_id) + ORDER BY created_at DESC（GET /api/review-records/course/{id}）
CREATE INDEX IF NOT EXISTS idx_review_course_created
    ON review_records (course_id, created_at DESC);

-- 级联删除会话数据：ConversationLifecycleService 按 conversation_id 删四张表
-- （其余三张已有索引，唯独这里漏建 → 删除会话时会全表扫描 review_records）
CREATE INDEX IF NOT EXISTS idx_review_conversation
    ON review_records (conversation_id);

-- ============================================================
-- 6. wrong_answer_book：错题本
-- ============================================================
-- 错题列表：eq(user_id) + ORDER BY last_wrong_at DESC（原为全表扫描 + 排序，实测 16ms）
CREATE INDEX IF NOT EXISTS idx_wrong_user_last_wrong
    ON wrong_answer_book (user_id, last_wrong_at DESC);

-- 错题筛选（未掌握/已掌握）+ 列表排序；两个等值列在前、排序列在后
CREATE INDEX IF NOT EXISTS idx_wrong_user_mastered_last_wrong
    ON wrong_answer_book (user_id, is_mastered, last_wrong_at DESC);

-- 入库前查重：eq(user_id) + eq(question) + eq(is_mastered)
-- 注：question 为 TEXT，B-tree 单键上限约 2704 字节；当前最长 103 字符，安全。
--     若将来允许超长题目，应改为 (user_id, md5(question)) 并同步调整查询。
CREATE INDEX IF NOT EXISTS idx_wrong_user_question
    ON wrong_answer_book (user_id, question);

-- 【刻意不建】关键词搜索的 pg_trgm 索引：
--   查询形态恒为 `WHERE user_id = ? AND (question LIKE %kw% OR topic LIKE %kw% OR ...)`，
--   即 trgm 永远只是「user_id 之后的第二道过滤」。实测（单用户 5 万道错题）：
--     · 命中时  → 走 idx_wrong_user_last_wrong 直接取前 50 条，0.37ms（trgm 方案需全量匹配再排序，更慢）
--     · 未命中时 → 11.8ms；而错题本正常规模（单用户数百条）下 <1ms
--   权衡：错题本会频繁 UPDATE（重做时递增 wrong_count、标记已掌握），
--   3 个 GIN 索引的写入放大远超其收益，故不建。

-- ============================================================
-- 7. courses / study_plan：课程与学习计划
-- ============================================================
-- 课程列表：eq(user_id) + ORDER BY updated_at DESC
CREATE INDEX IF NOT EXISTS idx_courses_user_updated
    ON courses (user_id, updated_at DESC);

-- 对话绑定课程：eq(user_id) + eq(name)（resolveCourseIdFromMessage 按课程名反查）
CREATE INDEX IF NOT EXISTS idx_courses_user_name
    ON courses (user_id, name);

-- 学习计划列表：eq(user_id) + ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_study_plan_user_created
    ON study_plan (user_id, created_at DESC);

-- ============================================================
-- 8. knowledge_document：知识库原始资料
-- ============================================================
-- 按课程取资料并按 id 升序（listDocuments / getDocumentContent 回退拼接）
CREATE INDEX IF NOT EXISTS idx_knowledge_document_course_id
    ON knowledge_document (course_id, id);

-- 按课程 + 文件名重传覆盖（eq course_id + eq file_name + ORDER BY id DESC）
CREATE INDEX IF NOT EXISTS idx_knowledge_document_course_name_id
    ON knowledge_document (course_id, file_name, id DESC);

-- ============================================================
-- 9. vector_store：知识库向量分块
-- ============================================================
-- 分块查询走 JSON 元数据：WHERE metadata->>'courseId' = ? ORDER BY (metadata->>'chunkIndex')::int
-- （DocumentServiceImpl 的 listChunks / countChunks / deleteChunksByCourseId）
-- 普通 B-tree 无法索引 JSON 内部字段，需用表达式索引；
-- 两个表达式一起建，让过滤与排序共用同一个索引。
CREATE INDEX IF NOT EXISTS idx_vector_store_course_chunk
    ON vector_store (((metadata ->> 'courseId')), (((metadata ->> 'chunkIndex'))::int));

-- RAG 关键词兜底检索：WHERE content ILIKE '%关键词%' LIMIT 3
-- 必须在建：该查询没有其它过滤条件，唯一的「无匹配」场景正是 RAG 兜底的常态。
-- 实测（5.2 万块 / 每块约 500 字符）：
--   · 无索引且无匹配 → 全表扫描 416ms（随分块数线性增长）
--   · 有索引且无匹配 → 0.03ms（GIN 直接判定不存在）
--   · 有索引且命中    → 5ms
-- 换言之，这个索引把「问了个知识库里没有的东西」从不可接受变成瞬时。
CREATE INDEX IF NOT EXISTS idx_vector_store_content_trgm
    ON vector_store USING gin (content gin_trgm_ops);

-- 注：spring_ai_vector_index（HNSW 向量索引）由 Spring AI 自动维护，勿动。

-- ============================================================
-- 10. 刷新统计信息
--     优化器依赖统计信息选择索引；不刷新可能出现「索引建了却不用」的情况。
-- ============================================================
ANALYZE;

-- ============================================================
-- 11. 结果核对
-- ============================================================
\echo ''
\echo '==================== 优化后的索引清单 ===================='
SELECT tablename AS "表",
       indexname AS "索引",
       CASE
           WHEN indexdef LIKE '%gin%' THEN 'GIN(模糊/JSON)'
           WHEN indexdef LIKE '%DESC%' THEN 'B-tree(含排序)'
           ELSE 'B-tree'
       END AS "类型"
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname LIKE 'idx_%'
ORDER BY tablename, indexname;

\echo ''
\echo '==================== 各表索引数量 ===================='
SELECT tablename AS "表", count(*) AS "索引数"
FROM pg_indexes
WHERE schemaname = 'public'
GROUP BY tablename
ORDER BY count(*) DESC;
