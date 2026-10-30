package com.offblink.keyou;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 2：SecondActivity 角色 —— 接收 Bundle 参数 + 用 registerForActivityResult 回传。
 *
 * 验收点：
 * 1. 与 LoginActivity 成对的完整生命周期日志；
 * 2. 接收 Bundle 多类型参数并展示；
 * 3. registerForActivityResult 接收「编辑昵称」页回传的结果（现代结果 API，非 startActivityForResult）。
 *
 * P9 起本页换底部胶囊导航（nav-pill-bar），按钮届时迁入对应 Tab。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Main";

    private TextView tvWelcome;
    private String username = "同学";

    /** 结果 API：注册在 onCreate，生命周期内保持有效 */
    private final ActivityResultLauncher<Intent> editNicknameLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String nickname = result.getData().getStringExtra("nickname");
                    if (nickname != null && !nickname.isEmpty()) {
                        username = nickname;
                        tvWelcome.setText("欢迎回来，" + username);
                        Log.i(TAG, "收到回传昵称: " + nickname);
                    }
                } else {
                    Log.w(TAG, "回传结果为空（未 setResult 就 finish 或按了返回）");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.v(TAG, "onCreate: verbose 日志");
        Log.d(TAG, "onCreate: debug 日志");
        Log.i(TAG, "onCreate: info 日志");
        Log.w(TAG, "onCreate: warn 日志");
        Log.e(TAG, "onCreate: error 日志（演示用，非真实错误）");

        // 接收登录页 Bundle 多类型参数
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            username = intent.getStringExtra("username");
            Log.i(TAG, "收到 Bundle: username=" + username
                    + ", age=" + intent.getIntExtra("age", -1)
                    + ", isVip=" + intent.getBooleanExtra("isVip", false)
                    + ", score=" + intent.getDoubleExtra("score", 0));
        }

        tvWelcome = findViewById(R.id.tv_welcome);
        tvWelcome.setText("欢迎回来，" + username);

        // registerForActivityResult 回传：编辑昵称
        Button btnEditNickname = findViewById(R.id.btn_edit_nickname);
        btnEditNickname.setOnClickListener(v -> {
            Intent i = new Intent(this, EditNicknameActivity.class);
            i.putExtra("nickname", username);
            editNicknameLauncher.launch(i);
        });

        findViewById(R.id.btn_lab).setOnClickListener(v ->
                startActivity(new Intent(this, LabActivity.class)));
        findViewById(R.id.btn_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }
}
