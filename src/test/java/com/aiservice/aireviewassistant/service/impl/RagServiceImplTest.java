package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.config.RagProperties;
import com.aiservice.aireviewassistant.metrics.AgentMetrics;
import com.aiservice.aireviewassistant.service.RagSearchLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// RagServiceImpl 单元测试：验证 RAG 问答、流式输出、重排序
@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private PromptTemplate promptTemplate;

    @Mock
    private RagSearchLogService ragSearchLogService;

    @Mock
    private RagProperties ragProperties;

    @Mock
    private AgentMetrics agentMetrics;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagServiceImpl(chatClient, vectorStore, promptTemplate, ragSearchLogService, ragProperties, agentMetrics, new ObjectMapper());
    }

    @Test
    void shouldAnswerDirectlyWhenNoDocumentsFound() {
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(ragProperties.isRerankEnabled()).thenReturn(false);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("直接回答");

        String result = ragService.answerQuestion("问题", 1);

        assertThat(result).isEqualTo("直接回答");
        verify(requestSpec).user("问题");
    }

    @Test
    void shouldUseContextWhenDocumentsFound() {
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(ragProperties.isRerankEnabled()).thenReturn(false);
        Document doc = new Document("相关知识", Map.of("source", "课本"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("基于知识库回答");

        String result = ragService.answerQuestion("问题", 1);

        assertThat(result).isEqualTo("基于知识库回答");
        verify(requestSpec).system("系统提示");
    }

    @Test
    void shouldStreamAnswer() {
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(ragProperties.isRerankEnabled()).thenReturn(false);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("流式", "回答"));

        Flux<String> result = ragService.answerQuestionStream("问题", 1);

        StepVerifier.create(result)
            .expectNext("流式")
            .expectNext("回答")
            .verifyComplete();
    }

    @Test
    void shouldRerankByKeywords() {
        when(ragProperties.getTopK()).thenReturn(1);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.0);
        when(ragProperties.isRerankEnabled()).thenReturn(true);
        when(ragProperties.getRerankCandidateMultiplier()).thenReturn(3);

        Document doc1 = new Document("这是一段关于索引的内容", Map.of());
        Document doc2 = new Document("这是一段关于事务的内容", Map.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("回答");

        ragService.answerQuestion("什么是事务", 1);

        verify(requestSpec).system("系统提示");
    }

    @Test
    void shouldRecordRagMetrics() {
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(ragProperties.isRerankEnabled()).thenReturn(false);
        Document doc = new Document("相关知识", Map.of("source", "课本"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("基于知识库回答");

        ragService.answerQuestion("问题", 1);

        verify(agentMetrics).recordRagSearch(anyLong());
        verify(agentMetrics).recordRagRetrieved(1);
    }
}
