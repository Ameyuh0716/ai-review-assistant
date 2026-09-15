package com.aiservice.aireviewassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot 应用上下文加载测试。
 *
 * <p>测试目标：验证在 test 环境下，Spring Boot 应用能够正常启动并完成上下文初始化，
 * 不依赖外部数据库等真实基础设施。</p>
 */
@ActiveProfiles("test")
@SpringBootTest
class AiReviewAssistantApplicationTests {

	/**
	 * 验证 Spring Boot 上下文可以成功加载。
	 *
	 * <p>准备条件：使用 application-test.yml 中的内存/模拟配置。</p>
	 * <p>断言意图：若上下文初始化失败，测试方法本身即会抛出异常导致失败。</p>
	 */
	@Test
	void contextLoads() {
	}

}
