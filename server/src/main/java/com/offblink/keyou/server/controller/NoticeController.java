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

import com.offblink.keyou.server.entity.Notice;
import com.offblink.keyou.server.util.Times;

/**
 * 班级公告接口（客户端"班级群 - 公告"页）。
 *
 * GET  /api/notices?size=50   公告列表（id 倒序）
 * POST /api/notice            发布公告（JSON body：title/content/author）
 *
 * 统一响应：{code, message, data}（见 ApiResult）。
 */
@RestController
@RequestMapping("/api")
public class NoticeController {

    private static final String COLUMNS = "id, title, content, author, created_at";

    /** created_at 是 DATETIME，出口统一格式化成 "yyyy-MM-dd HH:mm"。 */
    private static final RowMapper<Notice> ROW = (rs, i) -> {
        Notice n = new Notice();
        n.setId(rs.getInt("id"));
        n.setTitle(rs.getString("title"));
        n.setContent(rs.getString("content"));
        n.setAuthor(rs.getString("author"));
        n.setCreatedAt(Times.fmt(rs.getTimestamp("created_at")));
        return n;
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/notices")
    public ApiResult list(@RequestParam(value = "size", defaultValue = "50") int size) {
        if (size < 1 || size > 200) {
            size = 50;
        }
        List<Notice> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM notice ORDER BY id DESC LIMIT ?", ROW, size);
        return ApiResult.ok(list);
    }

    @PostMapping("/notice")
    public ApiResult add(@RequestBody Notice notice) {
        if (notice.getTitle() == null || notice.getTitle().trim().isEmpty()) {
            return ApiResult.error(400, "title 不能为空");
        }
        String title = notice.getTitle().trim();
        if (title.length() > 120) {
            return ApiResult.error(400, "title 超长（最多 120 字）");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO notice(title, content, author) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, notice.getContent());
            ps.setString(3, notice.getAuthor());
            return ps;
        }, keyHolder);
        return ApiResult.ok(List.of(byId(keyHolder.getKey().longValue())));
    }

    private Notice byId(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT " + COLUMNS + " FROM notice WHERE id = ?", ROW, id);
    }
}
