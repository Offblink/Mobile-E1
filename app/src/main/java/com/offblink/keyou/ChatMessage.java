package com.offblink.keyou;

/**
 * 聊天消息：isSelf 区分左右气泡。
 */
public class ChatMessage {

    private final String content;
    private final boolean self;

    public ChatMessage(String content, boolean self) {
        this.content = content;
        this.self = self;
    }

    public String getContent() {
        return content;
    }

    public boolean isSelf() {
        return self;
    }
}
