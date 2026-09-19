-- 启用必要扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- pg_trgm：为 RAG 关键词兜底的 ILIKE '%kw%' 模糊匹配提供 GIN 索引支持
-- （B-tree 对前导通配符无效，无语义搜索只能靠三元组索引）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 创建用户表
CREATE TABLE IF NOT EXISTS app_user (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建课程表（存储课程基本信息，关联用户）
CREATE TABLE IF NOT EXISTS courses (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建复习记录表（存储用户复习记录）
-- 创建复习记录表（存储用户复习问答的沉淀记录，course_id 允许为空：未绑定课程的对话同样计入学习统计）
CREATE TABLE IF NOT EXISTS review_records (
    id SERIAL PRIMARY KEY,
    conversation_id INTEGER,
    course_id INTEGER,
    user_id VARCHAR(50),
    question TEXT,
    answer TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建会话表（存储多轮对话的会话信息）
CREATE TABLE IF NOT EXISTS conversation (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200),
    user_id VARCHAR(50),
    course_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建消息表（存储多轮对话的每条消息）
CREATE TABLE IF NOT EXISTS message (
    id SERIAL PRIMARY KEY,
    conversation_id INTEGER NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    intent VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建Agent调用日志表（用于观测评估）
CREATE TABLE IF NOT EXISTS agent_log (
    id SERIAL PRIMARY KEY,
    conversation_id INTEGER,
    user_message TEXT,
    intent VARCHAR(50),
    parameters TEXT,
    latency_ms BIGINT,
    success BOOLEAN DEFAULT true,
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 索引（2026-09-19 优化）
-- 设计原则：复合索引「等值列在前、排序列在后」，让过滤与排序共用一个索引；
--          单列索引若已被复合索引前缀覆盖则不再单独创建；
--          只用写入的列不建索引；模糊匹配用 pg_trgm GIN。
-- 详细依据与实测数据见 db/migration/2026-09-19-index-optimization.sql
-- ============================================================
-- 注：app_user.username 的唯一约束会自动建索引，无需重复创建

-- courses：课程列表（按用户）+ 对话绑定课程（按用户+课程名）
CREATE INDEX IF NOT EXISTS idx_courses_user_updated ON courses(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_courses_user_name ON courses(user_id, name);

-- review_records：复习记录 -> 用户列表 / 课程列表 / 学习进度 / 会话级联删除
CREATE INDEX IF NOT EXISTS idx_review_user_created ON review_records(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_course_created ON review_records(course_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_course_user_created ON review_records(course_id, user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_review_conversation ON review_records(conversation_id);

-- conversation：会话列表（按用户+最近活动）+ 过期清理（按更新时间）
CREATE INDEX IF NOT EXISTS idx_conversation_user_updated ON conversation(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversation_updated_at ON conversation(updated_at);

-- message：会话时间线（过滤+排序）+ 首条/截断定位（按会话+id）
CREATE INDEX IF NOT EXISTS idx_message_conv_created ON message(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_message_conv_id ON message(conversation_id, id);

-- agent_log：按会话查日志（过滤+倒序）+ 按时间范围统计
CREATE INDEX IF NOT EXISTS idx_agent_log_conv_created ON agent_log(conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_log_created_at ON agent_log(created_at);

-- 创建RAG检索日志表（用于观测RAG召回质量）
CREATE TABLE IF NOT EXISTS rag_search_log (
    id SERIAL PRIMARY KEY,
    conversation_id INTEGER,
    query TEXT NOT NULL,
    result_count INTEGER DEFAULT 0,
    top_k INTEGER DEFAULT 3,
    similarity_threshold DOUBLE PRECISION DEFAULT 0.5,
    retrieved_chunks TEXT,
    response_text TEXT,
    success BOOLEAN DEFAULT true,
    error_msg TEXT,
    latency_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- rag_search_log：按会话查日志（过滤+倒序）+ 按时间范围统计
CREATE INDEX IF NOT EXISTS idx_rag_search_conv_created ON rag_search_log(conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_search_created_at ON rag_search_log(created_at);

-- 创建错题本表
CREATE TABLE IF NOT EXISTS wrong_answer_book (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    course_id INTEGER,
    question TEXT NOT NULL,
    options TEXT,
    correct_answer TEXT NOT NULL,
    user_answer TEXT,
    explanation TEXT,
    topic VARCHAR(200),
    is_mastered BOOLEAN DEFAULT false,
    wrong_count INTEGER DEFAULT 1,
    last_wrong_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- 注：错题本不建 pg_trgm 索引——查询形态恒为「先按 user_id 过滤」，
--     trgm 永远只是第二道过滤，而错题本会频繁 UPDATE，GIN 写入放大不划算。
--     实测依据见 db/migration/2026-09-19-index-optimization.sql
CREATE INDEX IF NOT EXISTS idx_wrong_user_last_wrong ON wrong_answer_book(user_id, last_wrong_at DESC);
CREATE INDEX IF NOT EXISTS idx_wrong_user_mastered_last_wrong ON wrong_answer_book(user_id, is_mastered, last_wrong_at DESC);
CREATE INDEX IF NOT EXISTS idx_wrong_user_question ON wrong_answer_book(user_id, question);

-- 创建学习计划表（存储从对话生成的复习计划）
CREATE TABLE IF NOT EXISTS study_plan (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    available_days VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    -- 结构化进度 JSON：{"1": {"done": true, "review": {"done": true, "content": "..."}}}
    progress TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_study_plan_user_created ON study_plan(user_id, created_at DESC);

-- 创建知识库原始资料表（保存上传文件的完整解析文本，用于“查看完整源文件预览”）
CREATE TABLE IF NOT EXISTS knowledge_document (
    id SERIAL PRIMARY KEY,
    course_id INTEGER NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    chunk_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_course_id ON knowledge_document(course_id, id);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_course_name_id ON knowledge_document(course_id, file_name, id DESC);

-- 知识库向量分块（vector_store）的索引
--
-- ⚠️ 该表由 Spring AI 在应用首次启动时创建（spring.ai.vectorstore.pgvector.initialize-schema），
--    因此全新部署时执行本脚本它还不在，无法直接建索引。
--    这里用条件块处理：表存在就建索引，不存在则提示；
--    等应用启动一次后重跑本脚本（或单独执行 db/migration/ 下的迁移脚本）即可补上。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'vector_store') THEN
        -- 分块查询走 JSON 元数据，普通 B-tree 无法索引 JSON 内部字段，需表达式索引
        CREATE INDEX IF NOT EXISTS idx_vector_store_course_chunk
            ON vector_store (((metadata ->> 'courseId')), (((metadata ->> 'chunkIndex'))::int));
        -- RAG 关键词兜底检索：无其它过滤条件，且「无匹配」是其常态，靠 GIN 避免全表扫描
        CREATE INDEX IF NOT EXISTS idx_vector_store_content_trgm
            ON vector_store USING gin (content gin_trgm_ops);
        RAISE NOTICE '已创建 vector_store 索引';
    ELSE
        RAISE NOTICE 'vector_store 表尚不存在（由应用启动时创建），请在其后重跑本脚本以补建索引';
    END IF;
END $$;

-- 自动更新 updated_at 的触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_courses_updated_at ON courses;
CREATE TRIGGER update_courses_updated_at
    BEFORE UPDATE ON courses
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_app_user_updated_at ON app_user;
CREATE TRIGGER update_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_conversation_updated_at ON conversation;
CREATE TRIGGER update_conversation_updated_at
    BEFORE UPDATE ON conversation
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_wrong_answer_book_updated_at ON wrong_answer_book;
CREATE TRIGGER update_wrong_answer_book_updated_at
    BEFORE UPDATE ON wrong_answer_book
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_study_plan_updated_at ON study_plan;
CREATE TRIGGER update_study_plan_updated_at
    BEFORE UPDATE ON study_plan
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
