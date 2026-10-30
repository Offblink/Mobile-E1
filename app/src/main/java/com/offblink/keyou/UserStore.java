package com.offblink.keyou;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 任务 5 延伸：本地账号存储（SharedPreferences）。
 *
 * 落位：/data/data/com.offblink.keyou/shared_prefs/users.xml
 * 结构：key = "u_" + 账号，value = 密码的 SHA-256 摘要（不存明文）。
 *
 * 说明：这是课程范围内的最小可用实现 —— 账号只在本机、单用户；
 * 真实项目应走服务端 + 加盐哈希（bcrypt/argon2）+ 传输层加密。
 */
public final class UserStore {

    private static final String SP_NAME = "users";
    private static final String KEY_PREFIX = "u_";

    private UserStore() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /** 注册：账号已存在返回 false */
    public static boolean register(Context ctx, String username, String password) {
        String key = KEY_PREFIX + username;
        if (sp(ctx).contains(key)) {
            return false;
        }
        sp(ctx).edit().putString(key, sha256(password)).apply();
        return true;
    }

    public static boolean exists(Context ctx, String username) {
        return !TextUtils.isEmpty(username) && sp(ctx).contains(KEY_PREFIX + username);
    }

    /** 登录校验：账号存在且密码匹配 */
    public static boolean check(Context ctx, String username, String password) {
        String saved = sp(ctx).getString(KEY_PREFIX + username, null);
        return saved != null && saved.equals(sha256(password));
    }

    /** SHA-256 摘要（十六进制小写） */
    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
