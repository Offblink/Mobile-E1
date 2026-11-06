package com.offblink.keyou.server.entity;

/**
 * 班级群消息（对应 keyou_e1.chat_message 表）。
 * createdAt 是已格式化的 "yyyy-MM-dd HH:mm" 文本。
 */
public class ChatMessage {

    private int id;
    private String sender;
    private String content;
    private String createdAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
