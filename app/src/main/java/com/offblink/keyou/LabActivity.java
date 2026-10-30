package com.offblink.keyou;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 实验室：收纳指导书中「观察 / 对比」性质的演示入口。
 *
 * 任务 2 验收点：四种启动模式对比（4 个 Manifest 变体 Activity）、隐式 Intent。
 * 任务 4/6/7 的演示入口后续在此追加（有序广播、JSON 双解析等）。
 */
public class LabActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Lab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lab);
        Log.d(TAG, "onCreate");

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
    }

    private void launch(String mode, Class<?> target) {
        Log.i(TAG, "启动模式演示: " + mode + " -> " + target.getSimpleName());
        startActivity(new Intent(this, target));
    }
}
