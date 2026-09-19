package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.mapper.CoursesMapper;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程服务实现类。
 * <p>基于 MyBatis-Plus 通用 Service 实现按用户 ID 查询课程列表，并提供 AI 自动生成课程描述能力。</p>
 */
@Slf4j
@Service
public class CoursesServiceImpl extends ServiceImpl<CoursesMapper, Courses> implements CoursesService {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    /**
     * 构造课程服务。
     *
     * @param chatClient     Spring AI 聊天客户端，用于生成课程描述
     * @param promptTemplate 提示词模板渲染器
     */
    public CoursesServiceImpl(ChatClient chatClient, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public List<Courses> listByUserId(Integer userId) {
        // 按用户 ID 等值过滤，并按 updated_at 降序排列，最近更新的课程排在前面
        return lambdaQuery()
            .eq(Courses::getUserId, userId)
            .orderByDesc(Courses::getUpdatedAt)
            .list();
    }

    /**
     * 根据课程名称生成课程描述。
     * <p>
     * 使用 {@code prompts/course-desc-system.txt} 约束输出风格；课程名相同的结果会被缓存，
     * 避免重复调用大模型。生成失败时返回兜底描述，保证新建课程流程不中断。
     * </p>
     *
     * @param courseName 课程名称
     * @return 课程描述文本
     */
    @Override
    @Cacheable(value = "agentResults", key = "'course-desc:' + #courseName")
    public String generateDescription(String courseName) {
        try {
            String systemPrompt = promptTemplate.render("course-desc-system.txt", null);
            String description = chatClient.prompt()
                .system(systemPrompt)
                .user("课程名称：" + courseName)
                .call()
                .content();
            if (description != null && !description.isBlank()) {
                // 去掉可能的引号、换行与 markdown 标记，保证描述为单行纯文本
                return description.trim()
                    .replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "")
                    .replaceAll("\\s*\\n\\s*", " ")
                    .replaceAll("^#+\\s*", "")
                    .trim();
            }
        } catch (Exception e) {
            log.warn("[Courses] AI 生成课程描述失败: {}", e.getMessage());
        }
        return "「" + courseName + "」相关知识点学习与复习";
    }
}
