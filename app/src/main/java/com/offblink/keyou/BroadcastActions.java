package com.offblink.keyou;

/**
 * 任务 4：自定义广播的 action 常量集中处（发送方与接收方共用，防止拼错）。
 */
public final class BroadcastActions {

    /** 同步完成广播（普通广播，静态 + 动态各一个接收器） */
    public static final String SYNC_DONE = "com.offblink.keyou.SYNC_DONE";

    /** 有序广播演示（priority 高低 + abortBroadcast 截断） */
    public static final String ORDERED_DEMO = "com.offblink.keyou.ORDERED_DEMO";

    private BroadcastActions() {
    }
}
