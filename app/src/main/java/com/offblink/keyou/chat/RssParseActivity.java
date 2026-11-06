package com.offblink.keyou.chat;

import com.offblink.keyou.R;
import com.offblink.keyou.log.AppLog;
import com.offblink.keyou.net.HttpUtil;
import com.offblink.keyou.ui.KeyouActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 班级公告页：数据来自服务端 `GET /api/notices`（电脑端客户端在这里发布公告）。
 *
 * 任务 7 的 XML 解析演示仍然保留在服务端 `/api/rss`（由 notice 表生成），
 * 但页面主数据面走 JSON —— 手机端负责看，电脑端负责发。
 */
public class RssParseActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Notice";

    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rss_parse);
        AppLog.d(TAG, "onCreate");

        tvResult = findViewById(R.id.tv_rss_result);

        Button btnRefresh = findViewById(R.id.btn_fetch_rss);
        btnRefresh.setOnClickListener(v -> loadNotices());

        loadNotices();
    }

    private void loadNotices() {
        tvResult.setText(R.string.json_parsing);
        HttpUtil.get(this, "/api/notices?size=50", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                tvResult.setText(render(body));
            }

            @Override
            public void onError(String message, Exception e) {
                tvResult.setText(getString(R.string.json_fetch_failed, message));
                AppLog.w(TAG, "公告加载失败: " + message);
            }
        });
    }

    private String render(String body) {
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", -1) != 0) {
                return root.optString("message");
            }
            JSONArray array = root.optJSONArray("data");
            if (array == null || array.length() == 0) {
                return getString(R.string.notice_empty);
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                sb.append(getString(R.string.notice_item,
                        item.optString("title"),
                        item.optString("author", "—"),
                        item.optString("createdAt", ""),
                        item.optString("content", "")));
                if (i < array.length() - 1) {
                    sb.append("\n\n");
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "公告解析失败", e);
            return getString(R.string.download_parse_error);
        }
        return sb.toString();
    }
}
