package com.offblink.keyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 有序广播 —— 低优先级接收器（priority=0）。
 * 高优先级截断时本类不会被调用。
 */
public class OrderedLowReceiver extends BroadcastReceiver {

    private static final String TAG = "Keyou/Broadcast/OrderedLow";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (BroadcastActions.ORDERED_DEMO.equals(intent.getAction())) {
            Log.i(TAG, "低优先级接收器收到（priority=0）—— 说明未被截断");
        }
    }
}
