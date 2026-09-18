package com.aiservice.aireviewassistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback 控制器。
 * <p>Vue 3 单页应用的所有路由都通过 index.html 渲染，由前端 Vue Router 接管。</p>
 */
@Controller
public class PageController {

    /**
     * 所有非 API 路由统一返回 Vue 入口 index.html。
     * <p>匹配：/、/login、/courses、/knowledge、/quiz、/plan、/stats 等前端路由。</p>
     *
     * @return index.html 静态资源
     */
    @GetMapping(value = {
        "/",
        "/login",
        "/courses",
        "/knowledge",
        "/quiz",
        "/plan",
        "/stats"
    })
    public String index() {
        return "forward:/index.html";
    }
}
