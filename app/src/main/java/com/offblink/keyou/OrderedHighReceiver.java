package com.offblink.keyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 有序广播 —— 高优先级接收器（priority=100）。
 * 可调用 abortBroadcast() 截断，后续低优先级接收器收不到。
 */
public class OrderedHighReceiver extends BroadcastReceiver {

    private static final String TAG = "Keyou/Broadcast/OrderedHigh";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BroadcastActions.ORDERED_DEMO.equals(intent.getAction())) {
            return;
        }
        boolean abort = intent.getBooleanExtra("abort", false);
        Log.i(TAG, "高优先级接收器收到（priority=100）, abort=" + abort);
        if (abort) {
            // 截断：有序广播链条到此为止，低优先级接收器不会再收到
            abortBroadcast();
            Log.i(TAG, "已 abortBroadcast 截断，低优先级接收器应无日志");
        }
    }
}
