package com.offblink.keyou;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 关于页 + Log 自检。
 *
 * 验收点：Log 分级输出（v/d/i/w/e）与 Logcat 过滤。
 * 约定：全 App TAG 统一 "Keyou/xxx"，Logcat 过滤框输入 Keyou 即可看到全部页面日志；
 * 输入 Keyou/About 可单独看本页 —— 这就是 Logcat 的 TAG 过滤用法。
 *
 * 交互反馈：日志本来只进 Logcat，屏幕上毫无变化，容易被误认为"点了没反应"。
 * 因此点击后同时在屏上回显五条日志摘要 + Toast 提示，方便现场演示与自检。
 */
public class AboutActivity extends KeyouActivity {

    private static final String TAG = "Keyou/About";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        Log.d(TAG, "onCreate");

        TextView tvOutput = findViewById(R.id.tv_log_output);
        findViewById(R.id.btn_log_check).setOnClickListener(v -> {
            String[] lines = {
                    "V  verbose —— 最细粒度，生产默认关闭",
                    "D  debug   —— 调试信息，开发期用",
                    "I  info    —— 关键节点（登录、下单）",
                    "W  warn    —— 可恢复的异常",
                    "E  error   —— 错误（演示用，非真实错误）"
            };
            Log.v(TAG, "Log 自检: verbose —— 最细粒度，生产默认关闭");
            Log.d(TAG, "Log 自检: debug —— 调试信息，开发期用");
            Log.i(TAG, "Log 自检: info —— 关键节点（登录、下单）");
            Log.w(TAG, "Log 自检: warn —— 可恢复的异常");
            Log.e(TAG, "Log 自检: error —— 错误（演示用，非真实错误）");

            StringBuilder sb = new StringBuilder();
            sb.append("TAG: ").append(TAG).append("（Logcat 过滤 Keyou/About）\n");
            for (String line : lines) {
                sb.append(line).append('\n');
            }
            tvOutput.setText(sb.toString().trim());
            Toast.makeText(this, R.string.about_log_emitted, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
