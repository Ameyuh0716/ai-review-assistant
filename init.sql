-- 启用必要扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

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

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_courses_name ON courses(name);
CREATE INDEX IF NOT EXISTS idx_courses_user ON courses(user_id);
CREATE INDEX IF NOT EXISTS idx_review_course ON review_records(course_id);
CREATE INDEX IF NOT EXISTS idx_review_user ON review_records(user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_user ON conversation(user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_course ON conversation(course_id);
CREATE INDEX IF NOT EXISTS idx_message_conversation ON message(conversation_id);
CREATE INDEX IF NOT EXISTS idx_agent_log_conversation ON agent_log(conversation_id);
CREATE INDEX IF NOT EXISTS idx_agent_log_intent ON agent_log(intent);
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

CREATE INDEX IF NOT EXISTS idx_rag_search_conversation ON rag_search_log(conversation_id);
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

CREATE INDEX IF NOT EXISTS idx_wrong_book_user ON wrong_answer_book(user_id);
CREATE INDEX IF NOT EXISTS idx_wrong_book_course ON wrong_answer_book(course_id);
CREATE INDEX IF NOT EXISTS idx_wrong_book_mastered ON wrong_answer_book(is_mastered);

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

CREATE INDEX IF NOT EXISTS idx_study_plan_user ON study_plan(user_id);
CREATE INDEX IF NOT EXISTS idx_study_plan_created_at ON study_plan(created_at);

-- 创建知识库原始资料表（保存上传文件的完整解析文本，用于“查看完整源文件预览”）
CREATE TABLE IF NOT EXISTS knowledge_document (
    id SERIAL PRIMARY KEY,
    course_id INTEGER NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    chunk_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_course ON knowledge_document(course_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_course_name ON knowledge_document(course_id, file_name);

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
