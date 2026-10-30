package com.offblink.keyou;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「同学录」tab 的页面控制器（不是 Activity）：父 Activity 在 onCreate 里调一次
 * {@link #attach(AppCompatActivity, View)}，本类接管 page_contacts.xml 里的整块界面。
 *
 * 为什么不是一个 Activity：同学录是主页的第二个 tab（叠在 activity_main 里平移切页），
 * 用 View 承载才能跟着 tab 一起滑动；详情页仍是独立 Activity（ContactDetailActivity）。
 *
 * 职责：
 * - 列表：RecyclerView + 分组适配器 + 吸顶字母条（装饰器）+ 右侧字母索引条；
 * - 数据：只走 ContactDao（本地 SQLite 的 contact 表），不新增表，也不碰服务端；
 * - 增：右下 FAB 进详情页的新增模式；
 * - 删：长按行弹确认框；
 * - 导入：READ_CONTACTS 三态权限 → 读系统通讯录（ContactsContract），按 phone 去重后入库；
 * - 种子：空库时用 dao.importBatch 真事务批量导入演示数据。
 */
public final class ContactsPage {

    private static final String TAG = "Keyou/ContactsPage";

    private final AppCompatActivity host;
    private final View pageRoot;
    private final ContactDao dao;

    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private ContactGroupAdapter adapter;
    private ContactGroupDecoration decoration;
    private LetterIndexBar indexBar;
    private TextView subtitle;
    private TextView emptyView;

    private boolean firstLoad = true;
    private boolean firstResume = true;
    private volatile boolean importing;

    /** 详情页回传：新增/编辑/删除过就整表重刷 */
    private final ActivityResultLauncher<Intent> detailLauncher;

    /** READ_CONTACTS 的运行时申请（宿主是 AppCompatActivity，必须在 onCreate 内注册，attach 就干这个） */
    private final ActivityResultLauncher<String> permissionLauncher;

    private ContactsPage(AppCompatActivity host, View pageRoot) {
        this.host = host;
        this.pageRoot = pageRoot;
        this.dao = new ContactDao(host);

        this.detailLauncher = host.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        reload();
                    }
                });
        this.permissionLauncher = host.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        importSystemContacts();
                    } else {
                        onPermissionDenied();
                    }
                });
    }

    /**
     * 把同学录接到宿主 Activity 的 page_contacts 上；幂等（重复调用直接忽略）。
     * 必须在宿主 onCreate 内、STARTED 之前调用 —— 里面要用 registerForActivityResult。
     */
    public static void attach(AppCompatActivity host, View pageRoot) {
        if (host == null || pageRoot == null) {
            Log.w(TAG, "attach 参数为空，忽略");
            return;
        }
        RecyclerView rv = pageRoot.findViewById(R.id.rv_contacts);
        if (rv == null) {
            Log.e(TAG, "page_contacts 里找不到 rv_contacts，装配失败");
            return;
        }
        if (rv.getAdapter() != null) {
            return;   // 已经接过一次（配置变更重入/重复调用），别装两套
        }
        new ContactsPage(host, pageRoot).bind();
    }

    // ================= 装配 =================

    private void bind() {
        recyclerView = pageRoot.findViewById(R.id.rv_contacts);
        indexBar = pageRoot.findViewById(R.id.index_bar);
        subtitle = pageRoot.findViewById(R.id.tv_contacts_subtitle);
        emptyView = pageRoot.findViewById(R.id.tv_empty);

        adapter = new ContactGroupAdapter(host);
        adapter.setListener(new ContactGroupAdapter.Listener() {
            @Override
            public void onContactClick(Contact contact) {
                openDetail(contact.getId());
            }

            @Override
            public void onContactLongClick(Contact contact) {
                confirmDelete(contact);
            }
        });

        layoutManager = new LinearLayoutManager(host);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // 索引条高亮不自己算：由吸顶字母条在分组变化时回调，
        // 这样「顶部显示的字母」和「右侧高亮的字母」永远是同一个
        decoration = new ContactGroupDecoration(host, adapter);
        decoration.setOnSectionChangedListener(indexBar::setActiveLetter);
        recyclerView.addItemDecoration(decoration);

        indexBar.setOnLetterSelectedListener(letter -> {
            Integer position = adapter.positionOfLetter(letter);
            if (position != null) {
                layoutManager.scrollToPositionWithOffset(position, 0);
                indexBar.setActiveLetter(letter);
            }
        });

        View fab = pageRoot.findViewById(R.id.fab_add);
        if (fab != null) {
            fab.setOnClickListener(v -> openDetail(-1));
        }

        View importButton = pageRoot.findViewById(R.id.btn_import);
        if (importButton != null) {
            importButton.setOnClickListener(v -> requestContactsPermission());
        }

        seedIfEmpty();
        reload();
        observeLifecycle();
        Log.i(TAG, "同学录已接入 tab");
    }

    private void observeLifecycle() {
        host.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onResume(@NonNull LifecycleOwner owner) {
                // attach 之后紧接着就会有一次 onResume，此时刚 reload 过，跳过避免重复查库；
                // 之后每次回到前台（详情页返回 / 别的 App 通过 ContentProvider 改过数据）都重刷
                if (firstResume) {
                    firstResume = false;
                    return;
                }
                reload();
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                dao.close();
            }
        });
    }

    // ================= 数据 =================

    /** 整表重刷（不用 liveData，本地小表同步查可接受；主线程只做读 + 绑定） */
    private void reload() {
        if (recyclerView == null) {
            return;
        }
        List<Contact> data = dao.query("");
        adapter.setData(data);
        decoration.resetSection();
        indexBar.setAvailableLetters(adapter.availableLetters());
        recyclerView.invalidate();

        boolean empty = data.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        subtitle.setText(host.getString(R.string.contacts_count, data.size(), adapter.letterCount()));

        if (empty) {
            indexBar.setActiveLetter((char) 0);
        } else {
            char first = 0;
            for (Character letter : adapter.availableLetters()) {
                first = letter;
                break;
            }
            indexBar.setActiveLetter(first);
            if (firstLoad) {
                recyclerView.scrollToPosition(0);   // 只有首次加载回顶；返回列表时保留滚动位置
            }
        }
        firstLoad = false;
        Log.i(TAG, "列表已刷新：" + data.size() + " 位同学 / " + adapter.letterCount() + " 个分组");
    }

    /**
     * 空库时用事务批量导入种子数据（ContactDao.importBatch 内部是真事务）。
     * 种子故意覆盖不同首字母（含数字开头的 '#' 组），方便直观看到分组与索引条效果。
     */
    private void seedIfEmpty() {
        if (!dao.query("").isEmpty()) {
            return;
        }
        List<Contact> seeds = new ArrayList<>();
        seeds.add(new Contact(0, "安琪", "13800000001", "anqi@example.com", "女"));
        seeds.add(new Contact(0, "Amy", "13800000002", "amy@example.com", "女"));
        seeds.add(new Contact(0, "白雪", "13800000003", "baixue@example.com", "女"));
        seeds.add(new Contact(0, "陈思远", "13800000004", "chensiyuan@example.com", "男"));
        seeds.add(new Contact(0, "李雷", "13800000005", "lilei@example.com", "男"));
        seeds.add(new Contact(0, "孙悦", "13800000006", "sunyue@example.com", "女"));
        seeds.add(new Contact(0, "王小明", "13800000007", "wangxiaoming@example.com", "男"));
        seeds.add(new Contact(0, "张三", "13800000008", "zhangsan@example.com", "男"));
        seeds.add(new Contact(0, "2019 级班长", "13800000009", null, "保密"));
        dao.importBatch(seeds);
        Log.i(TAG, "空库，事务导入种子数据 " + seeds.size() + " 条");
    }

    // ================= 跳转 / 删除 =================

    /** id = -1 进新增模式，> 0 进编辑模式 */
    private void openDetail(long id) {
        Intent intent = new Intent(host, ContactDetailActivity.class);
        intent.putExtra(ContactDetailActivity.EXTRA_ID, id);
        detailLauncher.launch(intent);
    }

    private void confirmDelete(final Contact contact) {
        new MaterialAlertDialogBuilder(host)
                .setTitle(R.string.contact_delete_title)
                .setMessage(host.getString(R.string.contact_delete_message,
                        ContactStyle.emptyIfNull(contact.getName())))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.contact_delete_ok, (dialog, which) -> {
                    int rows = dao.delete(contact.getId());
                    reload();
                    Toast.makeText(host,
                            rows > 0 ? R.string.contact_deleted : R.string.contacts_delete_failed,
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ================= 导入系统联系人（READ_CONTACTS 三态） =================

    /** 一态：已授权直接干；否则发起运行时申请（二态：拒绝 / 三态：永久拒绝走回调） */
    private void requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(host, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            importSystemContacts();
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    /** 第二/三态：拒绝就说清楚；「不再询问」时引导去系统设置里开权限 */
    private void onPermissionDenied() {
        Toast.makeText(host, R.string.permission_denied, Toast.LENGTH_SHORT).show();
        if (!host.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            Toast.makeText(host, R.string.permission_permanent_denied, Toast.LENGTH_LONG).show();
            try {
                host.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", host.getPackageName(), null)));
            } catch (Exception e) {
                Log.w(TAG, "打开应用设置页失败", e);
            }
        }
    }

    /**
     * 读系统通讯录（ContactsContract 的 Phone 表）→ 按 phone 去重 → 事务入库。
     *
     * 全程在子线程：系统通讯录可能有几千条，Cursor 遍历 + 批量插入不能压在主线程上；
     * 完成后 runOnUiThread 回主线程刷新列表。
     */
    private void importSystemContacts() {
        if (importing) {
            return;
        }
        importing = true;
        Toast.makeText(host, R.string.contacts_import_running, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            int imported = 0;
            try {
                Set<String> knownPhones = new HashSet<>();
                for (Contact contact : dao.query("")) {
                    String key = normalizePhone(contact.getPhone());
                    if (!key.isEmpty()) {
                        knownPhones.add(key);
                    }
                }

                List<Contact> fresh = new ArrayList<>();
                try (Cursor cursor = host.getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, null)) {
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String name = cursor.getString(0);
                            String phone = cursor.getString(1);
                            if (name == null || phone == null) {
                                continue;
                            }
                            String key = normalizePhone(phone);
                            // phone 是去重键：同号多条（座机/手机）只留一条
                            if (key.isEmpty() || !knownPhones.add(key)) {
                                continue;
                            }
                            fresh.add(new Contact(0, name.trim(), phone.trim(), null, "保密"));
                        }
                    }
                }

                if (!fresh.isEmpty()) {
                    dao.importBatch(fresh);   // 真事务：任一条失败整体回滚
                }
                imported = fresh.size();
            } catch (Throwable t) {
                Log.w(TAG, "导入系统联系人失败", t);
            } finally {
                importing = false;
            }

            final int count = imported;
            host.runOnUiThread(() -> {
                if (host.isFinishing() || host.isDestroyed()) {
                    return;
                }
                reload();
                Toast.makeText(host, host.getString(R.string.import_result, count),
                        Toast.LENGTH_SHORT).show();
                Log.i(TAG, "导入系统联系人 " + count + " 条");
            });
        }, "contacts-import").start();
    }

    /** 去重键：只留数字（"+86 138-0000-0000" 与 "13800000000" 视为同一个号） */
    private static String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(phone.length());
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
