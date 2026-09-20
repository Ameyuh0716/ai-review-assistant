package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.config.RagProperties;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.metrics.AgentMetrics;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.RagSearchLogService;
import com.aiservice.aireviewassistant.service.RagService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagServiceImpl 单元测试。
 * <p>覆盖 RAG 问答的核心路径：无文档时的直接回答、命中文档时的上下文增强、
 * 流式输出、关键词重排序以及搜索指标记录。</p>
 */
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

    @Mock
    private JdbcTemplate jdbcTemplate;

    /** 课程服务 mock：用于把「用户」换算为「可见课程」，是知识库隔离的前提。 */
    @Mock
    private CoursesService coursesService;

    private RagServiceImpl ragService;

    /** 测试用检索范围：用户 1 + 课程 100（归属校验会通过）。 */
    private RagService.RagScope scope;

    /**
     * 每个测试方法执行前初始化被测服务。
     * <p>使用 Mockito 模拟全部依赖，避免真实调用向量库与 LLM。</p>
     */
    @BeforeEach
    void setUp() {
        // 构造 RagServiceImpl 实例，注入所有 mock 依赖
        ragService = new RagServiceImpl(chatClient, vectorStore, promptTemplate, ragSearchLogService,
                ragProperties, agentMetrics, new ObjectMapper(), jdbcTemplate, coursesService);
        scope = RagService.RagScope.of(1, 100);
        // 默认：课程 100 属于用户 1，检索范围解析通过
        Courses course = new Courses();
        course.setId(100);
        course.setUserId(1);
        lenient().when(coursesService.getById(100)).thenReturn(course);
    }

    /**
     * 测试场景：向量库未检索到任何相关文档。
     * <p>准备条件：配置 topK=3、相似度阈值 0.7、关闭重排序；向量库返回空列表；LLM 固定返回 "直接回答"。</p>
     * <p>断言意图：服务直接以原问题调用 LLM，并返回 LLM 生成的回答。</p>
     */
    @Test
    void shouldAnswerDirectlyWhenNoDocumentsFound() {
        // 配置 RAG 基本参数
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(ragProperties.isRerankEnabled()).thenReturn(false);
        // 模拟向量库未检索到文档
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        // 模拟 ChatClient 调用链
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("直接回答");

        String result = ragService.answerQuestion("问题", 1, scope);

        // 验证返回 LLM 的直接回答
        assertThat(result).isEqualTo("直接回答");
        // 验证使用原始问题调用 requestSpec.user
        verify(requestSpec).user("问题");
    }

    /**
     * 测试场景：向量库检索到相关文档。
     * <p>准备条件：返回带有元数据的文档；提示词模板渲染为系统提示；LLM 返回基于上下文的答案。</p>
     * <p>断言意图：服务使用渲染后的系统提示调用 LLM，并返回基于知识库的回答。</p>
     */
    @Test
    void shouldUseContextWhenDocumentsFound() {
        // 配置 RAG 基本参数
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(ragProperties.isRerankEnabled()).thenReturn(false);
        // 构造一个包含元数据的知识库文档
        Document doc = new Document("相关知识", Map.of("source", "课本"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        // 模拟提示词模板渲染结果
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        // 模拟 ChatClient 调用链
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("基于知识库回答");

        String result = ragService.answerQuestion("问题", 1, scope);

        // 验证返回基于知识库的回答
        assertThat(result).isEqualTo("基于知识库回答");
        // 验证使用渲染后的系统提示
        verify(requestSpec).system("系统提示");
    }

    /**
     * 测试场景：流式问答接口正常返回多个数据块。
     * <p>准备条件：向量库返回空列表；LLM 流式接口按顺序输出 "流式"、"回答"。</p>
     * <p>断言意图：返回的 Flux 按预期顺序推送数据并正常完成。</p>
     */
    @Test
    void shouldStreamAnswer() {
        // 配置 RAG 基本参数
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(ragProperties.isRerankEnabled()).thenReturn(false);
        // 模拟未检索到文档
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        // 模拟 ChatClient 流式调用链
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("流式", "回答"));

        Flux<String> result = ragService.answerQuestionStream("问题", 1, scope);

        // 验证流式输出顺序与内容
        StepVerifier.create(result)
            .expectNext("流式")
            .expectNext("回答")
            .verifyComplete();
    }

    /**
     * 测试场景：启用重排序后，根据关键词从候选文档中选择最相关的片段。
     * <p>准备条件：topK=1，相似度阈值 0.0，开启重排序，候选倍数为 3；
     * 向量库返回两篇文档，分别关于索引与事务；问题为 "什么是事务"。</p>
     * <p>断言意图：服务成功构造系统提示并调用 LLM，重排序路径被覆盖。</p>
     */
    @Test
    void shouldRerankByKeywords() {
        // 配置重排序相关参数
        when(ragProperties.getTopK()).thenReturn(1);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.0);
        when(ragProperties.isRerankEnabled()).thenReturn(true);
        when(ragProperties.getRerankCandidateMultiplier()).thenReturn(3);

        // 构造两篇不同主题的候选文档
        Document doc1 = new Document("这是一段关于索引的内容", Map.of());
        Document doc2 = new Document("这是一段关于事务的内容", Map.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));
        // 模拟系统提示模板渲染
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        // 模拟 ChatClient 调用链
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("回答");

        ragService.answerQuestion("什么是事务", 1, scope);

        // 验证使用渲染后的系统提示调用 LLM
        verify(requestSpec).system("系统提示");
    }

    /**
     * 测试场景：RAG 搜索完成后记录相关指标。
     * <p>准备条件：向量库返回一篇文档，其余依赖正常桩化。</p>
     * <p>断言意图：验证 {@link AgentMetrics#recordRagSearch(long)} 与
     * {@link AgentMetrics#recordRagRetrieved(int)} 被正确调用。</p>
     */
    @Test
    void shouldRecordRagMetrics() {
        // 配置 RAG 基本参数
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(ragProperties.isRerankEnabled()).thenReturn(false);
        // 构造一篇知识库文档
        Document doc = new Document("相关知识", Map.of("source", "课本"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        // 模拟系统提示模板渲染
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        // 模拟 ChatClient 调用链
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("基于知识库回答");

        ragService.answerQuestion("问题", 1, scope);

        // 验证记录了 RAG 搜索耗时
        verify(agentMetrics).recordRagSearch(anyLong());
        // 验证记录了检索到的文档数量（1 篇）
        verify(agentMetrics).recordRagRetrieved(1);
    }
}
