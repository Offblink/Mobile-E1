package com.offblink.keyou.chat;

import com.offblink.keyou.R;
import com.offblink.keyou.log.AppLog;
import com.offblink.keyou.net.HttpUtil;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 「班级群」Tab 内嵌的聊天面板：消息列表 + 输入行（公告/群文件入口在调试面板里）。
 *
 * 消息后端起在 SSM（`/api/messages`）：
 * - 进入面板先拉一次全量（after=0&size=100），之后在宿主 onResume/onPause 之间
 *   每 4 秒用 after=最后一条 id 增量拉取 —— 电脑端客户端发来的消息会自动出现；
 * - 发送走 POST /api/message，成功后把服务端返回的那条追加到本地列表（不重复拉）。
 * 页面常驻在底部导航里，切走再切回消息不丢（状态挂在 Activity 上）。
 */
public final class ChatPanel {

    private static final String TAG = "Keyou/Chat";

    /** 增量拉取间隔：够电脑端发完消息基本"秒到"，又不至于把服务端打满 */
    private static final long POLL_INTERVAL_MS = 4000L;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ChatAdapter adapter;
    private Activity host;
    private RecyclerView recyclerView;
    private EditText etInput;
    private String sender = "我";
    /** 已收到的最大消息 id，增量拉取游标 */
    private long lastId;
    private boolean polling;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!polling) {
                return;
            }
            fetchNew(false);
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public ChatPanel() {
    }

    /** @param myName 本机昵称，作为发消息时的 sender */
    public void attach(Activity host, View root, String myName) {
        this.host = host;
        if (myName != null && !myName.trim().isEmpty()) {
            this.sender = myName.trim();
        }

        recyclerView = root.findViewById(R.id.rv_messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(host));
        adapter = new ChatAdapter(messages);
        recyclerView.setAdapter(adapter);

        etInput = root.findViewById(R.id.et_message);
        Button btnSend = root.findViewById(R.id.btn_send);

        Runnable send = () -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(host, R.string.chat_empty_tip, Toast.LENGTH_SHORT).show();
                return;
            }
            sendToServer(text);
        };
        btnSend.setOnClickListener(v -> send.run());
        // 键盘上的「发送」键也能直接发（回车不换行）
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                send.run();
                return true;
            }
            return false;
        });

        fetchNew(true);
    }

    /** 回到会话：恢复轮询并补拉断线期间的消息 */
    public void onHostResume() {
        if (host == null) {
            return;
        }
        polling = true;
        handler.removeCallbacks(pollTask);
        handler.post(pollTask);
    }

    /** 离开会话：停轮询（页面常驻，消息列表不清空） */
    public void onHostPause() {
        polling = false;
        handler.removeCallbacks(pollTask);
    }

    // ---------- 网络 ----------

    /** @param reset true=全量拉取（首次进入），false=按 after 游标增量 */
    private void fetchNew(boolean reset) {
        if (host == null) {
            return;
        }
        long after = reset ? 0 : lastId;
        HttpUtil.get(host, "/api/messages?after=" + after + "&size=100", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                applyMessages(body, reset);
            }

            @Override
            public void onError(String message, Exception e) {
                // 断网时保持列表不动，下次轮询再试
                AppLog.w(TAG, "拉取群消息失败: " + message);
            }
        });
    }

    private void applyMessages(String body, boolean reset) {
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", -1) != 0) {
                AppLog.w(TAG, "服务端返回错误: " + root.optString("message"));
                return;
            }
            JSONArray array = root.optJSONArray("data");
            if (array == null) {
                return;
            }
            if (reset) {
                messages.clear();
                lastId = 0;
            }
            int added = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                long id = item.optLong("id", 0);
                String content = item.optString("content", "");
                String from = item.optString("sender", "");
                if (id <= lastId) {
                    continue;
                }
                lastId = Math.max(lastId, id);
                messages.add(new ChatMessage(id, from, content, sender.equals(from)));
                added++;
            }
            if (added > 0) {
                if (added == messages.size()) {
                    adapter.notifyDataSetChanged();
                } else {
                    adapter.notifyItemRangeInserted(messages.size() - added, added);
                }
                recyclerView.scrollToPosition(messages.size() - 1);
                AppLog.d(TAG, "收到 " + added + " 条群消息，lastId=" + lastId);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "群消息解析失败", e);
        }
    }

    private void sendToServer(String text) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sender", sender);
            payload.put("content", text);
            HttpUtil.postJson(host, "/api/message", payload.toString(), new HttpUtil.Callback() {
                @Override
                public void onSuccess(String body) {
                    try {
                        JSONObject root = new JSONObject(body);
                        if (root.optInt("code", -1) != 0) {
                            Toast.makeText(host, root.optString("message"), Toast.LENGTH_LONG).show();
                            return;
                        }
                        JSONObject data = root.optJSONObject("data");
                        long id = data == null ? 0 : data.optLong("id", 0);
                        lastId = Math.max(lastId, id);
                        messages.add(new ChatMessage(id, sender, text, true));
                        adapter.notifyItemInserted(messages.size() - 1);
                        recyclerView.scrollToPosition(messages.size() - 1);
                        etInput.setText("");
                        AppLog.i(TAG, "已发送群消息 id=" + id);
                    } catch (Exception e) {
                        Toast.makeText(host, R.string.chat_send_failed,
                                Toast.LENGTH_SHORT).show();
                        AppLog.e(TAG, "发送结果解析失败", e);
                    }
                }

                @Override
                public void onError(String message, Exception e) {
                    Toast.makeText(host, host.getString(R.string.chat_send_failed, message),
                            Toast.LENGTH_LONG).show();
                    AppLog.w(TAG, "发送失败: " + message);
                }
            });
        } catch (Exception e) {
            AppLog.e(TAG, "构造消息失败", e);
        }
    }
}
