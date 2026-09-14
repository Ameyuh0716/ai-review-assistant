package com.aiservice.aireviewassistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// 业务应用配置
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    // 会话相关配置
    private Conversation conversation = new Conversation();

    @Getter
    @Setter
    public static class Conversation {
        // 会话过期天数，默认30天
        private int expireDays = 30;
    }
}
