package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.config.RagProperties;
import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.aiservice.aireviewassistant.metrics.AgentMetrics;
import com.aiservice.aireviewassistant.service.RagSearchLogService;
import com.aiservice.aireviewassistant.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptTemplate promptTemplate;
    private final RagSearchLogService ragSearchLogService;
    private final RagProperties ragProperties;
    private final AgentMetrics agentMetrics;
    private final ObjectMapper objectMapper;

    public RagServiceImpl(ChatClient chatClient, VectorStore vectorStore,
                          PromptTemplate promptTemplate, RagSearchLogService ragSearchLogService,
                          RagProperties ragProperties, AgentMetrics agentMetrics,
                          ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.promptTemplate = promptTemplate;
        this.ragSearchLogService = ragSearchLogService;
        this.ragProperties = ragProperties;
        this.agentMetrics = agentMetrics;
        this.objectMapper = objectMapper;
    }

    @Override
    @Cacheable(value = "agentResults", key = "'rag:' + #question + ':' + (#conversationId != null ? #conversationId : 0)")
    public String answerQuestion(String question, Integer conversationId) {
        long startTime = System.currentTimeMillis();
        SearchResult searchResult = searchDocuments(question);
        logSearch(question, conversationId, searchResult, null,
            System.currentTimeMillis() - startTime, true, null);

        if (searchResult.isEmpty()) {
            return chatClient.prompt()
                .user(question)
                .call()
                .content();
        }

        String systemPrompt = promptTemplate.render("rag-system.txt", Map.of("context", searchResult.context()));
        return chatClient.prompt()
            .system(systemPrompt)
            .user(question)
            .call()
            .content();
    }

    @Override
    public Flux<String> answerQuestionStream(String question, Integer conversationId) {
        SearchResult searchResult = searchDocuments(question);

        if (searchResult.isEmpty()) {
            // 无知识库命中，直接流式调用 LLM
            return chatClient.prompt()
                .user(question)
                .stream()
                .content();
        }

        // 有知识库命中，用 RAG 上下文流式生成
        String systemPrompt = promptTemplate.render("rag-system.txt", Map.of("context", searchResult.context()));
        return chatClient.prompt()
            .system(systemPrompt)
            .user(question)
            .stream()
            .content();
    }

    @Override
    public String retrieveContext(String query, Integer conversationId) {
        SearchResult searchResult = searchDocuments(query);
        return searchResult.context();
    }

    // 执行向量检索，支持配置化参数与关键词重排序
    private SearchResult searchDocuments(String question) {
        long searchStart = System.currentTimeMillis();
        List<Document> docs = null;
        try {
            int topK = ragProperties.getTopK();
            double threshold = ragProperties.getSimilarityThreshold();
            int candidateMultiplier = ragProperties.getRerankCandidateMultiplier();
            int searchTopK = ragProperties.isRerankEnabled() ? topK * candidateMultiplier : topK;

            docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(question)
                    .topK(searchTopK)
                    .similarityThreshold(threshold)
                    .build()
            );

            log.debug("========== RAG 检索日志 ==========");
            log.debug("用户问题: {}", question);
            log.debug("相似度阈值: {}", threshold);
            log.debug("初始召回文档数: {}", docs.size());

            // 启用重排序时，基于关键词匹配提升排序
            if (ragProperties.isRerankEnabled() && docs.size() > topK) {
                docs = rerankByKeywords(question, docs, topK);
                log.debug("重排序后文档数: {}", docs.size());
            }

            List<Map<String, Object>> chunks = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                log.debug("--- 文档 {} ---", i + 1);
                log.debug("内容: {}", doc.getText());
                log.debug("相似度分数: {}", doc.getScore());

                Map<String, Object> chunk = new LinkedHashMap<>();
                chunk.put("index", i + 1);
                chunk.put("score", doc.getScore());
                chunk.put("content", doc.getText());
                chunk.put("metadata", doc.getMetadata());
                chunks.add(chunk);

                contextBuilder.append(doc.getText());
                if (i < docs.size() - 1) {
                    contextBuilder.append("\n\n---\n\n");
                }
            }
            log.debug("================================");

            String chunksJson = objectMapper.writeValueAsString(chunks);
            return new SearchResult(docs.size(), chunksJson, contextBuilder.toString());
        } catch (Exception e) {
            log.error("[RAG] 向量检索失败: {}", e.getMessage(), e);
            return new SearchResult(0, "[]", "");
        } finally {
            long searchDuration = System.currentTimeMillis() - searchStart;
            agentMetrics.recordRagSearch(searchDuration);
            agentMetrics.recordRagRetrieved(docs != null ? docs.size() : 0);
        }
    }

    // 基于关键词匹配的重排序：向量分数 + 关键词命中加权
    private List<Document> rerankByKeywords(String question, List<Document> docs, int topK) {
        String[] keywords = question.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ")
            .toLowerCase()
            .split("\\s+");

        List<ScoredDocument> scored = new ArrayList<>();
        for (Document doc : docs) {
            String text = doc.getText().toLowerCase();
            double keywordScore = 0;
            for (String keyword : keywords) {
                if (keyword.length() > 1 && text.contains(keyword)) {
                    keywordScore += 0.05;
                }
            }
            double vectorScore = doc.getScore() != null ? doc.getScore() : 0;
            scored.add(new ScoredDocument(doc, vectorScore + keywordScore));
        }

        scored.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());
        return scored.stream()
            .limit(topK)
            .map(ScoredDocument::doc)
            .toList();
    }

    // 记录RAG检索日志
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

    // 检索结果内部对象
    private record SearchResult(int count, String chunksJson, String context) {
        boolean isEmpty() {
            return count == 0;
        }
    }

    // 带重排序分数的文档
    private record ScoredDocument(Document doc, double score) {
    }
}
