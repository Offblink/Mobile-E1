package com.offblink.keyou.chat;

import android.content.Context;

import java.io.File;

/**
 * 群文件的本机副本：按文件名记在 SharedPreferences，文件落在 files/chat/ 下。
 *
 * - uploads/：自己上传成功的原件（上传前先在 cacheDir 组包，成功后搬进来）；
 * - downloads/：从别人那里下载的原件。
 * 两个目录都在 files/ 下，FileProvider 的 files-path 才能把 content:// 给外部应用打开。
 */
public final class ChatFileStore {

    private static final String SP_NAME = "chat_files";
    private static final String KEY_PREFIX = "f_";

    private ChatFileStore() {
    }

    public static File uploadsDir(Context ctx) {
        return ensure(new File(ctx.getFilesDir(), "chat/uploads"));
    }

    public static File downloadsDir(Context ctx) {
        return ensure(new File(ctx.getFilesDir(), "chat/downloads"));
    }

    /** 下载目标文件（父目录已建好，文件本身可能还不存在） */
    public static File newDownload(Context ctx, String name) {
        return new File(downloadsDir(ctx), safeName(name));
    }

    /** 本机副本路径；文件被用户清掉时返回 null（顺手把脏记录删了） */
    public static String get(Context ctx, String name) {
        String path = sp(ctx).getString(KEY_PREFIX + name, null);
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (file.isFile()) {
            return path;
        }
        sp(ctx).edit().remove(KEY_PREFIX + name).apply();
        return null;
    }

    /** 记录本机副本（同名覆盖时指向新文件） */
    public static void put(Context ctx, String name, File file) {
        sp(ctx).edit().putString(KEY_PREFIX + name, file.getAbsolutePath()).apply();
    }

    /** 只留 basename，别让外部文件名里的路径分隔符把文件写到目录外 */
    private static String safeName(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isEmpty() ? "file" : name;
    }

    private static File ensure(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            // 目录建不出来时后续写入会失败，调用方会看到异常并 toast
            dir.mkdirs();
        }
        return dir;
    }

    private static android.content.SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }
}
