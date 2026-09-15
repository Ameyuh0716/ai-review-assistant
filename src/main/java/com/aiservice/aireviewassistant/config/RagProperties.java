package com.aiservice.aireviewassistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG（检索增强生成）配置属性类。
 * <p>绑定前缀为 {@code app.rag} 的配置项，控制向量召回数量、相似度阈值以及重排序策略。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /** 默认召回数量，默认 3。 */
    private int topK = 3;

    /** 向量相似度阈值，默认 0.5；低于该阈值的文档将被过滤。 */
    private double similarityThreshold = 0.5;

    /** 是否启用关键词重排序，默认 {@code true}。 */
    private boolean rerankEnabled = true;

    /** 重排序候选倍数，默认 2；先召回 {@code topK * multiplier} 个候选，再重排序取 topK。 */
    private int rerankCandidateMultiplier = 2;
}
