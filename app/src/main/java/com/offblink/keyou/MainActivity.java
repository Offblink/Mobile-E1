package com.offblink.keyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 2：SecondActivity 角色 —— 接收 Bundle 参数 + 用 registerForActivityResult 回传。
 * 任务 4：动态注册广播接收器监听网络变化（onDestroy 注销，防内存泄漏）。
 *
 * 任务 2 验收点：
 * 1. 与 LoginActivity 成对的完整生命周期日志；
 * 2. 接收 Bundle 多类型参数并展示；
 * 3. registerForActivityResult 接收「编辑昵称」页回传的结果（现代结果 API，非 startActivityForResult）。
 *
 * 任务 4 验收点（本页承担的部分）：
 * - 动态注册 CONNECTIVITY_CHANGE 接收器 + onDestroy 中 unregisterReceiver；
 * - Android 8.0+ 适配说明：隐式广播已受限，运行时注册的接收器仍可收到，
 *   且 onReceive 里用现代 API（getNetworkCapabilities）判断真实连通性。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Main";

    private TextView tvWelcome;
    private TextView tvNetBanner;
    private String username = "同学";

    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver networkReceiver;

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
        tvNetBanner = findViewById(R.id.tv_net_banner);

        // registerForActivityResult 回传：编辑昵称
        Button btnEditNickname = findViewById(R.id.btn_edit_nickname);
        btnEditNickname.setOnClickListener(v -> {
            Intent i = new Intent(this, EditNicknameActivity.class);
            i.putExtra("nickname", username);
            editNicknameLauncher.launch(i);
        });

        findViewById(R.id.btn_contacts).setOnClickListener(v ->
                startActivity(new Intent(this, ContactListActivity.class)));
        findViewById(R.id.btn_chat).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));
        findViewById(R.id.btn_draft).setOnClickListener(v ->
                startActivity(new Intent(this, DraftActivity.class)));
        findViewById(R.id.btn_tools).setOnClickListener(v ->
                startActivity(new Intent(this, ToolsActivity.class)));
        findViewById(R.id.btn_download).setOnClickListener(v ->
                startActivity(new Intent(this, DownloadActivity.class)));
        findViewById(R.id.btn_lab).setOnClickListener(v ->
                startActivity(new Intent(this, LabActivity.class)));
        findViewById(R.id.btn_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        registerNetworkReceiver();
    }

    /**
     * 任务 4：动态注册网络变化广播接收器。
     * 必须与 onDestroy 里的 unregisterReceiver 成对，否则 Activity 销毁后接收器泄漏。
     */
    private void registerNetworkReceiver() {
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // CONNECTIVITY_ACTION 只是触发信号，真实连通性要自己查（广播可能连通/断开都发）
                boolean connected = isCurrentlyConnected();
                Log.i(TAG, "收到网络变化广播, connected=" + connected);
                showBanner(getString(connected
                        ? R.string.banner_net_on : R.string.banner_net_off));
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
        Log.d(TAG, "已动态注册网络变化接收器");
    }

    /** 现代 API 判断连通性（getActiveNetworkInfo 已废弃） */
    private boolean isCurrentlyConnected() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    /** 横幅提示 3 秒后自动隐藏 */
    private void showBanner(String text) {
        tvNetBanner.setText(text);
        tvNetBanner.setVisibility(TextView.VISIBLE);
        bannerHandler.removeCallbacksAndMessages(null);
        bannerHandler.postDelayed(() -> tvNetBanner.setVisibility(TextView.GONE), 3000);
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
        // 任务 4：动态注册的接收器必须在这里注销（指导书排错点：内存泄漏警告）
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
            Log.d(TAG, "已注销网络变化接收器");
        }
        bannerHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }
}
