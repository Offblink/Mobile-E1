package com.offblink.keyou;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 启动模式演示的公共基类：页面显示本实例身份（hashCode），
 * 反复启动自己/跳主页，观察任务栈里实例数量与 onNewIntent 触发情况。
 * 四个子类的差异只在 AndroidManifest.xml 的 android:launchMode。
 */
public abstract class LaunchModeLabActivity extends AppCompatActivity {

    private TextView tvInstance;

    /** 子类提供日志标签，如 "Keyou/Mode/standard" */
    protected abstract String modeTag();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_mode);
        Log.d(modeTag(), "onCreate 实例=" + System.identityHashCode(this));

        tvInstance = findViewById(R.id.tv_instance);
        render();

        // 再次启动自己：standard 每次新建实例；singleTop 复用并走 onNewIntent；
        // singleTask/singleInstance 清掉上方页面后复用
        findViewById(R.id.btn_relaunch_self).setOnClickListener(v -> {
            Log.i(modeTag(), "再次启动自己，当前栈内实例=" + System.identityHashCode(this));
            startActivity(new Intent(this, getClass()));
        });

        // 跳回登录页（finishes 本页上方所有页面，便于观察 singleTask/singleInstance 的清栈行为）
        findViewById(R.id.btn_goto_login).setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // singleTop / singleTask / singleInstance 复用已有实例时走这里，而不是 onCreate
        Log.i(modeTag(), "onNewIntent 复用实例=" + System.identityHashCode(this));
        setIntent(intent);
        render();
    }

    private void render() {
        tvInstance.setText(getString(R.string.launch_mode_instance,
                getClass().getSimpleName(),
                Integer.toHexString(System.identityHashCode(this))));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(modeTag(), "onDestroy 实例=" + System.identityHashCode(this));
    }
}
