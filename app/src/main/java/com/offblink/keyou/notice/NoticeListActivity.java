package com.offblink.keyou.notice;

import com.offblink.keyou.R;
import com.offblink.keyou.log.AppLog;
import com.offblink.keyou.net.HttpUtil;
import com.offblink.keyou.ui.KeyouActivity;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 班级公告列表：底部导航「班级群」左下 FAB 进来。
 *
 * 数据走 `GET /api/notices?size=50`（桌面端客户端发布）。未读判据是
 * {@link NoticeStore#lastSeenId}：id 比它大的行首带红点；
 * 整页渲染完把游标推到当前最大 id —— 列表本身就展示了全部内容，看一次就算读完。
 * 班级群 FAB 上的红点同步由 ChatPanel 轮询 /api/notices?size=1 维护。
 */
public class NoticeListActivity extends KeyouActivity {

    private static final String TAG = "Keyou/NoticeList";

    private LinearLayout list;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice_list);
        AppLog.d(TAG, "onCreate");

        list = findViewById(R.id.notice_list);
        status = findViewById(R.id.tv_notice_status);
        load();
    }

    private void load() {
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.notice_loading);
        HttpUtil.get(this, "/api/notices?size=50", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                render(body);
            }

            @Override
            public void onError(String message, Exception e) {
                status.setVisibility(View.VISIBLE);
                status.setText(getString(R.string.notice_load_failed, message));
                AppLog.w(TAG, "公告加载失败: " + message);
            }
        });
    }

    private void render(String body) {
        long lastSeen = NoticeStore.lastSeenId(this);
        long maxId = lastSeen;
        list.removeAllViews();
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", -1) != 0) {
                status.setVisibility(View.VISIBLE);
                status.setText(root.optString("message"));
                return;
            }
            JSONArray array = root.optJSONArray("data");
            if (array == null || array.length() == 0) {
                status.setVisibility(View.VISIBLE);
                status.setText(R.string.notice_empty);
                return;
            }
            status.setVisibility(View.GONE);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                long id = item.optLong("id", 0);
                maxId = Math.max(maxId, id);
                list.addView(buildRow(
                        id > lastSeen,
                        item.optString("title", ""),
                        item.optString("author", "—") + " · " + item.optString("createdAt", ""),
                        item.optString("content", "")));
            }
        } catch (Exception e) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.notice_parse_failed);
            AppLog.e(TAG, "公告解析失败", e);
            return;
        }
        NoticeStore.markSeen(this, maxId);
        AppLog.i(TAG, "公告已渲染，游标推进到 " + maxId);
    }

    /** 一行公告：未读红点 + 标题 / 作者·时间 / 正文 */
    private View buildRow(boolean unread, String title, String meta, String content) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (unread) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_dot_red);
            dot.setContentDescription(getString(R.string.notice_unread));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
            dotParams.setMarginEnd(dp(8));
            head.addView(dot, dotParams);
        }

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(getColor(R.color.md_on_surface));
        tvTitle.setTypeface(null, unread
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        head.addView(tvTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(head);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(meta);
        tvMeta.setTextSize(12);
        tvMeta.setTextColor(getColor(R.color.md_on_surface_variant));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(4);
        row.addView(tvMeta, metaParams);

        if (content != null && !content.trim().isEmpty()) {
            TextView tvContent = new TextView(this);
            tvContent.setText(content);
            tvContent.setTextSize(13);
            tvContent.setTextColor(getColor(R.color.md_on_surface));
            tvContent.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            contentParams.topMargin = dp(6);
            row.addView(tvContent, contentParams);
        }
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
