package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.RagService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

// RAG问答工具：基于知识库回答课程问题
@Component
public class QuestionTool implements AgentTool {

    private final RagService ragService;

    public QuestionTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String getName() {
        return "QUESTION";
    }

    @Override
    public String getDescription() {
        return "基于知识库回答课程问题";
    }

    @Override
    public String getParameterSchema() {
        return "无";
    }

    @Override
    public String execute(ToolContext context) {
        return ragService.answerQuestion(context.getUserMessage(), context.getConversationId());
    }

    @Override
    public Flux<String> stream(ToolContext context) {
        return ragService.answerQuestionStream(context.getUserMessage(), context.getConversationId());
    }
}
