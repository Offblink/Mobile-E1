package com.offblink.keyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 实验室：收纳指导书中「观察 / 对比」性质的演示入口。
 *
 * 任务 2 验收点：四种启动模式对比（4 个 Manifest 变体 Activity）、隐式 Intent。
 * 任务 4 验收点：自定义广播（静态 vs 动态对比）、有序广播 priority 与 abortBroadcast 截断。
 * 任务 7 的演示入口后续在此追加（JSON 双解析等）。
 */
public class LabActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Lab";

    /** 动态接收器：与 Manifest 静态注册的 SyncDoneReceiver 收同一 action，用于对比 */
    private final BroadcastReceiver dynamicSyncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("Keyou/Broadcast/Dynamic",
                    "动态注册接收器收到自定义广播: " + intent.getStringExtra("detail"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lab);
        Log.d(TAG, "onCreate");

        // 任务 4：动态注册同一 action，对比静态/动态两个接收器都收到
        IntentFilter syncFilter = new IntentFilter(BroadcastActions.SYNC_DONE);
        registerReceiver(dynamicSyncReceiver, syncFilter);

        // 四种启动模式：同一段启动代码，Manifest 里 launchMode 不同，观察任务栈实例数
        Button btnStandard = findViewById(R.id.btn_mode_standard);
        btnStandard.setOnClickListener(v -> launch("standard", StandardLabActivity.class));

        Button btnSingleTop = findViewById(R.id.btn_mode_single_top);
        btnSingleTop.setOnClickListener(v -> launch("singleTop", SingleTopLabActivity.class));

        Button btnSingleTask = findViewById(R.id.btn_mode_single_task);
        btnSingleTask.setOnClickListener(v -> launch("singleTask", SingleTaskLabActivity.class));

        Button btnSingleInstance = findViewById(R.id.btn_mode_single_instance);
        btnSingleInstance.setOnClickListener(v -> launch("singleInstance", SingleInstanceLabActivity.class));

        // 隐式 Intent：与显式 Intent 对比（不需要知道目标类名，按 Action/Category 匹配）
        Button btnImplicit = findViewById(R.id.btn_implicit_intent);
        btnImplicit.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com"));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "没有可处理 https 的应用", e);
            }
        });

        // 任务 7：JSON 双解析对比 + RSS 解析
        findViewById(R.id.btn_json_compare).setOnClickListener(v ->
                startActivity(new Intent(this, JsonCompareActivity.class)));
        findViewById(R.id.btn_rss_parse).setOnClickListener(v ->
                startActivity(new Intent(this, RssParseActivity.class)));

        // 任务 4：发送自定义广播 —— setPackage 显式指定（8.0+ 静态接收器只收显式广播）
        Button btnCustom = findViewById(R.id.btn_send_custom);
        btnCustom.setOnClickListener(v -> {
            Intent intent = new Intent(BroadcastActions.SYNC_DONE);
            intent.setPackage(getPackageName());   // 8.0+ 适配关键：显式广播
            intent.putExtra("detail", "同学录同步完成（共 8 条）");
            sendBroadcast(intent);
            Log.i(TAG, "已发送自定义广播（静态+动态接收器各应有 1 条日志）");
            Toast.makeText(this, R.string.broadcast_custom_sent, Toast.LENGTH_SHORT).show();
        });

        // 任务 4：有序广播不截断 —— 高(priority=100) → 低(priority=0) 都收到
        Button btnOrdered = findViewById(R.id.btn_send_ordered);
        btnOrdered.setOnClickListener(v -> {
            Intent intent = new Intent(BroadcastActions.ORDERED_DEMO);
            intent.setPackage(getPackageName());
            intent.putExtra("abort", false);
            sendOrderedBroadcast(intent, null);
            Log.i(TAG, "已发送有序广播（不截断）：Logcat 应先 OrderedHigh 后 OrderedLow");
            Toast.makeText(this, R.string.broadcast_ordered_sent, Toast.LENGTH_SHORT).show();
        });

        // 任务 4：有序广播 + 截断 —— 高优先级 abortBroadcast 后低优先级收不到
        Button btnAbort = findViewById(R.id.btn_send_abort);
        btnAbort.setOnClickListener(v -> {
            Intent intent = new Intent(BroadcastActions.ORDERED_DEMO);
            intent.setPackage(getPackageName());
            intent.putExtra("abort", true);
            sendOrderedBroadcast(intent, null);
            Log.i(TAG, "已发送有序广播（截断）：Logcat 应只有 OrderedHigh，无 OrderedLow");
            Toast.makeText(this, R.string.broadcast_abort_sent, Toast.LENGTH_SHORT).show();
        });
    }

    private void launch(String mode, Class<?> target) {
        Log.i(TAG, "启动模式演示: " + mode + " -> " + target.getSimpleName());
        startActivity(new Intent(this, target));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 动态接收器成对注销
        unregisterReceiver(dynamicSyncReceiver);
        Log.d(TAG, "已注销动态广播接收器");
    }
}
