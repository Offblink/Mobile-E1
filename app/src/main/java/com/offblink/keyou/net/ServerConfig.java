package com.offblink.keyou.net;

import com.offblink.keyou.log.AppLog;

import android.content.Context;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 服务端地址解析。
 *
 * 三种可用的访问路径：
 * 1. http://10.0.2.2:8081  —— 模拟器访问宿主机 loopback 的标准别名（首选）；
 * 2. http://127.0.0.1:8081 —— 设备本机回环；用 `adb reverse tcp:8081 tcp:8081`
 *    把宿主 8081 转发到设备 8081 后可用（部分 AVD 镜像的 App 侧网络栈异常时走这条）；
 * 3. 自定义地址 —— 真机连本机局域网 IP（如 http://192.168.1.5:8081），
 *    在「我的 → 服务器地址」里填，{@link #set} 写进 SharedPreferences 后长期生效。
 *
 * 记住最近一次成功的地址（进程内静态 + SharedPreferences 持久化），
 * 避免每次请求都先撞 4 秒超时。
 */
public final class ServerConfig {

    private static final String TAG = "Keyou/ServerConfig";

    /** SSM 服务端固定端口（端口 8080 被课堂08 的旧 Tomcat 占着） */
    private static final int DEFAULT_PORT = 8081;

    public static final String HOST_EMULATOR = "http://10.0.2.2:8081";
    public static final String HOST_LOOPBACK = "http://127.0.0.1:8081";

    private static final String SP_NAME = "server";
    private static final String SP_KEY = "base_url";

    /** 进程内缓存：最近一次成功的 base */
    private static volatile String cachedBase;

    private ServerConfig() {
    }

    /** 当前首选地址 */
    public static String base(Context ctx) {
        if (cachedBase != null) {
            return cachedBase;
        }
        String saved = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getString(SP_KEY, HOST_EMULATOR);
        cachedBase = saved;
        return saved;
    }

    /** 备用地址（与当前不同，可能为 null） */
    public static String fallback(Context ctx) {
        String current = base(ctx);
        if (HOST_EMULATOR.equals(current)) {
            return HOST_LOOPBACK;
        }
        if (HOST_LOOPBACK.equals(current)) {
            return HOST_EMULATOR;
        }
        return null;   // 用户自定义地址（真机局域网 IP）不做回退
    }

    /** 回退成功后调用：记住这次可用的地址 */
    public static void remember(Context ctx, String baseUrl) {
        if (baseUrl == null || baseUrl.equals(cachedBase)) {
            return;
        }
        cachedBase = baseUrl;
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(SP_KEY, baseUrl).apply();
        AppLog.i(TAG, "服务端地址切换为 " + baseUrl);
    }

    /**
     * 手动设置（真机换局域网 IP 用）。成功返回 true，地址非法返回 false 且不改动现值。
     */
    public static boolean set(Context ctx, String rawUrl) {
        String baseUrl = normalize(rawUrl);
        if (baseUrl == null) {
            return false;
        }
        cachedBase = baseUrl;
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(SP_KEY, baseUrl).apply();
        AppLog.i(TAG, "服务端地址改为 " + baseUrl);
        return true;
    }

    /** 恢复默认（模拟器地址），清掉持久化的自定义值 */
    public static void reset(Context ctx) {
        cachedBase = HOST_EMULATOR;
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().remove(SP_KEY).apply();
        AppLog.i(TAG, "服务端地址恢复默认 " + HOST_EMULATOR);
    }

    /**
     * 用户输入 → {@code scheme://host:port}，非法返回 null。
     *
     * 接受这些写法：{@code 192.168.1.5}、{@code 192.168.1.5:8080}、
     * {@code http://192.168.1.5:8081}、{@code mypc.local}；
     * 缺端口补 8081，尾斜杠忽略，带路径（…/api/mates）视为非法 —— 路径由 HttpUtil 拼。
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String text = input.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (!text.regionMatches(true, 0, "http://", 0, 7)
                && !text.regionMatches(true, 0, "https://", 0, 8)) {
            text = "http://" + text;
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException e) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return null;
        }
        String path = uri.getPath();
        if (path != null && !path.isEmpty()) {
            return null;   // 带路径的地址不接受，避免 //api/mates 这种拼法
        }
        int port = uri.getPort();
        if (port == -1) {
            port = DEFAULT_PORT;
        }
        if (port < 1 || port > 65535) {
            return null;
        }
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + ":" + port;
    }
}
