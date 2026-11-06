package com.offblink.keyou.desktop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 服务端访问层（java.net.http，零第三方依赖）。
 *
 * 统一处理 {code, message, data} 包装、连接超时 3 秒 / 请求超时 10 秒，
 * 并把网络异常翻译成中文提示。
 */
final class Api {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** GET /api/ping 的 data。 */
    record Ping(String db) {}

    /** 公告。createdAt 为服务端原样字符串（如 "2026-11-06 14:30"）。 */
    record Notice(long id, String title, String content, String author, String createdAt) {}

    /** 群消息。id 升序递增。 */
    record Message(long id, String sender, String content, String createdAt) {}

    /** 群文件。 */
    record FileInfo(String name, long size, String uploader, String uploadedAt) {}

    /** 业务/网络错误，message 可直接展示在状态栏。transport=true 表示连不上服务器（而非业务报错）。 */
    static final class ApiException extends Exception {
        private final boolean transport;

        ApiException(String message) {
            this(message, false);
        }

        ApiException(String message, boolean transport) {
            super(message);
            this.transport = transport;
        }

        boolean transport() {
            return transport;
        }
    }

    private final String base;
    private final HttpClient http;

    Api(String rawBase) throws ApiException {
        String b = rawBase == null ? "" : rawBase.trim();
        if (b.isEmpty()) {
            throw new ApiException("服务器地址不能为空");
        }
        if (!b.startsWith("http://") && !b.startsWith("https://")) {
            b = "http://" + b;
        }
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        try {
            URI.create(b + "/api/ping");
        } catch (IllegalArgumentException e) {
            throw new ApiException("服务器地址无效：" + rawBase);
        }
        this.base = b;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    String base() {
        return base;
    }

    // ------------------------------------------------------------- 接口

    Ping ping() throws ApiException {
        Map<String, Object> data = first(call("GET", "/api/ping", null, null));
        return new Ping(Json.str(data, "db"));
    }

    List<Notice> notices(int size) throws ApiException {
        Object data = call("GET", "/api/notices?size=" + size, null, null);
        List<Notice> out = new ArrayList<>();
        for (Object o : Json.arr(data)) {
            out.add(toNotice(Json.obj(o)));
        }
        return out;
    }

    Notice createNotice(String title, String content, String author) throws ApiException {
        String body = "{\"title\":" + Json.quote(title)
                + ",\"content\":" + Json.quote(content)
                + ",\"author\":" + Json.quote(author) + "}";
        Object data = call("POST", "/api/notice",
                HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8),
                "application/json; charset=UTF-8");
        Map<String, Object> m = first(data);
        if (m.isEmpty()) {
            return new Notice(0L, title, content, author, "");
        }
        return toNotice(m);
    }

    List<Message> messages(long after, int size) throws ApiException {
        Object data = call("GET", "/api/messages?after=" + after + "&size=" + size, null, null);
        List<Message> out = new ArrayList<>();
        for (Object o : Json.arr(data)) {
            out.add(toMessage(Json.obj(o)));
        }
        return out;
    }

