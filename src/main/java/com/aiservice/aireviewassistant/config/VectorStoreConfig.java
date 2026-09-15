package com.aiservice.aireviewassistant.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 向量存储配置类。
 * <p>使用 PostgreSQL + pgvector 扩展作为向量存储后端，配置 embedding 维度、距离类型、索引类型等。</p>
 */
@Configuration
public class VectorStoreConfig {

    /** 是否初始化 pgvector schema；测试环境可设置为 {@code false} 以避免重复建表。 */
    @Value("${spring.ai.vectorstore.pgvector.initialize-schema:true}")
    private boolean initializeSchema;

    /**
     * 注册 {@link VectorStore} Bean。
     * <p>使用主数据源创建 {@code PgVectorStore}，维度固定为 1536，使用余弦距离和 HNSW 索引；
     * 批量插入最大文档数设置为 1000。</p>
     *
     * @param dataSource     主数据源（PostgreSQL）
     * @param embeddingModel 嵌入模型
     * @return 配置完成的 PgVectorStore 实例
     */
    @Bean
    public VectorStore vectorStore(DataSource dataSource, EmbeddingModel embeddingModel) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(1536)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .initializeSchema(initializeSchema)
            .maxDocumentBatchSize(1000)
            .build();
    }
}
