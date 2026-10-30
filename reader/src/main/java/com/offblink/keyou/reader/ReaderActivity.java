package com.offblink.keyou.reader;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 6 的「第二个 App」：跨 App 通过 ContentResolver 读取课友主 App 的 ContactProvider。
 *
 * 前提：
 * - 主 App 的 Provider 已 exported + readPermission（signature 级）；
 * - 本 App 在 Manifest 声明 uses-permission，两个 App 同为 debug 签名 → 权限自动授予；
 * - 课友 App 必须先被启动过一次（Provider 进程才能被拉起）。
 *
 * 验证点：能读到主 App contact.db 的数据 = 跨 App 数据访问 + 权限管控链路通。
 */
public class ReaderActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Reader";

    private static final Uri CONTACT_URI =
            Uri.parse("content://com.offblink.keyou.provider.contact/contact");

    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        Log.d(TAG, "onCreate");

        tvResult = findViewById(R.id.tv_reader_result);

        Button btnRead = findViewById(R.id.btn_read_provider);
        btnRead.setOnClickListener(v -> readProvider());
    }

    /** ContentResolver.query 主 App 的 Provider；权限不足会抛 SecurityException */
    private void readProvider() {
        ContentResolver resolver = getContentResolver();
        StringBuilder sb = new StringBuilder();
        int count = 0;
        try (Cursor cursor = resolver.query(CONTACT_URI,
                new String[]{"id", "name", "phone"}, null, null, "id DESC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    count++;
                    sb.append(cursor.getLong(0)).append("  ")
                            .append(cursor.getString(1)).append("  ")
                            .append(cursor.getString(2)).append("\n");
                }
            }
        } catch (SecurityException e) {
            // signature 权限未授予（签名不一致等）
            Log.e(TAG, "跨 App 读取被拒（权限管控生效）", e);
            tvResult.setText(R.string.reader_permission_denied);
            return;
        }
        Log.i(TAG, "跨 App 读到 " + count + " 条");
        tvResult.setText(getString(R.string.reader_result_prefix, count) + "\n" + sb);
    }
}
