package com.offblink.keyou;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务 5（本任务重点）：SQLite 通讯录 —— RecyclerView 展示，
 * 支持新增、编辑、删除、按姓名模糊查询。
 *
 * 交互约定：
 * - 顶部搜索框输入即查（LIKE 模糊，空查全部）；
 * - 点击行 → 编辑页（registerForActivityResult 回来刷新）；
 * - 长按行 → 删除确认对话框；
 * - 底部「新增」→ 编辑页空表单；
 * - 首次进入空库时用事务批量导入种子数据（importBatch 真事务）。
 */
public class ContactListActivity extends KeyouActivity {

    private static final String TAG = "Keyou/ContactList";

    private ContactDao dao;
    private ContactAdapter adapter;
    private final List<Contact> data = new ArrayList<>();
    private EditText etKeyword;

    /**
     * 任务 6：运行时权限 —— 现代结果 API（ActivityResultContracts.RequestPermission），
     * 三态流程（android/skills android-permissions-security 口径）：
     * 1. checkSelfPermission 已授权 → 直接干活；
     * 2. shouldShowRequestPermissionRationale = true → 先解释再申请；
     * 3. 拒绝且不再询问 → 引导去系统设置页。
     */
    private final ActivityResultLauncher<String> contactsPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    importSystemContacts();
                } else {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                    if (!shouldShowRequestPermissionRationale(
                            android.Manifest.permission.READ_CONTACTS)) {
                        // 永久拒绝（"不再询问"）：引导到应用详情设置页
                        Toast.makeText(this, R.string.permission_permanent_denied,
                                Toast.LENGTH_LONG).show();
                        android.content.Intent settings =
                                new android.content.Intent(android.provider.Settings
                                        .ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package",
                                                getPackageName(), null));
                        startActivity(settings);
                    }
                }
            });

    /** 编辑页回传：新增/编辑完成后刷新全表 */
    private final ActivityResultLauncher<Intent> editLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    refresh(etKeyword.getText().toString().trim());
                    Log.i(TAG, "编辑页返回，列表已刷新");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);
        Log.d(TAG, "onCreate");

        dao = new ContactDao(this);

        etKeyword = findViewById(R.id.et_keyword);
        etKeyword.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh(s.toString().trim());
            }
        });

        RecyclerView rv = findViewById(R.id.rv_contacts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactAdapter(data, contact -> {
            // 点击行 → 编辑页（带 id）
            Intent intent = new Intent(this, ContactEditActivity.class);
            intent.putExtra(ContactEditActivity.EXTRA_ID, contact.getId());
            editLauncher.launch(intent);
        });
        // 长按行 → 删除确认（RecyclerView 没有 setOnItemLongClickListener，挂在行视图上）
        adapter.setOnItemLongClickListener(position -> {
            Contact target = data.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.contact_delete_title)
                    .setMessage(getString(R.string.contact_delete_message, target.getName()))
                    .setPositiveButton(R.string.contact_delete_ok, (d, w) -> {
                        dao.delete(target.getId());
                        refresh(etKeyword.getText().toString().trim());
                        Toast.makeText(this, R.string.contact_deleted, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.common_cancel, null)
                    .show();
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btn_add_contact).setOnClickListener(v ->
                editLauncher.launch(new Intent(this, ContactEditActivity.class)));

        // 任务 6：导入系统联系人（READ_CONTACTS 危险权限运行时申请）
        findViewById(R.id.btn_import_system).setOnClickListener(v -> requestContactsPermission());

        seedIfEmpty();
        refresh("");
    }

    /** 任务 6：三态流程入口 —— 已授权直接导入，否则发起运行时申请 */
    private void requestContactsPermission() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_CONTACTS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            importSystemContacts();
        } else {
            contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS);
        }
    }

    /**
     * 任务 6：ContentResolver 读系统通讯录（理解 URI 与系统 Provider），
     * 读到的联系人插入本地 contact 表（按 phone 去重）。
     */
    private void importSystemContacts() {
        int imported = 0;
        try (android.database.Cursor cursor = getContentResolver().query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String phone = cursor.getString(1);
                    if (name == null || phone == null) {
                        continue;
                    }
                    // 简单去重：本地已有同名同号就跳过
                    boolean exists = false;
                    for (Contact c : dao.query("")) {
                        if (name.equals(c.getName()) && phone.equals(c.getPhone())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        dao.insert(new Contact(0, name, phone, null, "保密"));
                        imported++;
                    }
                }
            }
        }
        refresh(etKeyword.getText().toString().trim());
        Toast.makeText(this,
                getString(R.string.import_result, imported), Toast.LENGTH_SHORT).show();
        Log.i(TAG, "导入系统联系人 " + imported + " 条");
    }

    /** 空库时事务批量导入种子数据（importBatch 内部是真事务） */
    private void seedIfEmpty() {
        if (dao.query("").isEmpty()) {
            List<Contact> seeds = new ArrayList<>();
            seeds.add(new Contact(0, "张三", "13800000001", "zhangsan@example.com", "男"));
            seeds.add(new Contact(0, "李四", "13800000002", "lisi@example.com", "女"));
            dao.importBatch(seeds);
        }
    }

    private void refresh(String kw) {
        data.clear();
        data.addAll(dao.query(kw));
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dao.close();
        Log.d(TAG, "onDestroy");
    }
}
