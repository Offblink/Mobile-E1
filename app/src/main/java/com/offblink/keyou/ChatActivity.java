package com.offblink.keyou;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务 3：简易聊天室 —— 顶部标题栏 + 中间消息列表（左右气泡）+ 底部输入框与发送按钮。
 * 纯本地演示：发送后追加自己消息并模拟一条自动回复（不接后端，任务 7/9 联调后可扩展）。
 */
public class ChatActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Chat";

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private EditText etInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Log.d(TAG, "onCreate");

        RecyclerView rv = findViewById(R.id.rv_messages);
        rv.setLayoutManager(new LinearLayoutManager(this));
        // 起始两条演示消息：对方左、自己右
        messages.add(new ChatMessage("大家好，我是班长张三", false));
        messages.add(new ChatMessage("收到，明天的课在 301", true));
        adapter = new ChatAdapter(messages);
        rv.setAdapter(adapter);

        etInput = findViewById(R.id.et_message);
        Button btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.chat_empty_tip, Toast.LENGTH_SHORT).show();
                return;
            }
            messages.add(new ChatMessage(text, true));
            adapter.notifyItemInserted(messages.size() - 1);
            rv.scrollToPosition(messages.size() - 1);
            etInput.setText("");
            Log.d(TAG, "发送: " + text);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
