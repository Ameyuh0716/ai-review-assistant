package com.aiservice.aireviewassistant.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具注册表。
 * <p>
 * 集中管理所有 {@link AgentTool} 实现，提供按名称查找、获取全部工具以及生成工具 Schema 描述的能力。
 */
@Component
public class ToolRegistry {

    /** 工具名称到工具实例的映射。 */
    private final Map<String, AgentTool> tools = new HashMap<>();

    /**
     * 通过构造函数收集所有 {@link AgentTool} 实现并注册到注册表。
     *
     * @param toolCollection Spring 注入的所有工具实例集合
     */
    public ToolRegistry(Collection<AgentTool> toolCollection) {
        for (AgentTool tool : toolCollection) {
            tools.put(tool.getName(), tool);
        }
    }

    /**
     * 根据工具名称获取对应的工具实例。
     *
     * @param name 工具名称
     * @return 工具实例；未找到时返回 {@code null}
     */
    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取注册表中所有已注册的工具实例。
     *
     * @return 工具实例集合
     */
    public Collection<AgentTool> getAllTools() {
        return tools.values();
    }

    /**
     * 生成所有已注册工具的 Schema 描述文本，供大模型选择工具时使用。
     *
     * @return 工具名称、描述与参数 Schema 的拼接文本
     */
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
