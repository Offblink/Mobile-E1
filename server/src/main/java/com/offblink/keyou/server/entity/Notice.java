package com.offblink.keyou.server.entity;

/**
 * 班级公告（对应 keyou_e1.notice 表）。
 * createdAt 是已格式化的 "yyyy-MM-dd HH:mm" 文本，直接进 JSON，不需要额外序列化配置。
 */
public class Notice {

    private int id;
    private String title;
    private String content;
    private String author;
    private String createdAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
