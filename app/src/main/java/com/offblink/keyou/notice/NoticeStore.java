package com.offblink.keyou.notice;

import android.content.Context;

/**
 * 公告已读位置：只记「最后看过的最大 id」，比它大的都算未读。
 *
 * 为什么不用「每条一个已读标记」：公告列表会整页展示全部内容，
 * 打开一次就等于全看过；一个游标足够，也不会随条数无限膨胀。
 */
public final class NoticeStore {

    private static final String SP_NAME = "notices";
    private static final String KEY_LAST_SEEN = "last_seen_id";

    private NoticeStore() {
    }

    public static long lastSeenId(Context ctx) {
        return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SEEN, 0L);
    }

    /** 只前进不后退：并发/乱序拉取时不会被旧列表把游标拉回去 */
    public static void markSeen(Context ctx, long id) {
        if (id <= lastSeenId(ctx)) {
            return;
        }
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SEEN, id).apply();
    }
}
