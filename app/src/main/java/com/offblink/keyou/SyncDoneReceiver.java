package com.offblink.keyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 任务 4：自定义广播接收器（静态注册，AndroidManifest.xml）。
 *
 * 8.0+ 适配：隐式广播不再送达静态注册的接收器，发送方必须
 * intent.setPackage(...) 显式指定包名（见 LabActivity 的发送代码），
 * 显式广播仍可被 Manifest 接收器收到。
 */
public class SyncDoneReceiver extends BroadcastReceiver {

    private static final String TAG = "Keyou/Broadcast/Static";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (BroadcastActions.SYNC_DONE.equals(intent.getAction())) {
            String detail = intent.getStringExtra("detail");
            Log.i(TAG, "静态注册接收器收到自定义广播: " + detail);
        }
    }
}
