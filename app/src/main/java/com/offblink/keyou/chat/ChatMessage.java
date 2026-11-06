package com.offblink.keyou.chat;

/**
 * 聊天时间线的一项：文本消息或文件卡片。
 *
 * - 文本：id / sender / content / createdAt 全部来自服务端；
 * - 文件：file != null，time 取 uploadedAt —— 与文本消息同一个 createdAt 口径（yyyy-MM-dd HH:mm），
 *   时间线按这个字符串排序，文件就"像消息一样"插在对应时间点。
 * isSelf 决定气泡/卡片靠哪边：文本看 sender，文件看 uploader。
 */
public class ChatMessage {

    private final long id;
    private final String sender;
    private final String content;
    private final boolean self;
    private final String time;
    private final ChatFile file;

    private ChatMessage(long id, String sender, String content, boolean self,
                        String time, ChatFile file) {
        this.id = id;
        this.sender = sender;
        this.content = content;
        this.self = self;
        this.time = time == null ? "" : time;
        this.file = file;
    }

    public static ChatMessage text(long id, String sender, String content, boolean self, String time) {
        return new ChatMessage(id, sender, content, self, time, null);
    }

    public static ChatMessage ofFile(ChatFile file, boolean self) {
        return new ChatMessage(-1, file.getUploader(), file.getName(), self,
                file.getUploadedAt(), file);
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

    public String getTime() {
        return time;
    }

    public ChatFile getFile() {
        return file;
    }

    public boolean isFile() {
        return file != null;
    }
}
