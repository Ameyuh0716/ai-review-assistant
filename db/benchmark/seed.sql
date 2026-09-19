-- ============================================================
--  索引优化基准测试：数据生成脚本
--  用途：在独立库 idx_bench 中生成"大数据量"场景，
--        用于对比加索引前后的查询计划与耗时。
--  注意：绝不在生产库 review_db 上执行。
-- ============================================================

\timing on

-- ---------- 数据规模（可按需调整） ----------
-- app_user          1,000
-- courses          20,000
-- conversation     50,000
-- message         500,000
-- agent_log       200,000
-- rag_search_log  200,000
-- review_records  500,000
-- wrong_answer_book 100,000
-- study_plan       20,000
-- vector_store      2,000

-- ---------- app_user ----------
INSERT INTO app_user (username, password, nickname, role)
SELECT 'user' || i, 'pwd', '昵称' || i, 'USER'
FROM generate_series(1, 1000) i;

-- ---------- courses ----------
INSERT INTO courses (user_id, name, description)
SELECT (i % 1000) + 1,
       '课程-' || i,
       '这是第 ' || i || ' 门课程的描述文本，用于模拟真实数据长度。'
FROM generate_series(1, 20000) i;

-- ---------- conversation ----------
INSERT INTO conversation (title, user_id, course_id, created_at, updated_at)
SELECT '会话标题-' || i,
       ((i % 1000) + 1)::text,
       (i % 20000) + 1,
       now() - (i || ' minutes')::interval,
       now() - (i || ' minutes')::interval
FROM generate_series(1, 50000) i;

-- ---------- message（每会话 10 条，共 50 万） ----------
INSERT INTO message (conversation_id, role, content, intent, created_at)
SELECT c.id,
       CASE WHEN m % 2 = 0 THEN 'user' ELSE 'assistant' END,
       '这是会话 ' || c.id || ' 的第 ' || m || ' 条消息内容，用于模拟真实对话文本长度。',
       CASE WHEN m % 2 = 0 THEN NULL ELSE 'CHAT' END,
       c.created_at + (m || ' seconds')::interval
FROM conversation c
CROSS JOIN generate_series(0, 9) m;

-- ---------- agent_log ----------
INSERT INTO agent_log (conversation_id, user_message, intent, parameters, latency_ms, success, created_at)
SELECT (i % 50000) + 1,
       '日志消息-' || i,
       (ARRAY['CHAT','EXPLAIN','QUIZ','PLAN','SUMMARY'])[(i % 5) + 1],
       '{}',
       (i % 3000) + 100,
       true,
       now() - (i || ' seconds')::interval
FROM generate_series(1, 200000) i;

-- ---------- rag_search_log ----------
INSERT INTO rag_search_log (conversation_id, query, result_count, top_k, retrieved_chunks, latency_ms, success, created_at)
SELECT (i % 50000) + 1,
       '检索查询-' || i,
       i % 4,
       3,
       '[]',
       (i % 2000) + 50,
       true,
       now() - (i || ' seconds')::interval
FROM generate_series(1, 200000) i;

-- ---------- review_records ----------
INSERT INTO review_records (conversation_id, course_id, user_id, question, answer, created_at)
SELECT (i % 50000) + 1,
       (i % 20000) + 1,
       ((i % 1000) + 1)::text,
       '复习问题-' || i || '：请解释该知识点的核心概念。',
       '复习答案-' || i || '：该知识点的核心概念包含若干要点，需要逐一掌握。',
       now() - (i || ' seconds')::interval
FROM generate_series(1, 500000) i;

-- ---------- wrong_answer_book ----------
INSERT INTO wrong_answer_book (user_id, course_id, question, options, correct_answer, user_answer,
                               explanation, topic, is_mastered, wrong_count, last_wrong_at, created_at, updated_at)
SELECT (i % 1000) + 1,
       (i % 20000) + 1,
       '错题题目-' || i || '：下列关于该知识点的说法正确的是？',
       '["选项A", "选项B", "选项C", "选项D"]',
       'A',
       'B',
       '解析-' || i || '：该选项错误的原因是忽略了边界条件。',
       (ARRAY['进程同步','数据库索引','TCP握手','二叉树','排序算法'])[(i % 5) + 1],
       (i % 3 = 0),
       (i % 5) + 1,
       now() - (i || ' seconds')::interval,
       now() - (i || ' seconds')::interval,
       now() - (i || ' seconds')::interval
FROM generate_series(1, 100000) i;

-- ---------- study_plan ----------
INSERT INTO study_plan (user_id, course_name, available_days, content, progress, created_at, updated_at)
SELECT (i % 1000) + 1,
       '课程-' || i,
       '7天',
       '# 复习计划 ' || i || E'\n\n## 总体安排\n本计划覆盖核心知识点，分为七天逐步推进。',
       '{}',
       now() - (i || ' minutes')::interval,
       now() - (i || ' minutes')::interval
