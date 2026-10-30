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
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

/**
 * 任务 2：SecondActivity 角色 —— 接收 Bundle 参数 + 用 registerForActivityResult 回传。
 * 任务 4：动态注册广播接收器监听网络变化（onDestroy 注销，防内存泄漏）。
 * UI：m3 胶囊导航（nav-pill-bar 落地配方）—— 底条 4 格 + 弹射 pill + 平移切页。
 *
 * 动效双轨（mobile-design 约定）：位移走 spring（OvershootInterpolator 0.7 / 350ms），
 * 颜色/透明度直切不回弹 —— 绝不共用一个动画对象。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Main";

    private TextView tvWelcome;
    private TextView tvNetBanner;
    private String username = "同学";

    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver networkReceiver;

    // ---------- m3 胶囊导航状态 ----------
    private View navBar;
    private View navPill;
    private View[] pages;
    private TextView[] labelsOn;
    private TextView[] labelsOff;
    private int currentTab = 0;
    private boolean switching = false;

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

        setupNav();
        wirePageButtons();
        loadStats();

        // registerForActivityResult 回传：编辑昵称
        findViewById(R.id.btn_edit_nickname).setOnClickListener(v -> {
            Intent i = new Intent(this, EditNicknameActivity.class);
            i.putExtra("nickname", username);
            editNicknameLauncher.launch(i);
        });

        registerNetworkReceiver();
    }

    // ---------- m3 胶囊导航 ----------

    private void setupNav() {
        navBar = findViewById(R.id.nav_bar);
        navPill = findViewById(R.id.nav_pill);
        pages = new View[]{
                findViewById(R.id.page_home),
                findViewById(R.id.page_contacts),
                findViewById(R.id.page_chat),
                findViewById(R.id.page_mine)
        };
        labelsOn = new TextView[]{
                findViewById(R.id.nav_label_home),
                findViewById(R.id.nav_label_contacts),
                findViewById(R.id.nav_label_chat),
                findViewById(R.id.nav_label_mine)
        };
        // 颜色直切：active 用 secondary 角色，未选中 on-surface-variant（配方色值在 colors.xml）
        int colorOn = ContextCompat.getColor(this, R.color.nav_label_on);
        int colorOff = ContextCompat.getColor(this, R.color.nav_label_off);
        labelsOff = new TextView[labelsOn.length];
        for (int i = 0; i < labelsOn.length; i++) {
            labelsOn[i].setTextColor(i == 0 ? colorOn : colorOff);
        }
        for (int i = 1; i < pages.length; i++) {
            pages[i].setVisibility(View.GONE);
        }

        int[] tabIds = {R.id.nav_tab_home, R.id.nav_tab_contacts,
                R.id.nav_tab_chat, R.id.nav_tab_mine};
        for (int i = 0; i < tabIds.length; i++) {
            final int target = i;
            findViewById(tabIds[i]).setOnClickListener(v -> switchTo(target));
        }

        // pill 尺寸与初始位置：等布局完成后按 navBar 宽度/4 计算（配方几何）
        navBar.post(this::placePill);
    }

    /** 格宽 = navBar 宽/4；pill 宽 = 格宽-24dp；translationX = target*格宽+12dp */
    private void placePill() {
        int cell = navBar.getWidth() / pages.length;
        int margin = dp(12);
        int pillWidth = cell - margin * 2;
        android.view.ViewGroup.LayoutParams lp = navPill.getLayoutParams();
        lp.width = pillWidth;
        navPill.setLayoutParams(lp);
        navPill.setTranslationX(currentTab * cell + margin);
        navPill.setVisibility(View.VISIBLE);
    }

    /** 切页：目标页从右（或左）滑入回弹，旧页反向滑出后 GONE（配方平移切页） */
    private void switchTo(int target) {
        if (target == currentTab || switching) {
            return;   // 快速连点防护：切换中忽略
        }
        switching = true;
        int cell = navBar.getWidth() / pages.length;
        int margin = dp(12);

        // 复位所有动画状态（中途二次点击的保护）
        for (View page : pages) {
            page.animate().cancel();
            page.setTranslationX(0);
        }
        navPill.animate().cancel();

        View from = pages[currentTab];
        View to = pages[target];

        // pill 弹射：spring 位移
        navPill.animate()
                .translationX(target * cell + margin)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .setDuration(350)
                .start();

        // 颜色直切（damped 轨道：不做动画）
        int colorOn = ContextCompat.getColor(this, R.color.nav_label_on);
        int colorOff = ContextCompat.getColor(this, R.color.nav_label_off);
        labelsOn[currentTab].setTextColor(colorOff);
        labelsOn[target].setTextColor(colorOn);

        int dir = target > currentTab ? 1 : -1;
        to.setVisibility(View.VISIBLE);
        to.setTranslationX(dp(72) * dir);
        to.animate().translationX(0)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .setDuration(350)
                .withEndAction(() -> {
                    // 幂等：若中途又切走，from != 旧当前页就不再隐藏
                    if (from != pages[currentTab]) {
                        from.setVisibility(View.GONE);
                    }
                    switching = false;
                })
                .start();
        from.animate().translationX(dp(-72) * dir)
                .setDuration(280)
                .withEndAction(() -> {
                    // 快速连点时当前页可能已换，判身份再隐藏，避免藏错页
                    if (from != pages[currentTab]) {
                        from.setVisibility(View.GONE);
                        from.setTranslationX(0);
                    }
                })
                .start();

        currentTab = target;
        Log.d(TAG, "切到 tab " + target);

        // 回首页时刷新统计卡
        if (target == 0) {
            loadStats();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- 页面按钮 ----------

    private void wirePageButtons() {
        findViewById(R.id.btn_mates).setOnClickListener(v ->
                startActivity(new Intent(this, MateActivity.class)));
        findViewById(R.id.btn_lab).setOnClickListener(v ->
                startActivity(new Intent(this, LabActivity.class)));
        findViewById(R.id.btn_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btn_contacts).setOnClickListener(v ->
                startActivity(new Intent(this, ContactListActivity.class)));
        findViewById(R.id.btn_draft).setOnClickListener(v ->
                startActivity(new Intent(this, DraftActivity.class)));
        findViewById(R.id.btn_chat).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));
        findViewById(R.id.btn_tools).setOnClickListener(v ->
                startActivity(new Intent(this, ToolsActivity.class)));
        findViewById(R.id.btn_download).setOnClickListener(v ->
                startActivity(new Intent(this, DownloadActivity.class)));
    }

    /** expressive-card 统计：本地 SQLite 联系人数 + 服务端成员总数 */
    private void loadStats() {
        TextView tvContacts = findViewById(R.id.tv_stat_contacts);
        TextView tvMates = findViewById(R.id.tv_stat_mates);
        // 本地数同步查（SQLite 小表，主线程可接受；大表应切子线程）
        ContactDao dao = new ContactDao(this);
        tvContacts.setText(String.valueOf(dao.query("").size()));
        dao.close();
        // 服务端总数走网络
        HttpUtil.get(JsonCompareActivity.BASE_URL + "/api/mates?page=1&size=1",
                new HttpUtil.Callback() {
                    @Override
                    public void onSuccess(String body) {
                        try {
                            long total = new JSONObject(body)
                                    .getJSONObject("data").getLong("total");
                            tvMates.setText(String.valueOf(total));
                        } catch (Exception e) {
                            tvMates.setText("—");
                        }
                    }

                    @Override
                    public void onError(String message, Exception e) {
                        tvMates.setText("—");   // server 没起时显示占位
                    }
                });
    }

    // ---------- 任务 4：网络变化广播 ----------

    /**
     * 动态注册网络变化广播接收器。
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
        tvNetBanner.setVisibility(View.VISIBLE);
        bannerHandler.removeCallbacksAndMessages(null);
        bannerHandler.postDelayed(() -> tvNetBanner.setVisibility(View.GONE), 3000);
    }

    // ---------- 生命周期日志（任务 2 验收点） ----------

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
