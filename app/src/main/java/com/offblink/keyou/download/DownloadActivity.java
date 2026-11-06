package com.offblink.keyou.download;

import com.offblink.keyou.R;
import com.offblink.keyou.log.AppLog;
import com.offblink.keyou.net.HttpUtil;
import com.offblink.keyou.ui.KeyouActivity;
import com.offblink.keyou.util.Sizes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 群文件页。
 * - 列表来自服务端 `GET /api/files`（电脑端客户端上传的文件就出现在这里）；
 * - 点条目启动前台服务下载 `GET /api/file/{name}`，进度走广播刷新；
 * - POST_NOTIFICATIONS 运行时权限（Android 13+ 演示）。
 */
public class DownloadActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Download";

    private LinearLayout fileList;
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
                if (!granted) {
                    // 无通知权限仍可下载，只是看不到进度通知
                    Toast.makeText(this, R.string.notify_permission_denied,
                            Toast.LENGTH_LONG).show();
                }
                downloadPending();
            });

    /** 权限回调后要下载的文件名 */
    private String pendingFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        AppLog.d(TAG, "onCreate");

        fileList = findViewById(R.id.ll_files);
        progressBar = findViewById(R.id.progress_download);
        tvStatus = findViewById(R.id.tv_download_status);

        findViewById(R.id.btn_start_download).setOnClickListener(v -> loadFiles());

        // 33+ 需显式声明不导出；低版本传 0 走等价的两参重载
        registerReceiver(progressReceiver,
                new IntentFilter(DownloadService.ACTION_PROGRESS),
                Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);

        loadFiles();
    }

    // ---------- 列表 ----------

    private void loadFiles() {
        tvStatus.setText(R.string.download_loading);
        HttpUtil.get(this, "/api/files", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                renderFiles(body);
            }

            @Override
            public void onError(String message, Exception e) {
                tvStatus.setText(getString(R.string.json_fetch_failed, message));
                AppLog.w(TAG, "群文件列表失败: " + message);
            }
        });
    }

    private void renderFiles(String body) {
        fileList.removeAllViews();
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", -1) != 0) {
                tvStatus.setText(root.optString("message"));
                return;
            }
            JSONArray array = root.optJSONArray("data");
            int count = array == null ? 0 : array.length();
            if (count == 0) {
                tvStatus.setText(R.string.download_empty);
                return;
            }
            tvStatus.setText(getString(R.string.download_count, count));
            for (int i = 0; i < count; i++) {
                JSONObject item = array.getJSONObject(i);
                String name = item.optString("name", "");
                String uploader = item.optString("uploader", "");
                String time = item.optString("uploadedAt", "");
                long size = item.optLong("size", 0);
                fileList.addView(buildRow(name, uploader + " · " + time + " · " + Sizes.human(size), name));
            }
        } catch (Exception e) {
            tvStatus.setText(R.string.download_parse_error);
            AppLog.e(TAG, "群文件解析失败", e);
        }
    }

    private View buildRow(String title, String subtitle, String fileName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setClickable(true);

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(15);
        name.setTextColor(getColor(R.color.md_on_surface));
        row.addView(name);

        TextView meta = new TextView(this);
        meta.setText(subtitle);
        meta.setTextSize(12);
        meta.setTextColor(getColor(R.color.md_on_surface_variant));
        row.addView(meta);

        row.setOnClickListener(v -> requestDownload(fileName));
        return row;
    }

    // ---------- 下载 ----------

    private void requestDownload(String fileName) {
        pendingFile = fileName;
        requestAndDownload();
    }

    private void requestAndDownload() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } else {
            downloadPending();
        }
    }

    private void downloadPending() {
        if (pendingFile == null) {
            return;
        }
        progressBar.setProgress(0);
        tvStatus.setText(R.string.download_starting);
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra("path", "/api/file/" + pendingFile);
        intent.putExtra("fileName", pendingFile);
        ContextCompat.startForegroundService(this, intent);
        AppLog.i(TAG, "已启动下载服务: " + pendingFile);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(progressReceiver);
        AppLog.d(TAG, "onDestroy");
    }
}