    Message createMessage(String sender, String content) throws ApiException {
        String body = "{\"sender\":" + Json.quote(sender)
                + ",\"content\":" + Json.quote(content) + "}";
        Object data = call("POST", "/api/message",
                HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8),
                "application/json; charset=UTF-8");
        Map<String, Object> m = first(data);
        if (m.isEmpty()) {
            return new Message(0L, sender, content, "");
        }
        return toMessage(m);
    }

    List<FileInfo> files() throws ApiException {
        Object data = call("GET", "/api/files", null, null);
        List<FileInfo> out = new ArrayList<>();
        for (Object o : Json.arr(data)) {
            out.add(toFile(Json.obj(o)));
        }
        return out;
    }

    FileInfo upload(Path file, String uploader) throws ApiException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ApiException("读取文件失败：" + e.getMessage());
        }
        String boundary = "----KeyouBoundary" + System.currentTimeMillis() + Integer.toHexString(bytes.length);
        String fileName = file.getFileName().toString().replace("\"", "");

        ByteArrayOutputStream body = new ByteArrayOutputStream(bytes.length + 512);
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        write(body, "Content-Type: application/octet-stream\r\n\r\n");
        body.write(bytes, 0, bytes.length);
        write(body, "\r\n--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"uploader\"\r\n\r\n");
        write(body, uploader);
        write(body, "\r\n--" + boundary + "--\r\n");

        Object data = call("POST", "/api/file",
                HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()),
                "multipart/form-data; boundary=" + boundary);
        Map<String, Object> m = first(data);
        if (m.isEmpty()) {
            return new FileInfo(fileName, bytes.length, uploader, "");
        }
        return toFile(m);
    }

    /**
     * 下载群文件到 target（响应体流式写盘，不整包进内存）。
     * 非 2xx：删掉半截文件再抛；目标目录自动建。
     * 读超时放宽到 60 秒 —— 文件大时比 JSON 请求慢得多。
     */
    void download(String name, Path target) throws ApiException {
        String encoded = java.net.URLEncoder.encode(name, StandardCharsets.UTF_8)
                .replace("+", "%20");
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/file/" + encoded))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "*/*")
                .GET()
                .build();

        HttpResponse<Path> resp;
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            resp = http.send(request, HttpResponse.BodyHandlers.ofFile(target));
        } catch (HttpTimeoutException e) {
            throw new ApiException("下载超时（服务器 60 秒未响应）", true);
        } catch (ConnectException e) {
            throw new ApiException("无法连接服务器，请确认地址与端口", true);
        } catch (UnknownHostException e) {
            throw new ApiException("无法解析服务器地址", true);
        } catch (IOException e) {
            throw new ApiException("网络错误：" + e.getMessage(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("请求被中断", true);
        }

        if (resp.statusCode() != 200) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 删不掉就留着，下次点击会按文件大小重下
            }
            throw new ApiException("HTTP " + resp.statusCode() + "（下载失败）");
        }
    }

    // ------------------------------------------------------------- 内部

    private Object call(String method, String path, HttpRequest.BodyPublisher body, String contentType)
            throws ApiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : body);

        HttpResponse<String> resp;
        try {
            resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ApiException("请求超时（服务器 10 秒未响应）", true);
        } catch (ConnectException e) {
            throw new ApiException("无法连接服务器，请确认地址与端口", true);
        } catch (UnknownHostException e) {
            throw new ApiException("无法解析服务器地址", true);
        } catch (IOException e) {
            throw new ApiException("网络错误：" + e.getMessage(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("请求被中断", true);
        }

        Object root = null;
        String text = resp.body();
        try {
            root = Json.parse(text);
        } catch (RuntimeException ignored) {
            // 非 JSON 响应（如 Tomcat 404 页面）稍后按状态码报错
        }

        if (resp.statusCode() != 200) {
            String msg = root instanceof Map<?, ?> ? Json.str(Json.obj(root), "message") : "";
            throw new ApiException("HTTP " + resp.statusCode() + (msg.isEmpty() ? "" : "：" + msg));
        }
        if (!(root instanceof Map)) {
            throw new ApiException("响应不是 JSON 对象");
        }
        Map<String, Object> map = Json.obj(root);
        if (Json.lng(map.get("code")) != 0) {
            String msg = Json.str(map, "message");
            throw new ApiException(msg.isEmpty() ? "服务端返回错误" : msg);
        }
        return map.get("data");
    }

    /** data 可能是对象，也可能是单元素数组（服务端 ApiResult.ok(List.of(x)) 习惯）。 */
    private static Map<String, Object> first(Object data) {
        if (data instanceof List<?> list) {
            return list.isEmpty() ? Map.of() : Json.obj(list.get(0));
        }
        return Json.obj(data);
    }

    private static Notice toNotice(Map<String, Object> m) {
        return new Notice(Json.lng(m.get("id")), Json.str(m, "title"), Json.str(m, "content"),
                Json.str(m, "author"), Json.str(m, "createdAt"));
    }

    private static Message toMessage(Map<String, Object> m) {
        return new Message(Json.lng(m.get("id")), Json.str(m, "sender"), Json.str(m, "content"),
                Json.str(m, "createdAt"));
    }

    private static FileInfo toFile(Map<String, Object> m) {
        return new FileInfo(Json.str(m, "name"), Json.lng(m.get("size")),
                Json.str(m, "uploader"), Json.str(m, "uploadedAt"));
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write(b, 0, b.length);
    }

    /** 服务端时间字符串 -> "yyyy-MM-dd HH:mm"。 */
    static String displayTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "-";
        }
        String t = raw.trim().replace('T', ' ');
        if (t.endsWith("Z")) {
            t = t.substring(0, t.length() - 1);
        }
        int dot = t.indexOf('.');
        if (dot > 0) {
            t = t.substring(0, dot);
        }
        return t.length() > 16 ? t.substring(0, 16) : t;
    }
}
