package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.QuizService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 题目生成服务实现（Day 7）
@Slf4j
@Service
public class QuizServiceImpl implements QuizService {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public QuizServiceImpl(ChatClient chatClient, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public String generateQuiz(String topic) {
        return generateQuiz(topic, 1);
    }

    @Override
    @Cacheable(value = "agentResults", key = "'quiz:' + #topic + ':' + #count")
    public String generateQuiz(String topic, int count) {
        String prompt = buildQuizPrompt(topic, count);
        String systemPrompt = promptTemplate.render("quiz-system.txt", null);
        String raw = chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .call()
            .content();
        return formatQuizOutput(raw, topic);
    }

    @Override
    public Flux<String> generateQuizStream(String topic, int count) {
        String prompt = buildQuizPrompt(topic, count);
        String systemPrompt = promptTemplate.render("quiz-system.txt", null);
        return chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .stream()
            .content()
            .collect(StringBuilder::new, StringBuilder::append)
            .flatMapMany(sb -> {
                String formatted = formatQuizOutput(sb.toString(), topic);
                // 后端统一格式化后一次性发出，由前端按片段做字符流动画，
                // 避免 SSE 按字符传输出丢失换行/缩进。
                return Flux.just(formatted);
            });
    }

    private String buildQuizPrompt(String topic, int count) {
        return promptTemplate.render("quiz-user.txt", Map.of(
            "topic", topic,
            "count", String.valueOf(count)
        ));
    }

    /**
     * 将模型返回的任意格式题目内容，统一解析并格式化为规范 Markdown。
     * 支持：【题目1】、题目1：、### 题目 1： 等多种变体，以及选项/答案挤在同一行的情况。
     */
    public String formatQuizOutput(String raw, String topic) {
        return formatQuizOutput(raw, topic, false);
    }

    /**
     * 将模型返回的题目内容格式化为供对话展示的无答案 Markdown，隐藏答案与解析。
     */
    public String formatQuizOutputForChat(String raw, String topic) {
        return formatQuizOutput(raw, topic, true);
    }

    private String formatQuizOutput(String raw, String topic, boolean hideAnswer) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }

        // 1. 先尝试找到引导语（第一个题目标记之前的部分），并去掉末尾分隔线
        String intro = "";
        String body = raw;
        Pattern firstQuestionPattern = Pattern.compile("(?:###\\s*题目\\s*\\d+[：:]|[【\\[]题目\\d+[】\\]]|题目\\s*\\d+[：:])");
        Matcher firstQuestionMatcher = firstQuestionPattern.matcher(raw);
        if (firstQuestionMatcher.find()) {
            if (firstQuestionMatcher.start() > 0) {
                intro = raw.substring(0, firstQuestionMatcher.start()).trim().replaceAll("\\s*-{3,}\\s*$", "").trim();
            }
            body = raw.substring(firstQuestionMatcher.start());
        }

        // 2. 把题目分块
        List<String> blocks = splitQuestions(body);
        if (blocks.isEmpty()) {
            log.warn("[Quiz] 未从模型输出中识别出题目标记，返回原始内容。topic={}", topic);
            return raw;
        }

        // 3. 逐个解析并重整
        StringBuilder result = new StringBuilder();
        if (!intro.isEmpty()) {
            result.append(intro).append("\n\n");
        }

        int index = 1;
        for (String block : blocks) {
            Question question;
            try {
                question = parseQuestion(block, topic);
            } catch (Exception e) {
                log.warn("[Quiz] 解析第 {} 块题目失败，跳过。topic={}", index, topic, e);
                continue;
            }
            if (question == null) continue;

            if (index > 1) {
                result.append("---\n\n");
            }
            result.append("### 题目 ").append(index).append("：").append(question.subject).append("\n\n")
                .append(question.text).append("\n\n");
            for (String opt : question.options) {
                result.append(opt).append("\n");
            }
            if (!hideAnswer) {
                result.append("\n**答案：").append(question.answer).append("**\n\n")
                    .append("**解析：**\n");
                for (String exp : question.explanations) {
                    result.append("- ").append(exp).append("\n");
                }
            }
            index++;
        }

