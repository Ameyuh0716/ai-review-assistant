package com.aiservice.aireviewassistant.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Prompt 模板加载与渲染工具：支持从 classpath 读取模板文件，并用 {{key}} 替换变量
@Component
public class PromptTemplate {

    // 模板缓存，避免每次重复读取文件
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    // 渲染模板：根据名称加载模板并用 variables 替换 {{key}}
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

    // 加载模板文件内容
    private String loadTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, name -> {
            try {
                ClassPathResource resource = new ClassPathResource("prompts/" + name);
                if (!resource.exists()) {
                    throw new IllegalArgumentException("模板文件不存在: prompts/" + name);
                }
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("读取模板失败: prompts/" + name, e);
            }
        });
    }

    // 清除缓存，支持动态重新加载
    public void clearCache() {
        templateCache.clear();
    }
}
