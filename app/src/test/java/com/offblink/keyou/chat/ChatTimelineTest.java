package com.offblink.keyou.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 时间线合并：文件卡片按时间插进消息里；同分钟保持文本在前；自己发的靠右 */
public class ChatTimelineTest {

    @Test
    public void fileAppearsAtItsUploadTime() {
        ChatFile earlier = new ChatFile("早发的.txt", 10, "电脑端", "2026-11-06 09:00", null);
        ChatFile later = new ChatFile("晚发的.txt", 20, "我", "2026-11-06 17:30", null);
        ChatMessage m1 = ChatMessage.text(1, "张三", "早上好", false, "2026-11-06 08:59");
        ChatMessage m2 = ChatMessage.text(2, "李四", "下午见", false, "2026-11-06 16:00");

        List<ChatMessage> merged = ChatTimeline.merge(
                Arrays.asList(m1, m2), Arrays.asList(earlier, later), "我");

        assertEquals(4, merged.size());
        assertEquals("早上好", merged.get(0).getContent());
        assertEquals("早发的.txt", merged.get(1).getFile().getName());
        assertEquals("下午见", merged.get(2).getContent());
        assertEquals("晚发的.txt", merged.get(3).getFile().getName());
    }

    @Test
    public void sameMinuteKeepsTextBeforeFiles() {
        ChatMessage m = ChatMessage.text(9, "张三", "同一分钟", false, "2026-11-06 10:00");
        ChatFile f = new ChatFile("同一分钟.zip", 1, "我", "2026-11-06 10:00", null);

        List<ChatMessage> merged = ChatTimeline.merge(Collections.singletonList(m),
                Collections.singletonList(f), "我");

        assertEquals("同一分钟", merged.get(0).getContent());
        assertTrue(merged.get(1).isFile());
    }

    @Test
    public void selfFlagComesFromUploader() {
        ChatFile mine = new ChatFile("mine.pdf", 1, "我", "2026-11-06 10:00", null);
        ChatFile theirs = new ChatFile("theirs.pdf", 1, "电脑端", "2026-11-06 10:00", null);

        List<ChatMessage> merged = ChatTimeline.merge(Collections.emptyList(),
                Arrays.asList(mine, theirs), "我");

        assertTrue(merged.get(0).isSelf());
        assertFalse(merged.get(1).isSelf());
        assertTrue(merged.get(0).isFile());
    }

    @Test
    public void emptyInputsYieldEmptyTimeline() {
        assertTrue(ChatTimeline.merge(Collections.emptyList(), Collections.emptyList(), "我").isEmpty());
        assertTrue(ChatTimeline.merge(null, null, "我").isEmpty());
    }
}
