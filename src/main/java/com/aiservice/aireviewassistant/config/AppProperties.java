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
}
