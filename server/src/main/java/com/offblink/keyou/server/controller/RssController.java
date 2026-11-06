package com.offblink.keyou.server.controller;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务 7 练习用：RSS XML 端点（客户端用 XmlPullParser 解析）。
 * 数据源为 notice 表最新 20 条；表为空时返回空 channel，不抛 500。
 * produces 指定 application/rss+xml，ContentMapper 不会把它当 JSON 处理。
 */
@RestController
public class RssController {

    private static final Log log = LogFactory.getLog(RssController.class);

    private static final String SITE = "http://10.0.2.2:8081";

    /** RFC 822 时间（RSS 2.0 pubDate 规定格式）。 */
    private static final String RFC822 = "EEE, dd MMM yyyy HH:mm:ss Z";

    private record Item(int id, String title, String content, Timestamp created) {
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping(value = "/api/rss", produces = "application/rss+xml;charset=UTF-8")
    public String rss(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");

        List<Item> items;
        try {
            items = jdbcTemplate.query(
                    "SELECT id, title, content, created_at FROM notice ORDER BY id DESC LIMIT 20",
                    (rs, i) -> new Item(rs.getInt("id"), rs.getString("title"),
                            rs.getString("content"), rs.getTimestamp("created_at")));
        } catch (RuntimeException e) {
            // 建表未完成 / 库暂时不可用：退化成空 channel，别让客户端拿到 500
            log.warn("RssController: 读取 notice 失败，返回空 channel", e);
            items = List.of();
        }

        SimpleDateFormat rfc822 = new SimpleDateFormat(RFC822, Locale.US);
        Timestamp now = new Timestamp(System.currentTimeMillis());

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<rss version=\"2.0\">\n")
          .append("<channel>\n")
          .append("<title>课友 · 班级公告</title>\n")
          .append("<link>").append(SITE).append("</link>\n")
          .append("<description>班级公告（最新 20 条）</description>\n")
          .append("<language>zh-cn</language>\n")
          .append("<lastBuildDate>").append(rfc822.format(now)).append("</lastBuildDate>\n");

        for (Item it : items) {
            Timestamp created = it.created() == null ? now : it.created();
            sb.append("<item>\n")
              .append("<title>").append(esc(it.title())).append("</title>\n")
              .append("<description>").append(esc(it.content())).append("</description>\n")
              .append("<pubDate>").append(rfc822.format(created)).append("</pubDate>\n")
              .append("<guid isPermaLink=\"false\">").append(SITE).append("/notice/")
              .append(it.id()).append("</guid>\n")
              .append("<link>").append(SITE).append("/notice/").append(it.id()).append("</link>\n")
              .append("</item>\n");
        }

        sb.append("</channel>\n</rss>");
        return sb.toString();
    }

    /** XML 文本转义；顺带丢掉 XML 1.0 不允许的控制字符。 */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default:
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }
}
