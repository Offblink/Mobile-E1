package com.offblink.keyou;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用网络请求工具类。
 *
 * 封装点：
 * - GET / POST / PUT / DELETE（JSON body）；
 * - 子线程执行（统一单线程 Executor，禁止主线程网络请求 —— NetworkOnMainThreadException）；
 * - 回调统一回主线程（调用方不用再写 runOnUiThread）；
 * - 异常统一处理：失败回调拿到可读 message + 原始异常；
 * - 统一 UTF-8（避免中文乱码）。
 *
 * 地址回退（本机 AVD 实测需要）：先打 {@link ServerConfig#HOST_EMULATOR}（10.0.2.2），
 * 连接超时再自动退到 {@link ServerConfig#HOST_LOOPBACK}（127.0.0.1，配合 adb reverse），
 * 成功的那条会记住，后续不再重复撞超时。
 *
 * 明文 HTTP：Android 9+ 默认禁明文，见 network_security_config（放行 10.0.2.2/localhost）。
 */
public final class HttpUtil {

    private static final String TAG = "Keyou/Http";

    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 5000;

    /** 工作线程池：网络在子线程跑（单线程足够教学场景，避免乱序） */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /** 主线程 Handler：所有回调切回主线程 */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 回调接口：成功拿响应体字符串，失败拿可读错误 */
    public interface Callback {
        void onSuccess(String body);

        void onError(String message, Exception e);
    }

    private HttpUtil() {
    }

    // ---------- 对外 API：传「路径」，地址由 ServerConfig 决定并自动回退 ----------

    public static void get(Context ctx, String path, Callback callback) {
        request(ctx, "GET", path, null, callback, true);
    }

    public static void postJson(Context ctx, String path, String jsonBody, Callback callback) {
        request(ctx, "POST", path, jsonBody, callback, true);
    }

    public static void putJson(Context ctx, String path, String jsonBody, Callback callback) {
        request(ctx, "PUT", path, jsonBody, callback, true);
    }

    public static void delete(Context ctx, String path, Callback callback) {
        request(ctx, "DELETE", path, null, callback, true);
    }

    // ---------- 实现 ----------

    private static void request(Context ctx, String method, String path, String body,
                                Callback callback, boolean allowFallback) {
        EXECUTOR.execute(() -> {
            String base = ServerConfig.base(ctx);
            String result = null;
            Exception lastError = null;
            try {
                result = execute(method, base + path, body);
            } catch (Exception e) {
                lastError = e;
                Log.w(TAG, method + " " + base + path + " 失败: " + e.getMessage());
            }

            // 连接类失败 → 换备用地址再试一次
            if (result == null && allowFallback) {
                String fallback = ServerConfig.fallback(ctx);
                if (fallback != null && !fallback.equals(base)) {
                    Log.i(TAG, method + " 回退到 " + fallback + path);
                    try {
                        result = execute(method, fallback + path, body);
                        ServerConfig.remember(ctx, fallback);
                    } catch (Exception e) {
                        lastError = e;
                        Log.w(TAG, method + " " + fallback + path + " 也失败: " + e.getMessage());
                    }
                }
            }

            if (result != null) {
                final String ok = result;
                MAIN.post(() -> callback.onSuccess(ok));
            } else {
                final Exception e = lastError;
                final String msg = e == null ? "未知错误"
                        : (e.getMessage() != null ? e.getMessage() : e.toString());
                MAIN.post(() -> callback.onError(msg, e));
            }
        });
    }

    /** 真正发一次请求，返回响应体；失败抛异常 */
    private static String execute(String method, String url, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            if (body != null) {
                // POST/PUT body：必须开 doOutput，否则 getOutputStream 报错
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                String resp = readAll(conn.getInputStream());
                Log.i(TAG, method + " " + url + " -> " + code + " (" + resp.length() + "B)");
                return resp;
            }
            throw new IllegalStateException("HTTP " + code);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 读完整响应体（UTF-8，避免平台默认编码导致中文乱码） */
    private static String readAll(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
