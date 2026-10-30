package com.offblink.keyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * 任务 8：下载中心页。
 * - startService 启动前台下载服务（server /api/mates 作为演示下载目标太小，
 *   用 /files/demo-note.txt 静态文件端点）；
 * - POST_NOTIFICATIONS 运行时权限（Android 13+ 演示）；
 * - 动态接收进度广播刷新进度条（onDestroy 注销）。
 */
public class DownloadActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Download";

    private ProgressBar progressBar;
    private TextView tvStatus;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DownloadService.ACTION_PROGRESS.equals(intent.getAction())) {
                if (intent.hasExtra(DownloadService.EXTRA_DONE)) {
                    tvStatus.setText(intent.getStringExtra(DownloadService.EXTRA_DONE));
                    progressBar.setProgress(100);
                } else {
                    int p = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0);
                    progressBar.setProgress(p);
                    tvStatus.setText(p + "%");
                }
            }
        }
    };

    /** POST_NOTIFICATIONS 运行时权限（Android 13+；低版本自动 granted） */
    private final ActivityResultLauncher<String> notifyPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                if (granted) {
                    startDownload();
                } else {
                    // 无通知权限仍可下载，只是看不到进度通知
                    Toast.makeText(this, R.string.notify_permission_denied,
                            Toast.LENGTH_LONG).show();
                    startDownload();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        Log.d(TAG, "onCreate");

        progressBar = findViewById(R.id.progress_download);
        tvStatus = findViewById(R.id.tv_download_status);

        Button btnStart = findViewById(R.id.btn_start_download);
        btnStart.setOnClickListener(v -> requestAndDownload());

        registerReceiver(progressReceiver,
                new IntentFilter(DownloadService.ACTION_PROGRESS),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void requestAndDownload() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startDownload();
        }
    }

    private void startDownload() {
        progressBar.setProgress(0);
        tvStatus.setText(R.string.download_starting);
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra("url", JsonCompareActivity.BASE_URL + "/files/demo-note.txt");
        intent.putExtra("fileName", "demo-note.txt");
        // ContextCompat.startForegroundService：8.0+ 要求 5s 内 startForeground
        ContextCompat.startForegroundService(this, intent);
        Log.i(TAG, "已启动下载服务");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(progressReceiver);
        Log.d(TAG, "onDestroy");
    }
}
