package com.aiservice.aireviewassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 使用测试环境配置，避免依赖外部数据库
@ActiveProfiles("test")
@SpringBootTest
class AiReviewAssistantApplicationTests {

	@Test
	void contextLoads() {
	}

}
