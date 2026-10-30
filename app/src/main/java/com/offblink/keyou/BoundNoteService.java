package com.offblink.keyou;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 绑定式 Service —— BoundNoteService。
 * 与启动式 DownloadService 对比：
 * - 启动式：与调用方无连接，startService/stopService，适合"发出去就不管"的任务；
 * - 绑定式：bindService 拿 IBinder 直接调服务方法，unbindService 断开，
 *   适合"要拿返回值/持续交互"的场景；Service 跟随最后一个客户端解绑销毁。
 */
public class BoundNoteService extends Service {

    private static final String TAG = "Keyou/BoundNote";

    /** Binder：把服务实例交给客户端（LocalBinder 模式，同进程） */
    public class LocalBinder extends Binder {
        public BoundNoteService getService() {
            return BoundNoteService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(TAG, "onCreate");
    }

    @Override
    public IBinder onBind(Intent intent) {
        AppLog.d(TAG, "onBind: 客户端绑定");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        AppLog.d(TAG, "onUnbind: 客户端解绑");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLog.d(TAG, "onDestroy");
    }

    /** 客户端直接调用的方法：生成一条带时间戳的班级便签 */
    public String makeNote(String text) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        AppLog.i(TAG, "makeNote: " + text);
        return "[" + time + "] " + text;
    }
}
