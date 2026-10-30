package com.offblink.keyou.reader;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
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
 * 进入页面即读取并直接显示通讯录，不再需要点按钮；每次回到前台会重新读一遍。
 */
public class ReaderActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Reader";

    private static final Uri CONTACT_URI =
            Uri.parse("content://com.offblink.keyou.provider.contact/contact");

    private TextView tvStatus;
    private TextView tvEmpty;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        Log.d(TAG, "onCreate");

        tvStatus = findViewById(R.id.tv_reader_status);
        tvEmpty = findViewById(R.id.tv_reader_empty);
        list = findViewById(R.id.ll_contacts);
    }

    @Override
    protected void onResume() {
        super.onResume();
        readProvider();   // 进入/回到前台直接刷新，无需按钮
    }

    /** ContentResolver.query 主 App 的 Provider；权限不足会抛 SecurityException */
    private void readProvider() {
        tvStatus.setText(R.string.reader_reading);
        list.removeAllViews();
        tvEmpty.setVisibility(View.GONE);

        ContentResolver resolver = getContentResolver();
        LayoutInflater inflater = LayoutInflater.from(this);
        int count = 0;
        try (Cursor cursor = resolver.query(CONTACT_URI,
                new String[]{"id", "name", "phone"}, null, null, "id DESC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    String phone = cursor.getString(2);
                    addRow(inflater, name, phone);
                    count++;
                }
            }
        } catch (SecurityException e) {
            // signature 权限未授予（签名不一致等）
            Log.e(TAG, "跨 App 读取被拒（权限管控生效）", e);
            tvStatus.setText(R.string.reader_permission_denied);
            return;
        } catch (Exception e) {
            Log.e(TAG, "跨 App 读取失败", e);
            tvStatus.setText(getString(R.string.reader_failed, e.getMessage()));
            return;
        }

        Log.i(TAG, "跨 App 读到 " + count + " 条");
        if (count == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvStatus.setText(R.string.reader_empty);
        } else {
            tvStatus.setText(getString(R.string.reader_count, count));
        }
    }

    private void addRow(LayoutInflater inflater, String name, String phone) {
        View row = inflater.inflate(R.layout.item_reader_contact, list, false);
        TextView tvLetter = row.findViewById(R.id.tv_letter);
        TextView tvName = row.findViewById(R.id.tv_name);
        TextView tvPhone = row.findViewById(R.id.tv_phone);

        String safeName = name == null ? "" : name;
        tvName.setText(TextUtils.isEmpty(safeName) ? "未命名" : safeName);
        tvPhone.setText(TextUtils.isEmpty(phone) ? "—" : phone);
        tvLetter.setText(letterOf(safeName));
        list.addView(row);
    }

    /** 取姓名首字符做占位（本地 App 只读 id/name/phone，不读取头像） */
    private static String letterOf(String name) {
        if (TextUtils.isEmpty(name)) {
            return "?";
        }
        return new String(Character.toChars(name.codePointAt(0))).toUpperCase();
    }
}
