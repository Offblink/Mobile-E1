package com.offblink.keyou;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.File;

/**
 * 个人资料：昵称与头像路径，存 SharedPreferences（/data/data/<pkg>/shared_prefs/profile.xml）。
 * 头像文件落在应用私有目录 files/avatar.jpg，SP 里只记路径。
 */
public final class ProfileStore {

    private static final String SP_NAME = "profile";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_AVATAR = "avatar";
    private static final String AVATAR_FILE = "avatar.jpg";

    private ProfileStore() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /** 昵称；没设过就用 fallback（登录账号） */
    public static String nickname(Context ctx, String fallback) {
        String saved = sp(ctx).getString(KEY_NICKNAME, null);
        return TextUtils.isEmpty(saved) ? fallback : saved;
    }

    public static void setNickname(Context ctx, String nickname) {
        sp(ctx).edit().putString(KEY_NICKNAME, nickname).apply();
    }

    /** 头像文件（可能不存在） */
    public static File avatarFile(Context ctx) {
        return new File(ctx.getFilesDir(), AVATAR_FILE);
    }

    /** 当前头像路径；文件不在磁盘上就当没有 */
    public static String avatarPath(Context ctx) {
        String path = sp(ctx).getString(KEY_AVATAR, null);
        if (TextUtils.isEmpty(path) || !new File(path).exists()) {
            return null;
        }
        return path;
    }

    public static void setAvatarPath(Context ctx, String path) {
        sp(ctx).edit().putString(KEY_AVATAR, path).apply();
    }

    public static void clearAvatar(Context ctx) {
        sp(ctx).edit().remove(KEY_AVATAR).apply();
        File f = avatarFile(ctx);
        if (f.exists() && !f.delete()) {
            android.util.Log.w("Keyou/Profile", "旧头像删除失败: " + f);
        }
    }
}
