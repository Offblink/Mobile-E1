package com.offblink.keyou.chat;

import com.offblink.keyou.util.Sizes;

/**
 * 群文件条目：`GET /api/files` 的一行 + 本机副本路径。
 *
 * localPath != null 表示这台设备上已经有这个文件（自己上传的，或之前下载过的），
 * 卡片点击直接打开；否则点击去下载。服务端按文件名唯一（同名覆盖），所以文件名就是主键。
 */
public final class ChatFile {

    private final String name;
    private final long size;
    private final String uploader;
    private final String uploadedAt;
    private final String localPath;

    public ChatFile(String name, long size, String uploader, String uploadedAt, String localPath) {
        this.name = name;
        this.size = size;
        this.uploader = uploader;
        this.uploadedAt = uploadedAt;
        this.localPath = localPath;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public String getUploader() {
        return uploader;
    }

    public String getUploadedAt() {
        return uploadedAt;
    }

    /** 本机副本绝对路径；null = 还没下载 */
    public String getLocalPath() {
        return localPath;
    }

    public boolean hasLocalCopy() {
        return localPath != null;
    }

    /** 卡片第二行：25.9 KB · 电脑端 · 2026-11-06 15:38 */
    public String meta() {
        return Sizes.human(size) + " · " + safe(uploader) + " · " + safe(uploadedAt);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }
}
