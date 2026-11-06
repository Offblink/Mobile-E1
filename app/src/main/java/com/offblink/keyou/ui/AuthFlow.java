package com.offblink.keyou.ui;

import com.offblink.keyou.R;
import com.offblink.keyou.log.AppLog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 登录 / 注册共用的「进入主界面」动线：先盖全屏加载遮罩，600ms 后淡入 MainActivity。
 *
 * 两个入口走同一段代码：登录校验通过走这里，注册成功也直接走这里
 * —— 注册后不再回填登录框、也不用再点一次登录。
 */
final class AuthFlow {

    private static final String TAG = "Keyou/Auth";

    private AuthFlow() {
    }

    static void enter(Activity activity, String username, String role) {
        showLoadingOverlay(activity);

        Intent intent = new Intent(activity, MainActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString("username", username);
        bundle.putString("role", role);
        bundle.putInt("age", 20);
        bundle.putBoolean("isVip", true);
        bundle.putDouble("score", 99.5);
        // 清掉登录/注册页：否则从注册页进主界面后，按返回会退回登录页
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtras(bundle);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            activity.startActivity(intent);
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            activity.finish();
            AppLog.i(TAG, "进入主界面: " + username);
        }, 600);
    }

    /** 全屏半透明遮罩 + 转圈 + 「正在进入…」 */
    private static void showLoadingOverlay(Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null || root.findViewById(R.id.ll_auth_loading) != null) {
            return;
        }
        LinearLayout box = new LinearLayout(activity);
        box.setId(R.id.ll_auth_loading);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xCC000000);
        box.setClickable(true);   // 遮罩期间挡住点击

        ProgressBar bar = new ProgressBar(activity);
        box.addView(bar);

        TextView label = new TextView(activity);
        label.setText(R.string.login_loading);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(15);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (12 * activity.getResources().getDisplayMetrics().density);
        box.addView(label, labelParams);

        root.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
