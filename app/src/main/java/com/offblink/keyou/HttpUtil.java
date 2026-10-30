package com.offblink.keyou;

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
 * 任务 7：通用网络请求工具类。
 *
 * 封装点（指导书验收点）：
 * - GET / POST（JSON body）；
 * - 子线程执行（统一单线程 Executor，禁止主线程网络请求 —— NetworkOnMainThreadException）；
 * - 回调统一回主线程（调用方不用再写 runOnUiThread）；
 * - 异常统一处理：失败回调拿到可读 message + 原始异常。
 *
 * 明文 HTTP：Android 9+ 默认禁明文，见 AndroidManifest 的 networkSecurityConfig
 * （放行 10.0.2.2/localhost，模拟器访问本机后端用 10.0.2.2）。
 */
public final class HttpUtil {

    private static final String TAG = "Keyou/Http";

    private static final int CONNECT_TIMEOUT_MS = 5000;
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

    /** GET 请求 */
    public static void get(String url, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    String body = readAll(conn.getInputStream());
                    Log.i(TAG, "GET " + url + " -> " + code + " (" + body.length() + "B)");
                    postSuccess(callback, body);
                } else {
                    Log.w(TAG, "GET " + url + " -> HTTP " + code);
                    postError(callback, "HTTP " + code, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "GET " + url + " 失败", e);
                postError(callback, e.getMessage() != null ? e.getMessage() : e.toString(), e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /** POST JSON 请求（Content-Type: application/json; charset=UTF-8） */
    public static void postJson(String url, String jsonBody, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                // POST body 必须开 doOutput，否则 getOutputStream 报错
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    String body = readAll(conn.getInputStream());
                    Log.i(TAG, "POST " + url + " -> " + code);
                    postSuccess(callback, body);
                } else {
                    Log.w(TAG, "POST " + url + " -> HTTP " + code);
                    postError(callback, "HTTP " + code, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "POST " + url + " 失败", e);
                postError(callback, e.getMessage() != null ? e.getMessage() : e.toString(), e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /** PUT JSON 请求（任务 9 编辑用） */
    public static void putJson(String url, String jsonBody, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("PUT");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    postSuccess(callback, readAll(conn.getInputStream()));
                } else {
                    postError(callback, "HTTP " + code, null);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage() != null ? e.getMessage() : e.toString(), e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /** DELETE 请求（任务 9 删除用） */
    public static void delete(String url, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    postSuccess(callback, readAll(conn.getInputStream()));
                } else {
                    postError(callback, "HTTP " + code, null);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage() != null ? e.getMessage() : e.toString(), e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /** 读完整响应体（UTF-8，避免平台默认编码中文乱码） */
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

    private static void postSuccess(Callback cb, String body) {
        MAIN.post(() -> cb.onSuccess(body));
    }

    private static void postError(Callback cb, String message, Exception e) {
        MAIN.post(() -> cb.onError(message, e));
    }
}
