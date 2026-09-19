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

    /** 向量相似度阈值，默认 0.55；低于该阈值的文档将被过滤。
     *  <p>取值依据（实测本库分数呈双峰分布）：真实命中的片段相似度约 0.58～0.85，
     *  而同一学科内但不针对该知识点的“擦边”片段约 0.50～0.53。
     *  取 0.55 可挡掉后者并保留前者。</p> */
    private double similarityThreshold = 0.55;

    /** 是否启用关键词重排序，默认 {@code true}。 */
    private boolean rerankEnabled = true;

    /** 重排序候选倍数，默认 3；先召回 {@code topK * multiplier} 个候选，再重排序取 topK。
     *  <p>取 3 而非 2：同一资料常被导入多个课程，按内容去重后会消耗候选名额，
     *  倍数过小会导致最终只剩下 1 个不同片段。</p> */
    private int rerankCandidateMultiplier = 3;
}
