package com.offblink.keyou.server.controller;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offblink.keyou.server.entity.ChatMessage;
import com.offblink.keyou.server.util.Times;

/**
 * 班级群消息接口（客户端轮询增量拉取）。
 *
 * GET  /api/messages?after=0&size=100   只返回 id &gt; after 的消息，id 升序
 * POST /api/message                     发消息（JSON body：sender/content）
 *
 * 统一响应：{code, message, data}（见 ApiResult）。
 */
@RestController
@RequestMapping("/api")
public class MessageController {

    private static final String COLUMNS = "id, sender, content, created_at";

    private static final RowMapper<ChatMessage> ROW = (rs, i) -> {
        ChatMessage m = new ChatMessage();
        m.setId(rs.getInt("id"));
        m.setSender(rs.getString("sender"));
        m.setContent(rs.getString("content"));
        m.setCreatedAt(Times.fmt(rs.getTimestamp("created_at")));
        return m;
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/messages")
    public ApiResult list(@RequestParam(value = "after", defaultValue = "0") long after,
                          @RequestParam(value = "size", defaultValue = "100") int size) {
        if (after < 0) {
            after = 0;
        }
        if (size < 1 || size > 500) {
            size = 100;
        }
        List<ChatMessage> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM chat_message WHERE id > ? ORDER BY id ASC LIMIT ?",
                ROW, after, size);
        return ApiResult.ok(list);
    }

    @PostMapping("/message")
    public ApiResult add(@RequestBody ChatMessage message) {
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            return ApiResult.error(400, "content 不能为空");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO chat_message(sender, content) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, message.getSender());
            ps.setString(2, message.getContent());
            return ps;
        }, keyHolder);
        return ApiResult.ok(List.of(byId(keyHolder.getKey().longValue())));
    }

    private ChatMessage byId(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT " + COLUMNS + " FROM chat_message WHERE id = ?", ROW, id);
    }
}
