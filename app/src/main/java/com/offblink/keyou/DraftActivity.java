package com.offblink.keyou;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文件存储 —— 草稿箱。
 *
 * 验收点：openFileOutput / openFileInput 与 MODE_PRIVATE。
 * 文件落位 /data/data/com.offblink.keyou/files/chat_draft.txt（应用私有目录，卸载即删）；
 * 课友圈输入框自动保存的就是这个文件。
 * 首次进入必然触发 FileNotFoundException —— 按"还没有草稿"处理，不是错误。
 */
public class DraftActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Draft";
    private static final String FILE_NAME = "chat_draft.txt";

    private EditText etDraft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draft);
        AppLog.d(TAG, "onCreate");

        etDraft = findViewById(R.id.et_draft);

        Button btnSave = findViewById(R.id.btn_save_draft);
        btnSave.setOnClickListener(v -> saveDraft());

        Button btnLoad = findViewById(R.id.btn_load_draft);
        btnLoad.setOnClickListener(v -> loadDraft());

        loadDraft();
    }

    /** 写：openFileOutput(MODE_PRIVATE) —— 私有模式，其他应用不可读 */
    private void saveDraft() {
        String content = etDraft.getText().toString();
        try (FileOutputStream fos = openFileOutput(FILE_NAME, MODE_PRIVATE)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, R.string.draft_saved, Toast.LENGTH_SHORT).show();
            AppLog.i(TAG, "草稿已写入 files/" + FILE_NAME + " (" + content.length() + " 字)");
        } catch (IOException e) {
            AppLog.e(TAG, "写草稿失败", e);
            Toast.makeText(this, R.string.draft_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 读：openFileInput；首次无文件按"还没有草稿"处理 */
    private void loadDraft() {
        try (FileInputStream fis = openFileInput(FILE_NAME)) {
            byte[] buf = new byte[fis.available()];
            fis.read(buf);
            String text = new String(buf, StandardCharsets.UTF_8);
            etDraft.setText(text);
            AppLog.i(TAG, "草稿已读回 (" + text.length() + " 字)");
        } catch (FileNotFoundException e) {
            // 首次进入的正常路径，不是错误
            AppLog.i(TAG, "还没有草稿（files/" + FILE_NAME + " 不存在）");
        } catch (IOException e) {
            AppLog.e(TAG, "读草稿失败", e);
        }
    }
}
