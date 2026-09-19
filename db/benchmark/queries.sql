-- ============================================================
--  索引优化基准测试：查询探针
--  用途：对代码中真实使用的查询逐个 EXPLAIN ANALYZE，
--        用于对比加索引前后的执行计划与耗时。
--
--  参数（与 seed.sql 生成的数据规模对应）：
--    heavy_conv = 50001    超长会话（2 万条消息）
--    heavy_uid  = 'heavy0' 重度用户（10 万条复习记录）
--    heavy_wid  = 9001     重度错题用户（5 万道错题）
--
--  用法：psql -d idx_bench -f db/benchmark/queries.sql
-- ============================================================

\pset pager off
\set ON_ERROR_STOP on

\echo ''
\echo '#################### Q1 会话消息列表（重度会话 2 万条）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM message WHERE conversation_id = 50001 ORDER BY created_at ASC;

\echo ''
\echo '#################### Q2 历史上下文（LIMIT 10）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM message WHERE conversation_id = 50001 ORDER BY created_at ASC LIMIT 10;

\echo ''
\echo '#################### Q3 会话首条消息（旧写法，已从代码移除，仅作对照）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM message WHERE conversation_id = 50001 ORDER BY id ASC LIMIT 1;

\echo ''
\echo '#################### Q3b 会话首条消息（MIN 写法，仍受优化器估算影响）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT MIN(id) AS id FROM message WHERE conversation_id = 50001;

\echo ''
\echo '#################### Q4 截断（编辑提问/重新生成）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM message WHERE conversation_id = 50001 AND id >= 559900;

\echo ''
\echo '#################### Q5 会话列表（用户维度，按更新时间倒序）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM conversation WHERE user_id = '1' ORDER BY updated_at DESC;

\echo ''
\echo '#################### Q6 过期会话清理（每日定时任务）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM conversation WHERE updated_at < now() - interval '30 days';

\echo ''
\echo '#################### Q7 Agent 日志列表 ####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM agent_log WHERE conversation_id = 50001 ORDER BY created_at DESC LIMIT 50;

\echo ''
\echo '#################### Q8 Agent 日志统计（近 10 分钟）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT count(*) FROM agent_log WHERE created_at >= now() - interval '10 minutes';

\echo ''
\echo '#################### Q9 RAG 检索日志列表 ####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM rag_search_log WHERE conversation_id = 50001 ORDER BY created_at DESC LIMIT 50;

\echo ''
\echo '#################### Q10 复习记录列表（重度用户 10 万条）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM review_records WHERE user_id = 'heavy0' ORDER BY created_at DESC;

\echo ''
\echo '#################### Q11 学习进度（课程 + 用户）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM review_records WHERE course_id = 1 AND user_id = 'heavy0' ORDER BY created_at ASC;

\echo ''
\echo '#################### Q12 错题本列表（重度用户 5 万道）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM wrong_answer_book WHERE user_id = 9001 ORDER BY last_wrong_at DESC LIMIT 50;

\echo ''
\echo '#################### Q13 错题本筛选（未掌握 + 时间倒序）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM wrong_answer_book WHERE user_id = 9001 AND is_mastered = false ORDER BY last_wrong_at DESC LIMIT 50;

\echo ''
\echo '#################### Q14 错题去重查找（入库前查重）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM wrong_answer_book WHERE user_id = 9001 AND question = '重度错题-777' AND is_mastered = false;

\echo ''
\echo '#################### Q15 错题统计计数（已掌握）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT count(*) FROM wrong_answer_book WHERE user_id = 9001 AND is_mastered = true;

\echo ''
\echo '#################### Q16 学习计划列表 ####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM study_plan WHERE user_id = 1 ORDER BY created_at DESC;

\echo ''
\echo '#################### Q17 课程列表 ####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM courses WHERE user_id = 1 ORDER BY updated_at DESC;

\echo ''
\echo '#################### Q18 课程按名称查找（对话绑定课程）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM courses WHERE user_id = 1 AND name = '课程-1000';

\echo ''
\echo '#################### Q19 知识库分块（JSON 元数据查询）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT id, content, metadata FROM vector_store
WHERE metadata->>'courseId' = '100' ORDER BY (metadata->>'chunkIndex')::int;

\echo ''
\echo '#################### Q20 知识库分块计数 ####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT count(*) FROM vector_store WHERE metadata->>'courseId' = '100';

\echo ''
\echo '#################### Q21 RAG 关键词兜底检索（ILIKE 模糊匹配）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT id, content FROM vector_store WHERE content ILIKE '%第 3 个知识库分块%' LIMIT 3;

\echo ''
\echo '#################### Q22 错题本关键词搜索（无匹配）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM wrong_answer_book
WHERE user_id = 9001
  AND (question LIKE '%不存在的词%' OR topic LIKE '%不存在的词%' OR explanation LIKE '%不存在的词%')
ORDER BY last_wrong_at DESC LIMIT 50;

\echo ''
\echo '#################### Q22b 错题本关键词搜索（有匹配）####################'
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM wrong_answer_book
WHERE user_id = 9001
  AND (question LIKE '%解析%' OR topic LIKE '%解析%' OR explanation LIKE '%解析%')
ORDER BY last_wrong_at DESC LIMIT 50;
