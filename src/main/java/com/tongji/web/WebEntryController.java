package com.tongji.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端入口路由。
 *
 * <p>显式将根路径重定向到静态首页，避免在不同容器配置下出现欢迎页未命中的 404。</p>
 */
@Controller
public class WebEntryController {

    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }
}
