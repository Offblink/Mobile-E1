package com.offblink.keyou.server.entity;

/**
 * 班级群文件（对应 keyou_e1.app_file 表；文件本体落在 ${catalina.base}/files）。
 * uploadedAt 是已格式化的 "yyyy-MM-dd HH:mm" 文本。
 */
public class AppFile {

    private int id;
    private String name;
    private long size;
    private String uploader;
    private String uploadedAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public String getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(String uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
