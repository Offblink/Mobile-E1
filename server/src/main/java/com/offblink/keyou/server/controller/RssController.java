package com.offblink.keyou.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/**
 * 任务 7 练习用：RSS XML 端点（客户端用 XmlPullParser 解析）。
 * 注意 produces 指定 application/rss+xml，ContentMapper 不会把它当 JSON 处理。
 */
@RestController
public class RssController {

    @GetMapping(value = "/api/rss", produces = "application/rss+xml;charset=UTF-8")
    public String rss(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<rss version=\"2.0\">\n" +
                "<channel>\n" +
                "<title>课友 · 班级公告</title>\n" +
                "<link>http://10.0.2.2:8081</link>\n" +
                "<description>实验一任务 7 的 RSS 演示数据</description>\n" +
                "<item>\n" +
                "<title>周三下午 301 教室数据结构课调到 405</title>\n" +
                "<link>http://10.0.2.2:8081/notice/1</link>\n" +
                "</item>\n" +
                "<item>\n" +
                "<title>本周五交实验报告任务 1~3</title>\n" +
                "<link>http://10.0.2.2:8081/notice/2</link>\n" +
                "</item>\n" +
                "<item>\n" +
                "<title>班级篮球赛报名截止本周日</title>\n" +
                "<link>http://10.0.2.2:8081/notice/3</link>\n" +
                "</item>\n" +
                "</channel>\n" +
                "</rss>";
    }
}
