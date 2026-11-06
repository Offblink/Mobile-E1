package com.offblink.keyou.ui;

import com.offblink.keyou.R;
import com.offblink.keyou.log.AppLog;
import com.offblink.keyou.mine.UserStore;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 注册页：登录页之外的整页表单（原先是登录页里的 AlertDialog + dialog_register.xml）。
 *
 * 校验规则不变：账号非空 / 密码至少 6 位 / 两次一致 / 账号未注册；
 * 账号仍写本地 UserStore（SHA-256 摘要存 SharedPreferences）。
 * 区别在成功之后：直接走 {@link AuthFlow#enter} 进主界面，不回填登录框。
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Register";

    private static final int MIN_PASSWORD_LENGTH = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        AppLog.d(TAG, "onCreate");

        final EditText etName = findViewById(R.id.et_reg_username);
        final EditText etPwd = findViewById(R.id.et_reg_password);
        final EditText etConfirm = findViewById(R.id.et_reg_confirm);
        final RadioGroup rgRole = findViewById(R.id.rg_role);
        final Button btnRegister = findViewById(R.id.btn_register);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_back_login).setOnClickListener(v -> finish());

        btnRegister.setOnClickListener(v -> submit(etName, etPwd, etConfirm, rgRole, btnRegister));

        // 确认密码这一行按「完成」= 直接提交
        etConfirm.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit(etName, etPwd, etConfirm, rgRole, btnRegister);
                return true;
            }
            return false;
        });
    }

    private void submit(EditText etName, EditText etPwd, EditText etConfirm,
                        RadioGroup rgRole, Button btnRegister) {
        if (!btnRegister.isEnabled()) {
            return;   // 已经在进主界面的路上，别重复提交
        }
        String name = etName.getText().toString().trim();
        String pwd = etPwd.getText().toString();
        if (name.isEmpty()) {
            toast(R.string.login_username_empty);
            return;
        }
        if (pwd.length() < MIN_PASSWORD_LENGTH) {
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

        RadioButton rb = findViewById(rgRole.getCheckedRadioButtonId());
        String role = rb != null ? rb.getText().toString() : "";
        AppLog.i(TAG, "注册成功: " + name + "，角色 " + role);
        toast(R.string.register_success);

        btnRegister.setEnabled(false);
        AuthFlow.enter(this, name, role);
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
