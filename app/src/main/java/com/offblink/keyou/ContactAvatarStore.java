package com.offblink.keyou;

import android.content.Context;

import java.io.File;
import java.util.Map;

/**
 * 班级成员的头像：服务端 mate 表没有头像字段，这里按 mate.id 记在本地 SP 里，
 * 文件落在应用私有目录 files/avatars/。本地联系人的头像存在 SQLite 的 avatar 列。
 */
public final class ContactAvatarStore {

    private static final String SP_NAME = "contact_avatars";
    private static final String KEY_PREFIX = "mate_";

    private ContactAvatarStore() {
    }

    public static String get(Context ctx, long remoteId) {
        String path = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + remoteId, null);
        if (path == null || !new File(path).exists()) {
            return null;
        }
        return path;
    }

    public static void put(Context ctx, long remoteId, String path) {
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_PREFIX + remoteId, path).apply();
    }

    public static void remove(Context ctx, long remoteId) {
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_PREFIX + remoteId).apply();
    }

    /** 新头像文件：文件名带时间戳，避免 Glide 按路径命中旧缓存 */
    public static File newAvatarFile(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "avatars");
        if (!dir.exists() && !dir.mkdirs()) {
            AppLog.w("Keyou/Avatar", "创建头像目录失败: " + dir);
        }
        return new File(dir, "c_" + System.currentTimeMillis() + ".jpg");
    }
}
