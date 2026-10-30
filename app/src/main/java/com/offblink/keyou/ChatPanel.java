package com.offblink.keyou;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 「课友圈」Tab 内嵌的聊天面板：消息列表 + 输入行 + 班级群入口。
 * 页面常驻在底部导航里，切走再切回消息不丢（状态挂在 Activity 上）。
 *
 * 输入框内容实时落到 files/chat_draft.txt（与「草稿」页同一个文件）：
 * 切后台、进草稿页都能看到未发送的内容；发送后清空文件。
 */
public final class ChatPanel {

    private static final String TAG = "Keyou/Chat";
    private static final String DRAFT_FILE = "chat_draft.txt";

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private Activity host;
    private EditText etInput;

    public ChatPanel() {
        messages.add(new ChatMessage("大家好，我是班长张三", false));
        messages.add(new ChatMessage("收到，明天的课在 301", true));
    }

    public void attach(Activity host, View root) {
        this.host = host;

        RecyclerView rv = root.findViewById(R.id.rv_messages);
        rv.setLayoutManager(new LinearLayoutManager(host));
        adapter = new ChatAdapter(messages);
        rv.setAdapter(adapter);

        etInput = root.findViewById(R.id.et_message);
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
            clearDraft();
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

        // 班级群的三个入口：公告 / 群文件 / 草稿，都在会话上下文里
        root.findViewById(R.id.btn_chat_notice).setOnClickListener(v ->
                host.startActivity(new Intent(host, RssParseActivity.class)));
        root.findViewById(R.id.btn_chat_files).setOnClickListener(v ->
                host.startActivity(new Intent(host, DownloadActivity.class)));
        root.findViewById(R.id.btn_chat_draft).setOnClickListener(v -> {
            saveDraft();   // 先把当前输入落盘，草稿页读到的就是最新内容
            host.startActivity(new Intent(host, DraftActivity.class));
        });

        restoreDraft();
    }

    /** 离开会话（切后台/进二级页）时保存；空输入就删文件 */
    public void onHostPause() {
        saveDraft();
    }

    /** 回到会话时以文件为准（草稿页可能刚改过） */
    public void onHostResume() {
        restoreDraft();
    }

    private void saveDraft() {
        if (host == null || etInput == null) {
            return;
        }
        String text = etInput.getText().toString();
        if (text.trim().isEmpty()) {
            clearDraft();
            return;
        }
        try (FileOutputStream out = host.openFileOutput(DRAFT_FILE, Context.MODE_PRIVATE)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            AppLog.d(TAG, "草稿已保存 (" + text.length() + " 字)");
        } catch (IOException e) {
            AppLog.e(TAG, "草稿保存失败", e);
        }
    }

    private void restoreDraft() {
        if (host == null || etInput == null) {
            return;
        }
        try (FileInputStream in = host.openFileInput(DRAFT_FILE)) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!text.equals(etInput.getText().toString())) {
                etInput.setText(text);
                etInput.setSelection(text.length());
            }
        } catch (FileNotFoundException e) {
            // 还没有草稿，正常路径
        } catch (IOException e) {
            AppLog.e(TAG, "草稿读取失败", e);
        }
    }

    private void clearDraft() {
        if (host != null && host.deleteFile(DRAFT_FILE)) {
            AppLog.d(TAG, "草稿文件已清空");
        }
    }
}
