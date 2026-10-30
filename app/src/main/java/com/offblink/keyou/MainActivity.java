package com.offblink.keyou;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 主界面：四个 Tab（首页 / 同学录 / 课友圈 / 我的）共用一套胶囊导航。
 *
 * 首页直接展示班级成员列表（服务端），课友圈直接是聊天界面，
 * 我的页放个人信息、工具箱、关于与退出登录。
 *
 * 动效双轨（mobile-design 约定）：位移走 spring（OvershootInterpolator 0.7 / 350ms），
 * 颜色/透明度直切不回弹 —— 绝不共用一个动画对象。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Main";

    /** 登录页写「记住密码」用的 SP（退出登录时清掉） */
    private static final String LOGIN_SP = "login";

    private TextView tvNetBanner;
    private TextView tvNickname;
    private TextView tvAvatarLetter;
    private ImageView ivAvatar;
    private TextView tvProvince;
    private TextView tvLocationDetail;

    private String account = "同学";
    private String nickname = "同学";

    private ChatPanel chatPanel;

    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    /** 定位超时用独立 Handler：网络横幅会清空 bannerHandler 的回调 */
    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private CancellationSignal locatingSignal;
    /** 自动定位的权限弹窗每次进程只弹一次，拒绝后不再打扰 */
    private boolean permissionPrompted;
    private BroadcastReceiver networkReceiver;

    // ---------- m3 胶囊导航状态 ----------
    private View navBar;
    private View navPill;
    private View[] pages;
    private TextView[] labels;
    private int currentTab = 0;
    private boolean switching = false;

    /** 系统相册选头像（Photo Picker，无需存储权限） */
    private final ActivityResultLauncher<PickVisualMediaRequest> avatarPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    saveAvatar(uri);
                }
            });

    /** 「我的」页定位：一次申请 fine + coarse，不申请后台定位 */
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> {
                        Boolean fine = results.get(Manifest.permission.ACCESS_FINE_LOCATION);
                        Boolean coarse = results.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                        if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                            fetchLocation();
                        } else {
                            tvLocationDetail.setText(R.string.mine_location_denied);
                            AppLog.w(TAG, "定位权限被拒绝");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppLog.v(TAG, "onCreate: verbose 日志");
        AppLog.d(TAG, "onCreate: debug 日志");
        AppLog.i(TAG, "onCreate: info 日志");
        AppLog.w(TAG, "onCreate: warn 日志");
        AppLog.e(TAG, "onCreate: error 日志（演示用，非真实错误）");

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            account = intent.getStringExtra("username");
            AppLog.i(TAG, "收到 Bundle: username=" + account
                    + ", age=" + intent.getIntExtra("age", -1)
                    + ", isVip=" + intent.getBooleanExtra("isVip", false)
                    + ", score=" + intent.getDoubleExtra("score", 0));
        }
        nickname = ProfileStore.nickname(this, account);

        tvNetBanner = findViewById(R.id.tv_net_banner);
        tvNickname = findViewById(R.id.tv_nickname);
        tvAvatarLetter = findViewById(R.id.tv_avatar_letter);
        ivAvatar = findViewById(R.id.iv_avatar);
        tvNickname.setText(nickname);

        setupNav();
        bindPages();
        renderAvatar();

        // 系统返回键：非首页 tab 时先回首页，首页再按才退出
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTab != 0) {
                    switchTo(0);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        registerNetworkReceiver();
    }

    // ---------- 页面装配 ----------

    private void bindPages() {
        ContactsPage.attach(this, findViewById(R.id.page_contacts));

        chatPanel = new ChatPanel();
        chatPanel.attach(this, findViewById(R.id.page_chat));

        bindMine();
    }

    private void bindMine() {
        findViewById(R.id.fl_avatar).setOnClickListener(v ->
                avatarPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        tvNickname.setOnClickListener(v -> showNicknameDialog());

        tvProvince = findViewById(R.id.tv_province);
        tvLocationDetail = findViewById(R.id.tv_location_detail);
        renderPlace();

        findViewById(R.id.btn_tools).setOnClickListener(v ->
                startActivity(new Intent(this, ToolsActivity.class)));
        findViewById(R.id.btn_logs).setOnClickListener(v ->
                startActivity(new Intent(this, LogActivity.class)));
        findViewById(R.id.btn_logout).setOnClickListener(v -> confirmLogout());
    }

    // ---------- 定位 → 省份（离线枚举） ----------

    /** 进入「我的」自动定位：5 分钟内的结果直接复用，否则取新点；没授权才弹一次权限 */
    private void ensureLocation() {
        if (locatingSignal != null) {
            return;   // 上一次还在定位，不重复发起
        }
        PlaceStore.Place place = PlaceStore.last(this);
        if (place != null && place.time > 0
                && System.currentTimeMillis() - place.time < 5 * 60 * 1000L) {
            return;
        }
        if (hasLocationPermission()) {
            fetchLocation();
        } else if (!permissionPrompted) {
            permissionPrompted = true;
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        } else {
            tvLocationDetail.setText(R.string.mine_location_denied);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 缓存点够新（5 分钟内）直接用；否则请求一次实时定位，超时再退回缓存点 */
    @SuppressWarnings("MissingPermission")  // requestLocation 已校验授权
    private void fetchLocation() {
        LocationManager lm = getSystemService(LocationManager.class);
        if (lm == null) {
            tvLocationDetail.setText(R.string.mine_location_none);
            return;
        }
        Location last = lastKnownLocation(lm);
        boolean fresh = last != null && last.getTime() > 0
                && System.currentTimeMillis() - last.getTime() < 5 * 60 * 1000L;
        if (fresh) {
            onLocation(last);
            return;
        }

        List<String> providers = new ArrayList<>();
        for (String provider : new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER}) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    providers.add(provider);
                }
            } catch (Exception ignore) {
                // 个别 provider 查询失败不影响其它
            }
        }
        if (providers.isEmpty()) {
            tvLocationDetail.setText(R.string.mine_location_none);
            AppLog.w(TAG, "没有可用的定位 provider");
            return;
        }

        tvLocationDetail.setText(R.string.mine_location_loading);
        CancellationSignal signal = new CancellationSignal();
        locatingSignal = signal;
        Runnable timeout = () -> {
            if (locatingSignal != signal) {
                return;
            }
            locatingSignal = null;
            signal.cancel();
            if (last != null) {
                onLocation(last);   // 实时定位没回来，退回缓存点（详情里带时间）
            } else {
                tvLocationDetail.setText(R.string.mine_location_none);
            }
            AppLog.w(TAG, "实时定位超时");
        };
        locationHandler.postDelayed(timeout, 8000);
        try {
            lm.getCurrentLocation(providers.get(0), signal, getMainExecutor(), location -> {
                if (locatingSignal != signal) {
                    return;
                }
                locatingSignal = null;
                locationHandler.removeCallbacks(timeout);
                if (location != null) {
                    onLocation(location);
                } else if (last != null) {
                    onLocation(last);
                } else {
                    tvLocationDetail.setText(R.string.mine_location_none);
                }
            });
        } catch (Exception e) {
            AppLog.e(TAG, "实时定位失败", e);
            locationHandler.removeCallbacks(timeout);
            if (last != null) {
                onLocation(last);
            } else {
                tvLocationDetail.setText(R.string.mine_location_none);
            }
        }
    }

    /** 各 provider 的缓存点里取时间最新的一条 */
    private Location lastKnownLocation(LocationManager lm) {
        Location best = null;
        for (String provider : lm.getAllProviders()) {
            try {
                Location location = lm.getLastKnownLocation(provider);
                if (location != null && (best == null || location.getTime() > best.getTime())) {
                    best = location;
                }
            } catch (Exception ignore) {
                // 权限/后端异常：跳过该 provider
            }
        }
        return best;
    }

    private void onLocation(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        String province = ProvinceLocator.provinceOf(lat, lon);
        if (province == null) {
            province = getString(R.string.mine_location_outside);
        }
        PlaceStore.save(this, lat, lon, province, location.getProvider(),
                location.getTime() > 0 ? location.getTime() : System.currentTimeMillis());
        renderPlace();
        AppLog.i(TAG, "定位: " + lat + ", " + lon + " → " + province
                + "（" + location.getProvider() + "）");
    }

    /** 读上次结果填「我的」页；没有定位过就显示提示 */
    private void renderPlace() {
        PlaceStore.Place place = PlaceStore.last(this);
        if (place == null) {
            tvProvince.setVisibility(View.GONE);
            tvLocationDetail.setText(R.string.mine_location_hint);
            return;
        }
        tvProvince.setVisibility(View.VISIBLE);
        tvProvince.setText(place.province);
        String time = place.time <= 0 ? "—"
                : new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(place.time));
        String provider = TextUtils.isEmpty(place.provider) ? "—" : place.provider;
        tvLocationDetail.setText(getString(R.string.mine_location_detail,
                String.format(Locale.US, "%.4f", place.lat),
                String.format(Locale.US, "%.4f", place.lon),
                provider, time));
    }

    /** 昵称修改：输入框校验非空后就地保存（不再跳页面） */
    private void showNicknameDialog() {
        EditText input = new EditText(this);
        input.setText(nickname);
        input.setSelection(input.getText().length());
        input.setHint(R.string.edit_nickname_hint);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, 0);
        box.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.edit_nickname_title)
                .setView(box)
                .setPositiveButton(R.string.contact_save, null)
                .setNegativeButton(R.string.common_cancel, null)
                .create();
        dialog.show();
        // 覆写确认键：昵称为空时留在弹窗内，不关闭（默认点击即关会丢输入）
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(this, R.string.nickname_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            nickname = value;
            ProfileStore.setNickname(this, value);
            tvNickname.setText(value);
            updateAvatarLetter();
            Toast.makeText(this, R.string.nickname_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.mine_logout_title)
                .setMessage(R.string.mine_logout_message)
                .setPositiveButton(R.string.mine_logout_ok, (d, w) -> {
                    // 清掉「记住密码」相关偏好，回登录页并清空返回栈
                    SharedPreferences login = getSharedPreferences(LOGIN_SP, MODE_PRIVATE);
                    login.edit().clear().apply();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    // ---------- 头像 ----------

    /** 有头像文件就 Glide 圆裁加载，没有就露出昵称首字 */
    private void renderAvatar() {
        updateAvatarLetter();
        String path = ProfileStore.avatarPath(this);
        if (path == null) {
            ivAvatar.setVisibility(View.GONE);
            tvAvatarLetter.setVisibility(View.VISIBLE);
            return;
        }
        ivAvatar.setVisibility(View.VISIBLE);
        tvAvatarLetter.setVisibility(View.GONE);
        Glide.with(this)
                .load(new File(path))
                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                .into(ivAvatar);
    }

    private void updateAvatarLetter() {
        String source = nickname == null || nickname.isEmpty() ? account : nickname;
        tvAvatarLetter.setText(new String(Character.toChars(source.codePointAt(0))).toUpperCase());
    }

    /** 相册选图 → 缩到 512px 存私有目录 → 记路径 */
    private void saveAvatar(Uri uri) {
        // 文件名带时间戳：Glide 按文件路径做缓存 key，覆盖同名文件会一直显示旧图
        File target = new File(getFilesDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
        String previous = ProfileStore.avatarPath(this);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalStateException("openInputStream 返回 null");
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            byte[] raw = in.readAllBytes();
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 512) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
            if (bitmap == null) {
                throw new IllegalStateException("图片解码失败");
            }
            try (FileOutputStream out = new FileOutputStream(target)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            bitmap.recycle();
            ProfileStore.setAvatarPath(this, target.getAbsolutePath());
            if (previous != null && !previous.equals(target.getAbsolutePath())) {
                File old = new File(previous);
                if (old.exists() && !old.delete()) {
                    AppLog.w(TAG, "旧头像删除失败: " + previous);
                }
            }
            renderAvatar();
            Toast.makeText(this, R.string.avatar_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            AppLog.e(TAG, "头像保存失败", e);
            Toast.makeText(this, R.string.avatar_pick_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- m3 胶囊导航 ----------

    private void setupNav() {
        navBar = findViewById(R.id.nav_bar);
        navPill = findViewById(R.id.nav_pill);
        pages = new View[]{
                findViewById(R.id.page_contacts),
                findViewById(R.id.page_chat),
                findViewById(R.id.page_mine)
        };
        labels = new TextView[]{
                findViewById(R.id.nav_label_contacts),
                findViewById(R.id.nav_label_chat),
                findViewById(R.id.nav_label_mine)
        };
        int colorOn = ContextCompat.getColor(this, R.color.nav_label_on);
        int colorOff = ContextCompat.getColor(this, R.color.nav_label_off);
        for (int i = 0; i < labels.length; i++) {
            labels[i].setTextColor(i == 0 ? colorOn : colorOff);
        }
        for (int i = 1; i < pages.length; i++) {
            pages[i].setVisibility(View.GONE);
        }

        int[] tabIds = {R.id.nav_tab_contacts, R.id.nav_tab_chat, R.id.nav_tab_mine};
        for (int i = 0; i < tabIds.length; i++) {
            final int target = i;
            findViewById(tabIds[i]).setOnClickListener(v -> switchTo(target));
        }

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

    /** 切页：目标页滑入回弹，旧页反向滑出后 GONE */
    private void switchTo(int target) {
        if (target == currentTab || switching) {
            return;
        }
        switching = true;
        int cell = navBar.getWidth() / pages.length;
        int margin = dp(12);

        for (View page : pages) {
            page.animate().cancel();
            page.setTranslationX(0);
        }
        navPill.animate().cancel();

        View from = pages[currentTab];
        View to = pages[target];

        navPill.animate()
                .translationX(target * cell + margin)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .setDuration(350)
                .start();

        int colorOn = ContextCompat.getColor(this, R.color.nav_label_on);
        int colorOff = ContextCompat.getColor(this, R.color.nav_label_off);
        labels[currentTab].setTextColor(colorOff);
        labels[target].setTextColor(colorOn);

        int dir = target > currentTab ? 1 : -1;
        to.setVisibility(View.VISIBLE);
        to.setTranslationX(dp(72) * dir);
        to.animate().translationX(0)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .setDuration(350)
                .withEndAction(() -> {
                    if (from != pages[currentTab]) {
                        from.setVisibility(View.GONE);
                    }
                    switching = false;
                })
                .start();
        from.animate().translationX(dp(-72) * dir)
                .setDuration(280)
                .withEndAction(() -> {
                    if (from != pages[currentTab]) {
                        from.setVisibility(View.GONE);
                        from.setTranslationX(0);
                    }
                })
                .start();

        currentTab = target;
        AppLog.d(TAG, "切到 tab " + target);

        if (target == 2) {
            ensureLocation();
        } else if (target == 0) {
            // 同学录页在 onResume 时自行刷新（本地 + 服务端两路数据）
            AppLog.d(TAG, "回到同学录");
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- 网络变化横幅 ----------

    private void registerNetworkReceiver() {
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean connected = isCurrentlyConnected();
                AppLog.i(TAG, "收到网络变化广播, connected=" + connected);
                showBanner(getString(connected
                        ? R.string.banner_net_on : R.string.banner_net_off));
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
        AppLog.d(TAG, "已动态注册网络变化接收器");
    }

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

    private void showBanner(String text) {
        tvNetBanner.setText(text);
        tvNetBanner.setVisibility(View.VISIBLE);
        bannerHandler.removeCallbacksAndMessages(null);
        bannerHandler.postDelayed(() -> tvNetBanner.setVisibility(View.GONE), 3000);
    }

    // ---------- 生命周期 ----------

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
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
            AppLog.d(TAG, "已注销网络变化接收器");
        }
        bannerHandler.removeCallbacksAndMessages(null);
        locationHandler.removeCallbacksAndMessages(null);
        if (locatingSignal != null) {
            locatingSignal.cancel();
            locatingSignal = null;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        AppLog.d(TAG, "onRestart");
    }
}
