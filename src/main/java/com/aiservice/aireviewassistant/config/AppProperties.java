package com.aiservice.aireviewassistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务应用配置属性类。
 * <p>绑定前缀为 {@code app} 的配置项，集中管理应用级运行参数。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 会话相关配置。默认创建一个新的 {@link Conversation} 实例。 */
    private Conversation conversation = new Conversation();

    /** 学习统计相关配置。默认创建一个新的 {@link Stats} 实例。 */
    private Stats stats = new Stats();

    /**
     * 会话配置子类。
     * <p>用于描述会话/登录状态等生命周期相关的属性。</p>
     */
    @Getter
    @Setter
    public static class Conversation {
        /** 会话过期天数，默认为 30 天。 */
        private int expireDays = 30;
    }

    /**
     * 学习统计配置子类。
     * <p>控制哪些对话计入复习统计（复习次数、活跃天数、连续天数与综合评分）。</p>
     */
    @Getter
    @Setter
    public static class Stats {
        /**
         * 是否忽略闲聊产生的复习记录，默认开启。
         * <p>
         * 开启后，"你好""谢谢"这类社交性对话不计入复习统计，避免虚增评分；
         * 与课程相关的实质问答（即使被识别为普通对话）仍会正常统计。
         * </p>
         */
        private boolean skipCasualChat = true;
    }
}
