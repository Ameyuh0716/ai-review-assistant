package com.aiservice.aireviewassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 复习助手 Spring Boot 应用启动类。
 * <p>
 * 负责初始化并启动整个 Spring Boot 应用容器，启用定时任务调度，
 * 以及支持外部化配置属性（Configuration Properties）的绑定。
 * </p>
 *
 * @author aiservice
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class AiReviewAssistantApplication {

	/**
	 * 应用入口方法。
	 *
	 * @param args 启动参数，由命令行传入，可包含 Spring Boot 配置项（如 --spring.profiles.active=prod）
	 */
	public static void main(String[] args) {
		// 运行 Spring Boot 应用，加载当前类作为配置源并传入启动参数
		SpringApplication.run(AiReviewAssistantApplication.class, args);
	}

}
