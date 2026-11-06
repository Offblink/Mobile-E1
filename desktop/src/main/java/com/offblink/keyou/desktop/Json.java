package com.offblink.keyou.desktop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 工具（零第三方依赖）。
 *
 * 解析结果：对象 -> Map，数组 -> List，字符串 -> String，整数 -> Long，小数 -> Double，
 * true/false -> Boolean，null -> null。生成侧只提供字符串转义，供 POST 组包使用。
 */
final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    /** 解析完整 JSON 文本；格式非法时抛 IllegalArgumentException。 */
    static Object parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("响应内容为空");
        }
        Json j = new Json(text);
        j.skipSpace();
        Object v = j.value();
        j.skipSpace();
        return v;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    static List<Object> arr(Object v) {
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    static String str(Map<String, Object> m, String key) {
        return str(m.get(key));
    }

    static long lng(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(str(v).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 生成 JSON 字符串字面量（含引号）。 */
    static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ---------------------------------------------------------------- 解析

    private Object value() {
        char c = peek();
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        pos++; // '{'
        skipSpace();
        if (peek() == '}') {
            pos++;
            return m;
        }
        while (true) {
            skipSpace();
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            m.put(key, value());
            skipSpace();
            char c = next();
            if (c == '}') {
                return m;
            }
            if (c != ',') {
                throw err("对象缺少逗号");
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++; // '['
        skipSpace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(value());
            skipSpace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw err("数组缺少逗号");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > src.length()) {
                        throw err("\\u 转义不完整");
                    }
                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw err("非法转义 \\" + e);
            }
        }
    }

    private Object number() {
        int start = pos;
        while (pos < src.length() && isNumberChar(src.charAt(pos))) {
            pos++;
        }
        if (start == pos) {
            throw err("非法字符 '" + peek() + "'");
        }
        String raw = src.substring(start, pos);
        if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                // 超出 long 范围时退回 double
            }
        }
        return Double.parseDouble(raw);
    }

    private Object literal(String word, Object value) {
        if (!src.startsWith(word, pos)) {
            throw err("非法字面量");
        }
        pos += word.length();
        return value;
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
    }

    private void skipSpace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= src.length()) {
            throw err("内容意外结束");
        }
        return src.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw err("期望 '" + c + "'");
        }
    }

    private IllegalArgumentException err(String what) {
        return new IllegalArgumentException("JSON 解析失败（位置 " + pos + "）：" + what);
    }
}
