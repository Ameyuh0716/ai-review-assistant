package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.PlanService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

// 复习计划服务实现（Day 8）
@Service
public class PlanServiceImpl implements PlanService {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public PlanServiceImpl(ChatClient chatClient, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
    }

    @Override
    @Cacheable(value = "agentResults", key = "'plan:' + #courseName + ':' + #availableDays")
    public String createPlan(String courseName, String availableDays) {
        String prompt = buildPlanPrompt(courseName, availableDays);
        String systemPrompt = promptTemplate.render("plan-system.txt", null);
        return chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .call()
            .content();
    }

    @Override
    public Flux<String> createPlanStream(String courseName, String availableDays) {
        String prompt = buildPlanPrompt(courseName, availableDays);
        String systemPrompt = promptTemplate.render("plan-system.txt", null);
        return chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .stream()
            .content();
    }

    private String buildPlanPrompt(String courseName, String availableDays) {
        return promptTemplate.render("plan-user.txt", Map.of(
            "courseName", courseName,
            "availableDays", availableDays
        ));
    }
}
