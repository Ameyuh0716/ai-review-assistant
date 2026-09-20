package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.config.RagProperties;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.aiservice.aireviewassistant.metrics.AgentMetrics;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.RagSearchLogService;
import com.aiservice.aireviewassistant.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG（检索增强生成）服务实现类。
 * <p>
 * 核心职责：
 * 1. 将用户问题通过向量库进行相似度检索，召回课程知识库中的相关片段；
 * 2. 支持配置化的重排序策略，基于关键词命中加权优化 TopK 结果；
 * 3. 将检索上下文注入系统提示词，调用大模型完成同步或流式回答；
 * 4. 记录检索日志与 Prometheus 指标，便于后续审计与性能监控。
 * </p>
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {

    /**
     * 关键词兜底检索时，关键词需覆盖查询主体的最小百分比。
     * <p>低于该比例的词被视为“问句中的普通成分”（如“定义”“结构”），
     * 不作为匹配依据，避免产生与回答相矛盾的假命中。</p>
     */
    private static final int DOMINANT_KEYWORD_PERCENT = 60;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptTemplate promptTemplate;
    private final RagSearchLogService ragSearchLogService;
    private final RagProperties ragProperties;
    private final AgentMetrics agentMetrics;
    private final ObjectMapper objectMapper;

    /** 课程服务，用于解析检索范围（用户 → 其名下课程）。 */
    private final CoursesService coursesService;

    /** JDBC 模板，用于向量检索零命中时按关键词直接检索知识库内容。 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造方法：注入 RAG 所需的大模型客户端、向量库、提示词模板、检索日志服务等依赖。
     *
     * @param chatClient          Spring AI 大模型聊天客户端
     * @param vectorStore         向量存储（PgVectorStore），用于相似度检索
     * @param promptTemplate      提示词模板渲染器
     * @param ragSearchLogService RAG 检索日志服务，用于持久化检索与调用日志
     * @param ragProperties       RAG 配置属性（TopK、阈值、重排序开关等）
     * @param agentMetrics        Agent 调用指标收集器
     * @param objectMapper        JSON 序列化工具，用于记录检索片段
     * @param jdbcTemplate        JDBC 模板，用于关键词兜底检索
     * @param coursesService      课程服务，用于解析检索范围
     */
    public RagServiceImpl(ChatClient chatClient, VectorStore vectorStore,
                          PromptTemplate promptTemplate, RagSearchLogService ragSearchLogService,
                          RagProperties ragProperties, AgentMetrics agentMetrics,
                          ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                          CoursesService coursesService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.promptTemplate = promptTemplate;
        this.ragSearchLogService = ragSearchLogService;
        this.ragProperties = ragProperties;
        this.agentMetrics = agentMetrics;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.coursesService = coursesService;
    }

    /**
     * 同步 RAG 问答：先检索知识库，再调用大模型生成一次性答案。
     * <p>
     * 使用 Spring Cache 对结果进行缓存，缓存键由问题与会话 ID 组成；
     * 未命中知识库时直接调用大模型，避免空上下文污染答案。
     * </p>
     *
     * @param question       用户问题
     * @param conversationId 当前会话 ID
     * @param scope          检索范围（限定只能命中该用户自己的课程资料）
     * @return 大模型生成的回答文本
     */
    @Override
    public String answerQuestion(String question, Integer conversationId, RagScope scope) {
        long startTime = System.currentTimeMillis();
        SearchResult searchResult = searchDocuments(question, scope);
        logSearch(question, conversationId, searchResult, null,
            System.currentTimeMillis() - startTime, true, null);

        // 未命中知识库：直接调用大模型，不注入 RAG 上下文
        if (searchResult.isEmpty()) {
            return chatClient.prompt()
                .user(question)
                .call()
                .content();
        }

        // 命中知识库：渲染 RAG 系统提示词，将上下文注入 system 角色
        String systemPrompt = promptTemplate.render("rag-system.txt", Map.of("context", searchResult.context()));
        return chatClient.prompt()
            .system(systemPrompt)
            .user(question)
            .call()
            .content();
    }

    /**
     * 流式 RAG 问答：先检索知识库，再以 SSE 方式流式返回答案。
     *
     * @param question       用户问题
     * @param conversationId 当前会话 ID
     * @param scope          检索范围（限定只能命中该用户自己的课程资料）
     * @return 按 Token 流式推送的回答字符串流
     */
    @Override
    public Flux<String> answerQuestionStream(String question, Integer conversationId, RagScope scope) {
        return answerQuestionStreamWithMeta(question, conversationId, scope).content();
    }

    /**
     * 流式 RAG 问答（带检索元数据）。
     * <p>在返回答案流的同时回传本次检索的命中数量、TopK、相似度、耗时等信息，
     * 供前端在对话中展示 RAG 执行情况；同时将本次检索写入 rag_search_log。</p>
     *
     * @param question       用户问题
     * @param conversationId 当前会话 ID
     * @param scope          检索范围（限定只能命中该用户自己的课程资料）
     * @return 检索元数据 + 答案流
     */
    @Override
    public RagAnswer answerQuestionStreamWithMeta(String question, Integer conversationId, RagScope scope) {
        long startTime = System.currentTimeMillis();
        SearchResult searchResult = searchDocuments(question, scope);
        logSearch(question, conversationId, searchResult, null,
            System.currentTimeMillis() - startTime, true, null);

        Flux<String> content;
        if (searchResult.isEmpty()) {
            // 无知识库命中，直接流式调用 LLM，减少不必要的提示词长度
            content = chatClient.prompt()
                .user(question)
                .stream()
                .content();
        } else {
            // 有知识库命中，用 RAG 上下文流式生成，兼顾实时性与事实性
            String systemPrompt = promptTemplate.render("rag-system.txt", Map.of("context", searchResult.context()));
            content = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .stream()
                .content();
        }
        return new RagAnswer(searchResult.meta(), content);
    }

    /**
     * 检索与查询相关的知识库上下文，返回拼接后的文本块。
     *
     * @param query          查询文本
     * @param conversationId 当前会话 ID
     * @param scope          检索范围（限定只能命中该用户自己的课程资料）
     * @return 拼接后的知识库上下文；无命中时返回空字符串
     */
    @Override
    public String retrieveContext(String query, Integer conversationId, RagScope scope) {
        return retrieveContextWithMeta(query, conversationId, scope).context();
    }

    /**
     * 检索知识库上下文（带检索元数据）。
     * <p>供总结/解释工具使用：除上下文外回传检索统计，并写入 rag_search_log。</p>
     *
     * @param query          查询文本
     * @param conversationId 当前会话 ID
     * @param scope          检索范围（限定只能命中该用户自己的课程资料）
     * @return 检索元数据 + 拼接上下文
     */
    @Override
    public RagContext retrieveContextWithMeta(String query, Integer conversationId, RagScope scope) {
        long startTime = System.currentTimeMillis();
        SearchResult searchResult = searchDocuments(query, scope);
        logSearch(query, conversationId, searchResult, null,
            System.currentTimeMillis() - startTime, true, null);
        return new RagContext(searchResult.meta(), searchResult.context());
    }

    /**
     * 解析检索范围：把「当前用户 + 当前选中课程」换算为允许检索的课程 ID 列表。
     * <p>
     * 规则：
     * <ol>
     *   <li>指定了课程 → 仅该课程；同时校验课程归属，防止前端伪造 courseId 越权读取他人资料；</li>
     *   <li>未指定课程但已登录 → 该用户名下全部课程；</li>
     *   <li>匿名 → 空列表（无任何可见资源）。
     * </ol>
     * </p>
     *
     * @param scope 检索范围描述
     * @return 允许检索的课程 ID 列表；空列表表示不应检索
     */
    private List<Integer> resolveCourseIds(RagScope scope) {
        if (scope == null) {
            return List.of();
        }
        if (scope.courseId() != null) {
            // 指定了课程：校验归属（匿名用户不允许按课程检索）
            if (scope.userId() == null) {
                log.debug("[RAG] 匿名用户指定了 courseId={}，不予检索", scope.courseId());
                return List.of();
            }
            try {
                Courses course = coursesService.getById(scope.courseId());
                if (course == null || !scope.userId().equals(course.getUserId())) {
                    log.warn("[RAG] 课程 {} 不属于用户 {}，不予检索", scope.courseId(), scope.userId());
                    return List.of();
                }
                return List.of(scope.courseId());
            } catch (Exception e) {
                log.warn("[RAG] 校验课程归属失败: {}", e.getMessage());
                return List.of();
            }
        }
        if (scope.userId() == null) {
            return List.of();
        }
        // 未指定课程：允许检索该用户的全部课程
        try {
            return coursesService.lambdaQuery()
                .eq(Courses::getUserId, scope.userId())
                .list()
                .stream()
                .map(Courses::getId)
                .toList();
        } catch (Exception e) {
            log.warn("[RAG] 查询用户课程失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 执行向量相似度检索，并将命中文档拼接为上下文。
     * <p>
     * 流程：
     * 1. 根据配置决定初始召回数量（若启用重排序则扩大候选集）；
     * 2. 调用向量库 similaritySearch 召回文档；
     * 3. 如启用重排序，基于关键词匹配对候选文档二次排序并截取 TopK；
     * 4. 将文档内容拼接为上下文，并将片段元数据序列化为 JSON 用于日志。
     * 检索耗时与召回数量会被记录到 Prometheus 指标。
     * </p>
     *
     * @param question 用户问题或查询文本
     * @param scope    检索范围（限定可见课程）
     * @return 检索结果封装，包括命中数量、JSON 片段、拼接上下文
     */
    private SearchResult searchDocuments(String question, RagScope scope) {
        long searchStart = System.currentTimeMillis();
        List<Document> docs = null;
        int topK = ragProperties.getTopK();
        double threshold = ragProperties.getSimilarityThreshold();
        // 记录向量检索初始召回数（重排序前），用于前端展示检索漏斗
        int candidateCount = 0;
        boolean keywordFallback = false;

        // 解析可见课程范围：为空则直接返回空结果，连向量库都不查
        List<Integer> scopedCourseIds = resolveCourseIds(scope);
        if (scopedCourseIds.isEmpty()) {
            log.debug("[RAG] 检索范围为空（anonymous 或用户无课程），跳过知识库检索");
            RagMeta meta = new RagMeta(0, 0, topK, threshold,
                System.currentTimeMillis() - searchStart, false, null);
            return new SearchResult(0, "[]", "", meta);
        }
        // 检索过滤条件：限定只能命中这些课程的分块（PgVectorStore 会转成 JSONPath 过滤）
        String filterExpression = buildCourseFilter(scopedCourseIds);

        try {
            // 若启用重排序，先召回 candidateMultiplier 倍文档作为候选集
            int candidateMultiplier = ragProperties.getRerankCandidateMultiplier();
            int searchTopK = ragProperties.isRerankEnabled() ? topK * candidateMultiplier : topK;

            // 调用向量库执行相似度检索：query 向量化 + 向量空间最近邻搜索
            docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(question)
                    .topK(searchTopK)
                    .similarityThreshold(threshold)
                    .filterExpression(filterExpression)
                    .build()
            );
            candidateCount = docs.size();

            // 同一份资料可能被重复导入到多个课程（或重复上传），导致相同内容占据多个 TopK 名额；
            // 按内容去重（保留相似度最高的一条），把有限名额让给真正不同的片段。
            docs = dedupeByContent(docs);

            // 打印检索调试信息，便于排查召回与阈值问题
            log.debug("========== RAG 检索日志 ==========");
            log.debug("用户问题: {}", question);
            log.debug("相似度阈值: {}", threshold);
            log.debug("初始召回文档数: {}", docs.size());

            // 启用重排序且候选集大于最终 TopK 时，基于关键词命中进行二次排序
            if (ragProperties.isRerankEnabled() && docs.size() > topK) {
                docs = rerankByKeywords(question, docs, topK);
                log.debug("重排序后文档数: {}", docs.size());
            }

            // 向量检索零命中时的关键词兜底：
            // 用户只给出宽泛学科名（如"操作系统"）时，查询向量与内容向量的余弦相似度
            // 可能低于阈值，但知识库中确实存在该学科资料。此处按关键词直接匹配内容，
            // 避免下游工具拿到空上下文后误报"知识库中暂无相关内容"。
            if (docs.isEmpty()) {
                docs = keywordFallbackSearch(question, topK, scopedCourseIds);
                keywordFallback = !docs.isEmpty();
                if (keywordFallback) {
                    log.debug("向量检索零命中，关键词兜底召回 {} 篇", docs.size());
                }
            }

            // 构建供日志记录的 JSON 片段、供模型使用的拼接上下文，并统计最高相似度
            List<Map<String, Object>> chunks = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();
            Double topScore = null;
            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                log.debug("--- 文档 {} ---", i + 1);
                log.debug("内容: {}", doc.getText());
                log.debug("相似度分数: {}", doc.getScore());

                if (doc.getScore() != null && (topScore == null || doc.getScore() > topScore)) {
                    topScore = doc.getScore();
                }

                Map<String, Object> chunk = new LinkedHashMap<>();
                chunk.put("index", i + 1);
                chunk.put("score", doc.getScore());
                chunk.put("content", doc.getText());
                chunk.put("metadata", doc.getMetadata());
                chunks.add(chunk);

                // 多个文档之间用分隔线拼接，便于模型区分不同来源
                contextBuilder.append(doc.getText());
                if (i < docs.size() - 1) {
                    contextBuilder.append("\n\n---\n\n");
                }
            }
            log.debug("================================");

            // 将片段元数据序列化为 JSON 字符串，用于后续检索日志落库
            String chunksJson = objectMapper.writeValueAsString(chunks);
            RagMeta meta = new RagMeta(docs.size(), candidateCount, topK, threshold,
                System.currentTimeMillis() - searchStart, keywordFallback, topScore);
            return new SearchResult(docs.size(), chunksJson, contextBuilder.toString(), meta);
        } catch (Exception e) {
            // 向量检索异常不阻断主流程，返回空结果，由上层决定是否直接调用大模型
            log.error("[RAG] 向量检索失败: {}", e.getMessage(), e);
            RagMeta meta = new RagMeta(0, candidateCount, topK, threshold,
                System.currentTimeMillis() - searchStart, false, null);
            return new SearchResult(0, "[]", "", meta);
        } finally {
            // 无论成功失败都记录检索耗时与召回数量，保证指标完整性
            long searchDuration = System.currentTimeMillis() - searchStart;
            agentMetrics.recordRagSearch(searchDuration);
            agentMetrics.recordRagRetrieved(docs != null ? docs.size() : 0);
        }
    }

    /**
     * 基于关键词命中的重排序：在向量分数基础上增加关键词命中加权。
     * <p>
     * 实现要点：
     * - 从问题中提取中英文/数字关键词（过滤标点）；
     * - 每个长度大于 1 的关键词在文档文本中命中一次加 0.05 分；
     * - 按综合分数降序排列后截取前 topK 篇文档。
     * </p>
     *
     * @param question 用户问题
     * @param docs     向量检索召回的候选文档
     * @param topK     重排序后需要保留的文档数量
     * @return 重排序并截断后的文档列表
     */
    private List<Document> rerankByKeywords(String question, List<Document> docs, int topK) {
        // 清理标点，仅保留中英文与数字，并按空白切分为关键词列表
        String[] keywords = question.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ")
            .toLowerCase()
            .split("\\s+");

        // 计算每篇文档的综合得分：向量相似度分数 + 关键词命中加权
        List<ScoredDocument> scored = new ArrayList<>();
        for (Document doc : docs) {
            String text = doc.getText().toLowerCase();
            double keywordScore = 0;
            for (String keyword : keywords) {
                // 仅对长度大于 1 的关键词计分，避免单字噪声
                if (keyword.length() > 1 && text.contains(keyword)) {
                    keywordScore += 0.05;
                }
            }
            double vectorScore = doc.getScore() != null ? doc.getScore() : 0;
            scored.add(new ScoredDocument(doc, vectorScore + keywordScore));
        }

        // 按综合得分降序排列并截取前 topK
        scored.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());
        return scored.stream()
            .limit(topK)
            .map(ScoredDocument::doc)
            .toList();
    }

    /**
     * 按内容对召回的文档去重，保留相似度最高的一条。
     * <p>
     * 背景：同一份资料常被导入到多个课程，或用户重复上传同一文档，
     * 此时向量库里会存在多份内容完全相同的分块。它们会同时被召回并占据多个 TopK 名额，
     * 使前端徒劳地显示“命中 3/3”，却只提供了同一个片段（甚至挤掉真正相关的其它片段）。
     * 此处仅按正文去重，保留分数最高的那条（连同其元数据），保持原有排序。
     * </p>
     *
     * @param docs 向量检索召回（已可能重排序）的文档列表
     * @return 内容唯一的文档列表，保持原相对顺序
     */
    private List<Document> dedupeByContent(List<Document> docs) {
        if (docs == null || docs.size() <= 1) {
            return docs;
        }
        Map<String, Document> best = new LinkedHashMap<>();
        for (Document doc : docs) {
            String key = normalizeContent(doc.getText());
            Document exist = best.get(key);
            if (exist == null || scoreOf(doc) > scoreOf(exist)) {
                best.put(key, doc);
            }
        }
        return new ArrayList<>(best.values());
    }

    /**
     * 归一化正文作为去重键：去掉所有空白并转小写，
     * 使仅缩进/换行差异的同一段落也能被识别为重复。
     *
     * @param text 文档正文
     * @return 归一化后的去重键
     */
    private String normalizeContent(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 安全获取文档相似度分数，为空时视为 0。
     *
     * @param doc 文档
     * @return 分数值
     */
    private double scoreOf(Document doc) {
        return doc.getScore() != null ? doc.getScore() : 0d;
    }

    /**
     * 关键词兜底检索：向量检索零命中时，按关键词直接匹配知识库内容。
     * <p>
     * 设计意图：仅服务于“用户只给出宽泛学科名”的场景（如只输入“操作系统”），
     * 此时查询向量可能因缺乏上下文而低于阈值，但知识库中确实存在该学科资料。
     * </p>
     * <p>
     * 为避免假命中（前端显示“命中”、模型却回答“无相关内容”的矛盾），匹配分两步且都很保守：
     * <ol>
     *   <li>先用<b>整句</b>（清洗后）匹配，精确覆盖“只输入学科名”的主要场景；</li>
     *   <li>再用<b>占据查询主体</b>（长度不低于整句的 60%）的关键词匹配，支持“操作系统 进程同步”这类多词查询。</li>
     * </ol>
     * 过短的通用词（如“链表定义”里的“定义”）不会触发匹配，
     * 从而避免完整问句中某个词偶然出现在资料里就产生命中。
     * </p>
     *
     * @param question        用户查询
     * @param limit           最多返回的片段数
     * @param scopedCourseIds 允许检索的课程 ID 列表（非空）
     * @return 匹配到的文档列表；无匹配时返回空列表
     */
    private List<Document> keywordFallbackSearch(String question, int limit, List<Integer> scopedCourseIds) {
        // 候选数量放宽：同一资料常被导入多个课程，去重会消耗名额，先多取一些再截断
        int fetch = Math.max(limit, limit * ragProperties.getRerankCandidateMultiplier());
        String cleaned = cleanQuery(question);
        // 第一步：整句匹配
        if (!cleaned.isEmpty()) {
            List<Document> whole = dedupeAndLimit(matchContent(cleaned, fetch, scopedCourseIds), limit);
            if (!whole.isEmpty()) {
                return whole;
            }
        }
        // 第二步：主体关键词匹配（关键词需覆盖整句的至少 60% 长度）
        for (String keyword : extractKeywords(question)) {
            if (cleaned.length() > 0 && keyword.length() * 100 < cleaned.length() * DOMINANT_KEYWORD_PERCENT) {
                continue;
            }
            List<Document> matched = dedupeAndLimit(matchContent(keyword, fetch, scopedCourseIds), limit);
            if (!matched.isEmpty()) {
                return matched;
            }
        }
        return List.of();
    }

    /**
     * 去重后截断到指定数量。
     * <p>兜底检索同样命中同一资料在多个课程下的副本，不去重会白白占满 TopK 名额。</p>
     *
     * @param docs  匹配结果
     * @param limit 最多保留的数量
     * @return 去重且截断后的结果
     */
    private List<Document> dedupeAndLimit(List<Document> docs, int limit) {
        List<Document> distinct = dedupeByContent(docs);
        return distinct.size() > limit ? new ArrayList<>(distinct.subList(0, limit)) : distinct;
    }

    /**
     * 按关键词做内容模糊匹配（限定在可见课程范围内）。
     * <p>
     * 必须带上课程过滤：这是裸 SQL 查询，若不过滤会直接绕过 RAG 的多用户隔离，
     * 让用户“兜底命中”别人的资料。
     * </p>
     *
     * @param keyword         匹配词
     * @param limit           最多返回的片段数
     * @param scopedCourseIds 允许检索的课程 ID 列表（非空）
     * @return 命中的文档列表
     */
    private List<Document> matchContent(String keyword, int limit, List<Integer> scopedCourseIds) {
        String placeholders = scopedCourseIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, content FROM vector_store "
            + "WHERE content ILIKE ? AND (metadata::jsonb ->> 'courseId')::int IN (" + placeholders + ") "
            + "LIMIT ?";
        List<Object> args = new ArrayList<>();
        args.add("%" + keyword + "%");
        args.addAll(scopedCourseIds);
        args.add(limit);
        return jdbcTemplate.query(sql,
            (rs, rowNum) -> new Document(rs.getString("id"), rs.getString("content"), Map.of()),
            args.toArray());
    }

    /**
     * 清洗查询文本：去掉标点、常见疑问词与空白，得到用于匹配的主体文本。
     * 与 {@link #extractKeywords(String)} 使用同一套规则，保证口径一致。
     *
     * @param question 用户查询
     * @return 清洗后的查询主体
     */
    private String cleanQuery(String question) {
        if (question == null) {
            return "";
        }
        return question
            .replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "")
            .replaceAll("什么是|请问|怎么|如何|为什么|解释一下|介绍一下|的|了|吗|呢", "");
    }

    /**
     * 构造「限定可见课程」的向量检索过滤表达式。
     * <p>
     * PgVectorStore 会把它转换为 JSONPath 并附加到 SQL 上（{@code metadata::jsonb @@ '...'}）；
     * metadata 中的 courseId 存的是 JSON 数字，因此这里必须用数字字面量，
     * 写成字符串会导致匹配失败。
     * </p>
     *
     * @param courseIds 允许检索的课程 ID 列表（非空）
     * @return 形如 {@code courseId in [1, 2, 3]} 的过滤表达式；仅一个课程时用等值形式
     */
    private String buildCourseFilter(List<Integer> courseIds) {
        if (courseIds.size() == 1) {
            return "courseId == " + courseIds.get(0);
        }
        return "courseId in [" + courseIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    /**
     * 从查询文本中提取候选关键词，按长度降序排列。
     * <p>
     * 先剔除标点与常见疑问词，再按空白切分；仅保留长度大于 1 的词（过滤单字噪声）。
     * </p>
     *
     * @param question 用户查询
     * @return 候选关键词列表
     */
    private List<String> extractKeywords(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String cleaned = question
            .replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ")
            .replaceAll("什么是|请问|怎么|如何|为什么|解释一下|介绍一下|的|了|吗|呢", " ")
            .toLowerCase();
        List<String> keywords = new ArrayList<>();
        for (String part : cleaned.split("\\s+")) {
            if (part.length() > 1) {
                keywords.add(part);
            }
        }
        keywords.sort(Comparator.comparingInt(String::length).reversed());
        return keywords;
    }

    /**
     * 记录 RAG 检索日志到数据库，用于后续审计、统计与召回质量分析。
     * <p>
     * 日志内容包含查询文本、命中数量、TopK、相似度阈值、召回片段 JSON、
     * 响应文本、总耗时、是否成功及异常信息。保存失败仅记录警告，不影响主流程。
     * </p>
     *
     * @param question        用户查询
     * @param conversationId  会话 ID
     * @param searchResult    向量检索结果
     * @param responseText    大模型生成的响应文本
     * @param totalLatencyMs  本次问答总耗时（毫秒）
     * @param success         是否成功
     * @param errorMsg        错误信息；成功时为 null
     */
    private void logSearch(String question, Integer conversationId, SearchResult searchResult,
                           String responseText, long totalLatencyMs,
                           boolean success, String errorMsg) {
        try {
            RagSearchLog log = new RagSearchLog();
            log.setConversationId(conversationId);
            log.setQuery(question);
            log.setResultCount(searchResult.count());
            log.setTopK(ragProperties.getTopK());
            log.setSimilarityThreshold(ragProperties.getSimilarityThreshold());
            log.setRetrievedChunks(searchResult.chunksJson());
            log.setResponseText(responseText);
            log.setLatencyMs(totalLatencyMs);
            log.setSuccess(success);
            log.setErrorMsg(errorMsg);
            log.setCreatedAt(LocalDateTime.now());
            ragSearchLogService.save(log);
        } catch (Exception e) {
            log.warn("[RAG] 保存检索日志失败: {}", e.getMessage());
        }
    }

    /**
     * 向量检索结果内部封装对象。
     *
     * @param count      命中文档数量
     * @param chunksJson 检索片段 JSON 字符串，用于日志落库
     * @param context    拼接后的上下文文本，用于注入系统提示词
     * @param meta       检索元数据，用于前端展示 RAG 执行情况
     */
    private record SearchResult(int count, String chunksJson, String context, RagMeta meta) {
        boolean isEmpty() {
            return count == 0;
        }
    }

    /**
     * 带重排序综合得分的文档包装对象。
     *
     * @param doc   Spring AI Document
     * @param score 综合得分（向量分数 + 关键词加权）
     */
    private record ScoredDocument(Document doc, double score) {
    }
}
