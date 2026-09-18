package com.aiservice.aireviewassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存学习计划请求 DTO。
 * <p>前端在生成计划后，通过 JSON 请求体提交课程名称、可用天数与计划正文。</p>
 */
@Getter
@Setter
public class SavePlanRequest {

    /** 课程名称。 */
    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    /** 可用天数描述，例如 "7天"。 */
    @NotBlank(message = "可用天数不能为空")
    private String availableDays;

    /** 计划正文（Markdown 格式）。 */
    @NotBlank(message = "计划内容不能为空")
    private String content;
}
