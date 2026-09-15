package com.aiservice.aireviewassistant.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板加载与渲染工具类。
 * <p>支持从 classpath 的 {@code prompts/} 目录读取模板文件，并使用 {@code {{key}}} 占位符替换变量。
 * 模板内容会被缓存，避免每次重复读取文件。</p>
 */
@Component
public class PromptTemplate {

    /** 模板缓存，Key 为模板文件名，Value 为模板内容。 */
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 渲染指定模板。
     * <p>先加载模板内容，再用 {@code variables} 中的键值对替换 {@code {{key}}} 占位符；
     * 若变量为 {@code null}，则直接返回原模板。</p>
     *
     * @param templateName 模板文件名称（位于 classpath {@code prompts/} 下）
     * @param variables    用于替换占位符的键值对
     * @return 渲染后的字符串
     */
    public String render(String templateName, Map<String, String> variables) {
        String template = loadTemplate(templateName);
        if (variables == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    /**
     * 加载模板文件内容，并使用缓存避免重复读取。
     *
     * @param templateName 模板文件名称
     * @return 模板文件内容
     * @throws IllegalArgumentException 当模板文件不存在时抛出
     * @throws IllegalStateException    当读取模板发生 IO 异常时抛出
     */
    private String loadTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, name -> {
            try {
                ClassPathResource resource = new ClassPathResource("prompts/" + name);
                // 模板不存在时给出明确提示，避免空指针或难以定位的渲染错误
                if (!resource.exists()) {
                    throw new IllegalArgumentException("模板文件不存在: prompts/" + name);
                }
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("读取模板失败: prompts/" + name, e);
            }
        });
    }

    /**
     * 清除模板缓存。
     * <p>调用后下次渲染会重新从 classpath 加载模板，可用于支持模板热更新或测试场景。</p>
     */
    public void clearCache() {
        templateCache.clear();
    }
}
