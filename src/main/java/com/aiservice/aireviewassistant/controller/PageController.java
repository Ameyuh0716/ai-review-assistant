package com.aiservice.aireviewassistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器：将 HTML 路径映射到 Thymeleaf 模板
 * 前端访问 /login.html → 渲染 templates/login.html
 */
@Controller
public class PageController {

    @GetMapping({"/", "/index.html"})
    public String index() {
        return "index";
    }

    @GetMapping("/login.html")
    public String login() {
        return "login";
    }

    @GetMapping("/courses.html")
    public String courses() {
        return "courses";
    }

    @GetMapping("/knowledge.html")
    public String knowledge() {
        return "knowledge";
    }

    @GetMapping("/quiz.html")
    public String quiz() {
        return "quiz";
    }

    @GetMapping("/plan.html")
    public String plan() {
        return "plan";
    }

    @GetMapping("/stats.html")
    public String stats() {
        return "stats";
    }
}
