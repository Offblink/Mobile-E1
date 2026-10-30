package com.offblink.keyou;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 1：关于页 + Log 自检。
 *
 * 验收点：Log 分级输出（v/d/i/w/e）与 Logcat 过滤。
 * 约定：全 App TAG 统一 "Keyou/xxx"，Logcat 过滤框输入 Keyou 即可看到全部页面日志；
 * 输入 Keyou/Login 可单独看登录页，这就是 Logcat 的 TAG 过滤用法。
 */
public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/About";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        Log.d(TAG, "onCreate");

        Button btnLogCheck = findViewById(R.id.btn_log_check);
        btnLogCheck.setOnClickListener(v -> {
            Log.v(TAG, "Log 自检: verbose —— 最细粒度，生产默认关闭");
            Log.d(TAG, "Log 自检: debug —— 调试信息，开发期用");
            Log.i(TAG, "Log 自检: info —— 关键节点（登录、下单）");
            Log.w(TAG, "Log 自检: warn —— 可恢复的异常");
            Log.e(TAG, "Log 自检: error —— 错误（演示用，非真实错误）");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
