package com.offblink.keyou.server.controller;

import java.util.Collections;
import java.util.Map;

/**
 * 统一响应体：{ code, message, data }。
 * data 恒为数组 —— 客户端解析约定（见 SPEC §5）。
 */
public class ApiResult {

    private final int code;
    private final String message;
    private final Object data;

    private ApiResult(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static ApiResult ok(Object data) {
        return new ApiResult(0, "ok", data);
    }

    public static ApiResult ok() {
        return new ApiResult(0, "ok", Collections.emptyList());
    }

    public static ApiResult error(int code, String message) {
        return new ApiResult(code, message, Collections.emptyList());
    }

    public static ApiResult page(Object list, long total) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return new ApiResult(0, "ok", data);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
