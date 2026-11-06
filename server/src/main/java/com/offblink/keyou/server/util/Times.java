package com.offblink.keyou.server.util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

/**
 * 时间文本化：数据库存 DATETIME，出参统一格式化为 "yyyy-MM-dd HH:mm"。
 * 刻意不引入 Jackson 全局时间配置 —— 客户端只认这一个字符串格式，写死在出口处最保险。
 */
public final class Times {

    public static final String PATTERN = "yyyy-MM-dd HH:mm";

    private Times() {
    }

    public static String fmt(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        // SimpleDateFormat 非线程安全，按调用创建（代价可忽略，换取零共享状态）
        return new SimpleDateFormat(PATTERN).format(ts);
    }
}
