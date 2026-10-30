package com.offblink.keyou;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 登录页：账号 / 密码校验、记住密码、注册入口（本地账号库，密码存 SHA-256 摘要）。
 *
 * 生命周期七个方法全部重写并打日志：正常进入 / Home 键 / 旋转屏 / finish() 四条路径
 * 都能在 Logcat（过滤 Keyou/Login）里看到完整序列。
 * 登录成功后用显式 Intent + Bundle 把 String / int / boolean / double 多种类型参数传给主界面。
 *
 * 登录策略：已注册账号校验密码；未注册账号直接放行（游客）。
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Login";

    /** SharedPreferences 文件名（/data/data/<pkg>/shared_prefs/login.xml） */
    private static final String SP_NAME = "login";
    private static final String SP_KEY_REMEMBER = "remember";
    private static final String SP_KEY_USERNAME = "username";
    private static final String SP_KEY_PASSWORD = "password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        AppLog.d(TAG, "onCreate");
        StartupSound.playOnceOnLaunch(this);

        EditText etUsername = findViewById(R.id.et_username);
        EditText etPassword = findViewById(R.id.et_password);
        android.widget.Switch swRemember = findViewById(R.id.sw_remember);

        // SharedPreferences 读取 —— 上次勾过记住密码就预填
        android.content.SharedPreferences sp = getSharedPreferences(SP_NAME, MODE_PRIVATE);
        if (sp.getBoolean(SP_KEY_REMEMBER, false)) {
            etUsername.setText(sp.getString(SP_KEY_USERNAME, ""));
            etPassword.setText(sp.getString(SP_KEY_PASSWORD, ""));
            swRemember.setChecked(true);
            AppLog.i(TAG, "SP 命中：已预填上次保存的账号");
        }

        Button btnLogin = findViewById(R.id.btn_login);
        Button btnRegister = findViewById(R.id.btn_register);

        btnLogin.setOnClickListener(v -> {
            // 空值校验 + Toast
            String username = etUsername.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, R.string.login_username_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            // 账号库校验 —— 已注册校验密码；未注册放行（游客），不挡演示动线
            String password = etPassword.getText().toString();
            if (UserStore.exists(this, username)) {
                if (!UserStore.check(this, username, password)) {
                    Toast.makeText(this, R.string.login_wrong_password, Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                Toast.makeText(this, R.string.login_guest, Toast.LENGTH_SHORT).show();
            }
            android.widget.CheckBox cbAgree = findViewById(R.id.cb_agree);
            if (!cbAgree.isChecked()) {
                Toast.makeText(this, R.string.login_agree_required, Toast.LENGTH_SHORT).show();
                return;
            }
            android.widget.RadioGroup rgRole = findViewById(R.id.rg_role);
            RadioButton rb = findViewById(rgRole.getCheckedRadioButtonId());
            String role = rb != null ? rb.getText().toString() : "";

            // SharedPreferences 写入 —— 记住密码（apply 异步落盘，无需等回调）
            android.content.SharedPreferences.Editor editor = sp.edit();
            if (swRemember.isChecked()) {
                editor.putBoolean(SP_KEY_REMEMBER, true)
                        .putString(SP_KEY_USERNAME, username)
                        .putString(SP_KEY_PASSWORD, password)
                        .apply();   // apply 异步写内存+磁盘；commit 同步返回布尔（思考题对比点）
            } else {
                editor.clear().apply();
            }

            // 显式 Intent + Bundle：多种类型数据一次传齐
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("username", username);
            bundle.putString("role", role);
            bundle.putInt("age", 20);
            bundle.putBoolean("isVip", true);
            bundle.putDouble("score", 99.5);
            intent.putExtras(bundle);
            AppLog.i(TAG, "登录: username=" + username + ", role=" + role);
            startActivity(intent);
        });

        // 注册 —— 弹窗表单 → 校验 → 写入 SharedPreferences 账号库（SHA-256 摘要）
        btnRegister.setOnClickListener(v -> showRegisterDialog(etUsername, etPassword));
    }

    /**
     * 注册弹窗：账号 / 密码 / 确认密码 → UserStore.register。
     * 用 AlertDialog 承载 dialog_register.xml，并覆写确认键
     * —— 默认点击即关闭，校验失败时用户会被踢出表单、已输入内容全丢。
     */
    private void showRegisterDialog(EditText etUsername, EditText etPassword) {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_register, null);
        EditText etName = form.findViewById(R.id.et_reg_username);
        EditText etPwd = form.findViewById(R.id.et_reg_password);
        EditText etConfirm = form.findViewById(R.id.et_reg_confirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.register_title)
                .setView(form)
                .setPositiveButton(R.string.register_ok, null)
                .setNegativeButton(R.string.common_cancel, null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String pwd = etPwd.getText().toString();
            if (name.isEmpty()) {
                toast(R.string.login_username_empty);
                return;
            }
            if (pwd.length() < 6) {
                toast(R.string.register_pwd_short);
                return;
            }
            if (!pwd.equals(etConfirm.getText().toString())) {
                toast(R.string.register_pwd_mismatch);
                return;
            }
            if (!UserStore.register(this, name, pwd)) {
                toast(R.string.register_exists);
                return;
            }
            // 回填登录框：注册完直接能点登录（演示动线少一步手输）
            etUsername.setText(name);
            etPassword.setText(pwd);
            AppLog.i(TAG, "注册成功: " + name);
            toast(R.string.register_success);
            dialog.dismiss();
        });
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppLog.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppLog.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppLog.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        AppLog.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLog.d(TAG, "onDestroy");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        AppLog.d(TAG, "onRestart");
    }
}
