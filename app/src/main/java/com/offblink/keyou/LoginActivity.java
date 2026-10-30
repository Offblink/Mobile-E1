package com.offblink.keyou;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 2：FirstActivity 角色 —— 与 MainActivity 成对观察完整生命周期。
 *
 * 验收点：
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
        btnLogin.setOnClickListener(v -> {
            // 显式 Intent + Bundle：多种类型数据一次传齐
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("username", etUsername.getText().toString().trim());
            bundle.putInt("age", 20);
            bundle.putBoolean("isVip", true);
            bundle.putDouble("score", 99.5);
            intent.putExtras(bundle);
            startActivity(intent);
        });
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
