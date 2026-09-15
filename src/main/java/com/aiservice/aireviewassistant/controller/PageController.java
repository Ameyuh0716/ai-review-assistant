package com.aiservice.aireviewassistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器。
 * <p>将前端 HTML 路径映射到 Thymeleaf 模板，例如访问 /login.html 即渲染 templates/login.html。</p>
 */
@Controller
public class PageController {

    /**
     * 首页路由。
     * <p>GET / 与 GET /index.html 均返回 index 模板。</p>
     *
     * @return index 视图名
     */
    @GetMapping({"/", "/index.html"})
    public String index() {
        return "index";
    }

    /**
     * 登录页路由。
     * <p>GET /login.html 返回 login 模板。</p>
     *
     * @return login 视图名
     */
    @GetMapping("/login.html")
    public String login() {
        return "login";
    }

    /**
     * 课程管理页路由。
     * <p>GET /courses.html 返回 courses 模板。</p>
     *
     * @return courses 视图名
     */
    @GetMapping("/courses.html")
    public String courses() {
        return "courses";
    }

    /**
     * 知识库页路由。
     * <p>GET /knowledge.html 返回 knowledge 模板。</p>
     *
     * @return knowledge 视图名
     */
    @GetMapping("/knowledge.html")
    public String knowledge() {
        return "knowledge";
    }

    /**
     * 测验页路由。
     * <p>GET /quiz.html 返回 quiz 模板。</p>
     *
     * @return quiz 视图名
     */
    @GetMapping("/quiz.html")
    public String quiz() {
        return "quiz";
    }

    /**
     * 学习计划页路由。
     * <p>GET /plan.html 返回 plan 模板。</p>
     *
     * @return plan 视图名
     */
    @GetMapping("/plan.html")
    public String plan() {
        return "plan";
    }

    /**
     * 统计页路由。
     * <p>GET /stats.html 返回 stats 模板。</p>
     *
     * @return stats 视图名
     */
    @GetMapping("/stats.html")
    public String stats() {
        return "stats";
    }
}