FROM generate_series(1, 20000) i;

-- ---------- vector_store（metadata 中含 courseId，用于知识库分块查询） ----------
-- 2 万块、每块约 400 字符：对应「几十份资料、每份数百块」的真实知识库规模。
-- 内容长度很重要——它决定 ILIKE 全表扫描的代价（trgm 索引的收益正在于此）。
INSERT INTO vector_store (id, content, metadata, embedding)
SELECT gen_random_uuid(),
       '这是课程 ' || c || ' 的第 ' || k || ' 个知识库分块内容。'
       || repeat('本段补充该知识点的定义、原理、适用场景与常见误区，并给出典型例题的推导过程。', 6),
       json_build_object('courseId', c, 'chunkIndex', k, 'chunkTotal', 5)::json,
       NULL   -- 元数据查询不需要向量，留空以节省生成时间
FROM generate_series(1, 4000) c
CROSS JOIN generate_series(0, 4) k;

-- ============================================================
--  数据倾斜：模拟真实使用中的"重度用户 / 超长会话"
--  真实场景里数据从不均匀分布——少数用户有上千条复习记录，
--  少数会话有上千条消息。这些才是"数据变多后变慢"的典型受害者。
-- ============================================================

-- 3 个超长会话（各 2 万条消息）→ 会话 id 100001~100003
INSERT INTO conversation (title, user_id, course_id, created_at, updated_at)
SELECT '超长会话-' || i, '1', 1, now() - interval '1 day', now() - interval '1 day'
FROM generate_series(1, 3) i;

INSERT INTO message (conversation_id, role, content, intent, created_at)
SELECT 50000 + c,
       CASE WHEN m % 2 = 0 THEN 'user' ELSE 'assistant' END,
       '超长会话中的第 ' || m || ' 条消息内容，用于模拟重度使用场景下的长对话。',
       CASE WHEN m % 2 = 0 THEN NULL ELSE 'CHAT' END,
       now() - interval '1 day' + (m || ' seconds')::interval
FROM generate_series(1, 3) c
CROSS JOIN generate_series(0, 19999) m;

-- 3 个重度用户（各 10 万条复习记录）
INSERT INTO review_records (conversation_id, course_id, user_id, question, answer, created_at)
SELECT (i % 50000) + 1,
       (i % 20000) + 1,
       'heavy' || (i % 3),
       '重度用户复习问题-' || i,
       '重度用户复习答案-' || i,
       now() - (i || ' seconds')::interval
FROM generate_series(1, 300000) i;

-- 1 个重度错题用户（5 万道错题）
INSERT INTO wrong_answer_book (user_id, course_id, question, options, correct_answer, user_answer,
                               explanation, topic, is_mastered, wrong_count, last_wrong_at, created_at, updated_at)
SELECT 9001,
       (i % 20000) + 1,
       '重度错题-' || i,
       '["A","B","C","D"]',
       'A', 'B',
       '解析-' || i,
       (ARRAY['进程同步','数据库索引','TCP握手','二叉树','排序算法'])[(i % 5) + 1],
       (i % 2 = 0),
       (i % 5) + 1,
       now() - (i || ' seconds')::interval,
       now() - (i || ' seconds')::interval,
       now() - (i || ' seconds')::interval
FROM generate_series(1, 50000) i;

-- 重度会话的日志（各 3 万条）：观测页面按会话查日志时才会真正吃力
INSERT INTO agent_log (conversation_id, user_message, intent, parameters, latency_ms, success, created_at)
SELECT 50001 + (i % 3),
       '重度会话日志-' || i,
       (ARRAY['CHAT','EXPLAIN','QUIZ','PLAN','SUMMARY'])[(i % 5) + 1],
       '{}',
       (i % 3000) + 100,
       true,
       now() - (i || ' seconds')::interval
FROM generate_series(1, 90000) i;

INSERT INTO rag_search_log (conversation_id, query, result_count, top_k, retrieved_chunks, latency_ms, success, created_at)
SELECT 50001 + (i % 3),
       '重度会话检索-' || i,
       i % 4,
       3,
       '[]',
       (i % 2000) + 50,
       true,
       now() - (i || ' seconds')::interval
FROM generate_series(1, 90000) i;

-- ---------- 更新统计信息（否则优化器可能选错计划） ----------
ANALYZE;

-- ---------- 规模确认 ----------
SELECT relname AS 表, n_live_tup AS 行数, pg_size_pretty(pg_total_relation_size(relid)) AS 占用
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;
