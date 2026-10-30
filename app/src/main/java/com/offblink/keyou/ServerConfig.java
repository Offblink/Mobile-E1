package com.offblink.keyou;

import android.content.Context;
import android.util.Log;

/**
 * 服务端地址解析。
 *
 * 两种可用的访问路径：
 * 1. http://10.0.2.2:8081  —— 模拟器访问宿主机 loopback 的标准别名（首选）；
 * 2. http://127.0.0.1:8081 —— 设备本机回环；用 `adb reverse tcp:8081 tcp:8081`
 *    把宿主 8081 转发到设备 8081 后可用（部分 AVD 镜像的 App 侧网络栈异常时走这条）。
 *
 * 记住最近一次成功的地址（进程内静态 + SharedPreferences 持久化），
 * 避免每次请求都先撞 5 秒超时。
 */
public final class ServerConfig {

    private static final String TAG = "Keyou/ServerConfig";

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
        Log.i(TAG, "服务端地址切换为 " + baseUrl);
    }

    /** 手动设置（真机换局域网 IP 用） */
    public static void set(Context ctx, String baseUrl) {
        cachedBase = baseUrl;
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(SP_KEY, baseUrl).apply();
    }
}
