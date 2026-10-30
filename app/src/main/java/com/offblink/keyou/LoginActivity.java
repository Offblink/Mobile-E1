package com.offblink.keyou;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 2：FirstActivity 角色 —— 与 MainActivity 成对观察完整生命周期。
 * 任务 3：登录页 —— 四大布局 + 七控件 + 空值校验 Toast（指导书样例口径）。
 *
 * 任务 2 验收点：
 * 1. 重写全部生命周期方法并打日志（正常进入 / Home 键 / 旋转屏 / finish() 四种观察路径）；
 * 2. 显式 Intent 跳转 + Bundle 传递多种类型数据（String/int/boolean/double）。
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Login";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Log.d(TAG, "onCreate");

        EditText etUsername = findViewById(R.id.et_username);
        Button btnLogin = findViewById(R.id.btn_login);
        Button btnRegister = findViewById(R.id.btn_register);

        btnLogin.setOnClickListener(v -> {
            // 任务 3 样例：TextUtils.isEmpty 空值校验 + Toast
            String username = etUsername.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, R.string.login_username_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            android.widget.CheckBox cbAgree = findViewById(R.id.cb_agree);
            if (!cbAgree.isChecked()) {
                Toast.makeText(this, R.string.login_agree_required, Toast.LENGTH_SHORT).show();
                return;
            }
            android.widget.RadioGroup rgRole = findViewById(R.id.rg_role);
            RadioButton rb = findViewById(rgRole.getCheckedRadioButtonId());
            String role = rb != null ? rb.getText().toString() : "";

            // 显式 Intent + Bundle：多种类型数据一次传齐
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("username", username);
            bundle.putString("role", role);
            bundle.putInt("age", 20);
            bundle.putBoolean("isVip", true);
            bundle.putDouble("score", 99.5);
            intent.putExtras(bundle);
            Log.i(TAG, "登录: username=" + username + ", role=" + role);
            startActivity(intent);
        });

        // 注册按钮：本阶段仅演示点击事件（任务 5 接入持久化后扩展）
        btnRegister.setOnClickListener(v ->
                Toast.makeText(this, R.string.login_register_todo, Toast.LENGTH_SHORT).show());
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
