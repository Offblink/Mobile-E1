package com.offblink.keyou;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 任务 8：后台文件下载 —— 前台服务 + 进度通知。
 *
 * 验收点：
 * - Service 生命周期（onCreate / onStartCommand / onDestroy，Log 可观察）；
 * - startService 启动后台下载，进度经通知栏展示；
 * - Android 8.0+ 后台服务限制：长时间任务必须 startForeground（否则
 *   BackgroundServiceStartNotAllowedException / IllegalStateException）；
 * - POST_NOTIFICATIONS 运行时权限（Android 13+，见 DownloadActivity）。
 *
 * 指导书提到 IntentService 已废弃：等价现代替代是 JobIntentService/WorkManager，
 * 本类用「普通 Service + 前台通知」演示更直观的生命周期教学口径。
 */
public class DownloadService extends Service {

    private static final String TAG = "Keyou/DownloadService";

    public static final String CHANNEL_ID = "download_channel";
    public static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_PROGRESS = "com.offblink.keyou.DOWNLOAD_PROGRESS";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_DONE = "done";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        // 8.0+ 关键：先 startForeground 占位，再做耗时下载
        startForeground(NOTIFICATION_ID, buildNotification(0, false));
        if (!running && intent != null) {
            String path = intent.getStringExtra("path");
            String fileName = intent.getStringExtra("fileName");
            if (path != null && fileName != null) {
                running = true;
                executor.execute(() -> doDownload(path, fileName));
            }
        }
        // START_NOT_STICKY：被杀后不自动重建（下载任务语义）
        return START_NOT_STICKY;
    }

    /** 依次尝试当前地址与备用地址（AVD 的 App 侧网络栈异常时靠回退保底） */
    private void doDownload(String path, String fileName) {
        java.util.List<String> bases = new java.util.ArrayList<>();
        bases.add(ServerConfig.base(this));
        String alt = ServerConfig.fallback(this);
        if (alt != null) {
            bases.add(alt);
        }
        Exception last = null;
        for (String base : bases) {
            try {
                long bytes = downloadOnce(base + path, fileName);
                ServerConfig.remember(this, base);
                Log.i(TAG, "下载完成: " + fileName + " (" + bytes + "B) from " + base);
                updateDone("已保存到 files/" + fileName + "（" + bytes + " 字节）");
                finishDownload();
                return;
            } catch (Exception e) {
                last = e;
                Log.w(TAG, "从 " + base + " 下载失败: " + e.getMessage());
            }
        }
        Log.e(TAG, "下载失败", last);
        updateDone("下载失败: " + (last != null ? last.getMessage() : "未知错误"));
        finishDownload();
    }

    /** 单次下载，返回写入字节数 */
    private long downloadOnce(String url, String fileName) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            int total = conn.getContentLength();
            byte[] buf = new byte[8192];
            long downloaded = 0;
            int lastPercent = 0;
            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = openFileOutput(fileName, MODE_PRIVATE)) {
                int len;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                    downloaded += len;
                    if (total > 0) {
                        int percent = (int) (downloaded * 100 / total);
                        if (percent != lastPercent && percent % 5 == 0) {
                            lastPercent = percent;
                            updateProgress(percent);
                        }
                    }
                }
            }
            return downloaded;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void finishDownload() {
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** 更新通知进度 + 广播给 Activity（本地显式广播） */
    private void updateProgress(int percent) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(percent, false));
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PROGRESS, percent);
        sendBroadcast(intent);
    }

    private void updateDone(String message) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(100, true, message));
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_DONE, message);
        sendBroadcast(intent);
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "文件下载", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("后台下载进度通知");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private android.app.Notification buildNotification(int percent, boolean done) {
        return buildNotification(percent, done, null);
    }

    private android.app.Notification buildNotification(int percent, boolean done,
                                                       String contentText) {
        Intent open = new Intent(this, DownloadActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(done ? "下载完成" : "正在下载…")
                .setContentIntent(pi)
                .setOngoing(!done)
                .setOnlyAlertOnce(true);
        if (contentText != null) {
            builder.setContentText(contentText);
        } else {
            builder.setProgress(100, percent, false)
                    .setContentText(percent + "%");
        }
        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // 任务 8 另一半：绑定式通信见 BoundNoteService（本下载服务是启动式）
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        Log.d(TAG, "onDestroy");
    }
}
