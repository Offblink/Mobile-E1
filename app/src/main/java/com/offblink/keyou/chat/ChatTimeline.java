package com.offblink.keyou.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 文本消息 + 文件卡片合并成一条时间线（纯逻辑，无 Android 依赖，单测直接跑）。
 *
 * 两边的时间都是服务端的 "yyyy-MM-dd HH:mm" 文本，字典序即时间序；
 * 同一分钟的条目用稳定排序：先放文本、再放文件 —— 顺序就是各自列表里的顺序，
 * 不会因为排序把同一分钟的消息搅乱。
 */
public final class ChatTimeline {

    private ChatTimeline() {
    }

    /** @param selfName 本机昵称：文件 uploader 等于它 → 卡片算「自己发的」，靠右显示 */
    public static List<ChatMessage> merge(List<ChatMessage> texts, List<ChatFile> files,
                                          String selfName) {
        List<ChatMessage> out = new ArrayList<>(
                (texts == null ? 0 : texts.size()) + (files == null ? 0 : files.size()));
        if (texts != null) {
            out.addAll(texts);
        }
        if (files != null) {
            for (ChatFile file : files) {
                boolean self = selfName != null && selfName.equals(file.getUploader());
                out.add(ChatMessage.ofFile(file, self));
            }
        }
        out.sort(Comparator.comparing(ChatMessage::getTime));
        return out;
    }
}
