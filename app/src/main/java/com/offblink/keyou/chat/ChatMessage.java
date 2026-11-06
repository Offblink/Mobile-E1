package com.offblink.keyou.chat;

/**
 * 班级群消息：isSelf 区分左右气泡。
 * id / sender 来自服务端（id 用于增量拉取游标，sender 显示在气泡上方）。
 */
public class ChatMessage {

    private final long id;
    private final String sender;
    private final String content;
    private final boolean self;

    public ChatMessage(long id, String sender, String content, boolean self) {
        this.id = id;
        this.sender = sender;
        this.content = content;
        this.self = self;
    }

    public long getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public boolean isSelf() {
        return self;
    }
}