        String formatted = result.toString().trim();
        int actualCount = Math.max(0, index - 1);
        log.debug("[Quiz] 格式化完成：topic={}, hideAnswer={}, 原始块数={}, 有效题目数={}", topic, hideAnswer, blocks.size(), actualCount);
        return formatted;
    }

    private List<String> splitQuestions(String body) {
        List<String> blocks = new ArrayList<>();
        // 匹配：### 题目 1： 或 ###题目1： 或 【题目1】 或 题目1： 或 题目 1：
        Pattern pattern = Pattern.compile("(?:---\\s*)?(?:###\\s*题目\\s*\\d+[：:]|[【\\[]题目\\d+[】\\]]|题目\\s*\\d+[：:])");
        Matcher matcher = pattern.matcher(body);

        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
        }

        if (starts.isEmpty()) {
            // 没有检测到任何题目标记，可能整段就是一道题
            String trimmed = body.replaceAll("^---\\s*", "").trim();
            if (!trimmed.isEmpty()) blocks.add(trimmed);
            return blocks;
        }

        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : body.length();
            String block = body.substring(start, end).replaceAll("^---\\s*", "").trim();
            if (!block.isEmpty()) blocks.add(block);
        }
        return blocks;
    }

    private Question parseQuestion(String block, String defaultSubject) {
        // 去掉题目标记行（兼容 ### 题目 1： / 【题目1】 / 题目 1： 等）
        String content = block.replaceFirst("^(?:###\\s*)?题目\\s*\\d+[：:]", "")
                              .replaceFirst("^[【\\[]题目\\d+[】\\]]", "")
                              .trim();

        // 提取答案
        String answer = "";
        Pattern answerPattern = Pattern.compile("(?:\\*\\*|\\s)*答案[：:]([A-Da-d])(?:\\*\\*|\\s)*");
        Matcher answerMatcher = answerPattern.matcher(content);
        if (answerMatcher.find()) {
            answer = answerMatcher.group(1).toUpperCase();
            content = content.substring(0, answerMatcher.start()) + content.substring(answerMatcher.end());
        }

        // 提取解析：从"解析"开始到结尾，并去掉可能混入的末尾分隔线
        String explanationText = "";
        Pattern expPattern = Pattern.compile("(?:\\*\\*|\\s)*解析[：:](.*)$", Pattern.DOTALL);
        Matcher expMatcher = expPattern.matcher(content);
        if (expMatcher.find()) {
            explanationText = expMatcher.group(1).trim().replaceAll("\\s*-{3,}\\s*$", "").trim();
            content = content.substring(0, expMatcher.start()).trim();
        }

        // 提取选项 A/B/C/D（支持 A. / A、 / A． 及同行挤在一起、跨行的情况）
        List<String> options = new ArrayList<>();
        Pattern optPattern = Pattern.compile("([A-Da-d])[.．、]\\s*(.*?)(?=(?:[A-Da-d][.．、])|(?:答案)|(?:解析)|$)", Pattern.DOTALL);
        Matcher optMatcher = optPattern.matcher(content);
        int firstOptStart = -1;
        while (optMatcher.find()) {
            if (firstOptStart < 0) {
                firstOptStart = optMatcher.start();
            }
            String label = optMatcher.group(1).toUpperCase();
            // 把选项内部的换行、多余空白压缩成单行，避免前端渲染错乱
            String text = optMatcher.group(2).replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            options.add(label + ". " + text);
        }

        // 题干 = 选项前的内容
        String questionText = content;
        if (firstOptStart >= 0) {
            questionText = content.substring(0, firstOptStart).trim();
        }
        // 清理题干末尾可能的选项残留
        questionText = questionText.replaceAll("[A-Da-d][.．、]\\s*$", "").trim();

        // 学科名称：优先用 defaultSubject；若题干首行是短标题则提取为学科
        String subject = defaultSubject;
        String[] lines = questionText.split("\\s*\\n\\s*", 2);
        if (lines.length >= 1 && !lines[0].isEmpty()) {
            String firstLine = lines[0].trim();
            // 首行在 2~14 字之间、且以标点结尾，视为学科标题
            if (firstLine.length() >= 2 && firstLine.length() <= 14
                && (firstLine.endsWith("：") || firstLine.endsWith(":")
                    || firstLine.endsWith("，") || firstLine.endsWith(",")
                    || firstLine.endsWith("。") || firstLine.endsWith("."))) {
                subject = firstLine.substring(0, firstLine.length() - 1).trim();
                questionText = questionText.substring(firstLine.length()).trim();
            }
            // 若首行就是学科名（无标点）且与 topic 相近，也视为标题
            else if (firstLine.length() >= 2 && firstLine.length() <= 14
                     && !firstLine.contains("？") && !firstLine.contains("?")
                     && (firstLine.contains(defaultSubject) || defaultSubject.contains(firstLine))) {
                subject = firstLine;
                questionText = questionText.substring(firstLine.length()).trim();
            }
        }
        // 清理题干中重复出现的学科前缀，并把题干整理成连贯段落
        questionText = questionText.replaceFirst("^(?:" + Pattern.quote(subject) + "[：:]?\\s*)", "")
                                   .replaceAll("\\s*\\n\\s*", " ")
                                   .trim();

        // 解析拆成列表，清理 markdown 标记
        List<String> explanations = new ArrayList<>();
        if (!explanationText.isEmpty()) {
            String[] expLines = explanationText.split("\\r?\\n|(?=-[\\s-])");
            for (String line : expLines) {
                line = line.replaceAll("^[-*#]+\\s*", "")
                           .replaceAll("^\\*\\*\\s*", "")
                           .replaceAll("\\s*\\*\\*$", "")
                           .trim();
                if (line.length() >= 2) explanations.add(line);
            }
        }
        if (explanations.isEmpty()) {
            explanations.add("略");
        }

        // 如果没有选项，放弃解析
        if (options.isEmpty()) return null;

        return new Question(subject, questionText, options, answer, explanations);
    }

    private record Question(String subject, String text, List<String> options, String answer, List<String> explanations) {}
}
