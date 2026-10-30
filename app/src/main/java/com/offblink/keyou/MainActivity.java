package com.offblink.keyou;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 课友 App 入口。
 *
 * 任务 1 验收点：Log 分级输出（v/d/i/w/e），Logcat 按 TAG 过滤查看。
 * 约定：全 App 统一 TAG 前缀 "Keyou/"，Logcat 过滤 "Keyou" 即可看到所有页面日志。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Main";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.v(TAG, "onCreate: verbose 日志");
        Log.d(TAG, "onCreate: debug 日志");
        Log.i(TAG, "onCreate: info 日志");
        Log.w(TAG, "onCreate: warn 日志");
        Log.e(TAG, "onCreate: error 日志（演示用，非真实错误）");
    }
}
