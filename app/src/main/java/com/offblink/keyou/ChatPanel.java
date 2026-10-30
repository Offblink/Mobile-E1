package com.offblink.keyou;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 「课友圈」Tab 内嵌的聊天面板：消息列表 + 输入行。
 * 页面常驻在底部导航里，切走再切回消息不丢（状态挂在 Activity 上）。
 */
public final class ChatPanel {

    private static final String TAG = "Keyou/Chat";

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;

    public ChatPanel() {
        messages.add(new ChatMessage("大家好，我是班长张三", false));
        messages.add(new ChatMessage("收到，明天的课在 301", true));
    }

    public void attach(Activity host, View root) {
        RecyclerView rv = root.findViewById(R.id.rv_messages);
        rv.setLayoutManager(new LinearLayoutManager(host));
        adapter = new ChatAdapter(messages);
        rv.setAdapter(adapter);

        EditText etInput = root.findViewById(R.id.et_message);
        Button btnSend = root.findViewById(R.id.btn_send);

        Runnable send = () -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(host, R.string.chat_empty_tip, Toast.LENGTH_SHORT).show();
                return;
            }
            messages.add(new ChatMessage(text, true));
            adapter.notifyItemInserted(messages.size() - 1);
            rv.scrollToPosition(messages.size() - 1);
            etInput.setText("");
            AppLog.d(TAG, "发送: " + text);
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
    }
}
