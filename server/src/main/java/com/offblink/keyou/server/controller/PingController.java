package com.offblink.keyou.server.controller;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查：验证 Spring MVC 链路 + MySQL 连通性。
 * curl http://localhost:8080/api/ping
 */
@RestController
public class PingController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/api/ping")
    public ApiResult ping() {
        String dbVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        return ApiResult.ok(Map.of("db", "MySQL " + dbVersion));
    }
}
