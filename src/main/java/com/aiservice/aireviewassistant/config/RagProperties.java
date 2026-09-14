package com.aiservice.aireviewassistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// RAG 检索配置
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    // 默认召回数量
    private int topK = 3;

    // 向量相似度阈值
    private double similarityThreshold = 0.5;

    // 是否启用关键词重排序
    private boolean rerankEnabled = true;

    // 重排序候选倍数：先召回 topK * multiplier 个，再重排序取 topK
    private int rerankCandidateMultiplier = 2;
}
