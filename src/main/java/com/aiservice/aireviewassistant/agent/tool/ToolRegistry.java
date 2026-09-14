package com.aiservice.aireviewassistant.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

// 工具注册表：集中管理所有 Agent 工具
@Component
public class ToolRegistry {

    // 工具名称 -> 工具实例
    private final Map<String, AgentTool> tools = new HashMap<>();

    // 通过构造函数收集所有 AgentTool 实现并注册
    public ToolRegistry(Collection<AgentTool> toolCollection) {
        for (AgentTool tool : toolCollection) {
            tools.put(tool.getName(), tool);
        }
    }

    // 根据工具名称获取工具
    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    // 获取所有工具
    public Collection<AgentTool> getAllTools() {
        return tools.values();
    }

    // 生成所有工具的 Schema 描述文本
    public String buildToolSchemas() {
        StringBuilder sb = new StringBuilder("可用工具：\n");
        int index = 1;
        for (AgentTool tool : tools.values()) {
            sb.append(index).append(". ").append(tool.getName())
              .append(" - ").append(tool.getDescription()).append("\n");
            sb.append("   参数：").append(tool.getParameterSchema()).append("\n");
            index++;
        }
        return sb.toString();
    }
}
