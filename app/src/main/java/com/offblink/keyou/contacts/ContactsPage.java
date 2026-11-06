package com.offblink.keyou.contacts;

import com.offblink.keyou.R;
import com.offblink.keyou.log.AppLog;
import com.offblink.keyou.net.HttpUtil;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.lang.reflect.Type;

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
 * - 种子：空库时用 dao.importBatch 真事务批量导入演示数据。
 */
public final class ContactsPage {

    private static final String TAG = "Keyou/ContactsPage";

    /** 当前挂着的实例：服务端地址变更后要靠它原地重拉（同一时刻只会有一个主界面） */
    private static ContactsPage attached;

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

    private final Gson gson = new Gson();

    private boolean firstLoad = true;
    private boolean firstResume = true;

    /** 详情页回传：新增/编辑/删除过就整表重刷 */
    private final ActivityResultLauncher<Intent> detailLauncher;

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
    }

    /**
     * 把同学录接到宿主 Activity 的 page_contacts 上；幂等（重复调用直接忽略）。
     * 必须在宿主 onCreate 内、STARTED 之前调用 —— 里面要用 registerForActivityResult。
     */
    public static void attach(AppCompatActivity host, View pageRoot) {
        if (host == null || pageRoot == null) {
            AppLog.w(TAG, "attach 参数为空，忽略");
            return;
        }
        RecyclerView rv = pageRoot.findViewById(R.id.rv_contacts);
        if (rv == null) {
            AppLog.e(TAG, "page_contacts 里找不到 rv_contacts，装配失败");
            return;
        }
        if (rv.getAdapter() != null) {
            return;   // 已经接过一次（配置变更重入/重复调用），别装两套
        }
        ContactsPage page = new ContactsPage(host, pageRoot);
        attached = page;
        page.bind();
    }

    /** 服务端地址变更后调用：按新地址重拉班级成员（没挂上就什么都不做） */
    public static void refresh() {
        ContactsPage page = attached;
        if (page != null) {
            page.reload();
        }
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
                openDetail(contact);
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
            fab.setOnClickListener(v -> openNewDetail());
        }

        seedIfEmpty();
        reload();
        observeLifecycle();
        AppLog.i(TAG, "同学录已接入 tab");
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
                attached = null;
                dao.close();
            }
        });
    }

    // ================= 数据 =================

    /** 整表重刷：本地 SQLite + 服务端班级成员两路合并（本地查询同步，服务端异步回调） */
    private void reload() {
        if (recyclerView == null) {
            return;
        }
        List<Contact> local = dao.query("");
        applyList(local, null, local.size(), 0);
        fetchClassmates(local);
    }

    /** 拉取班级成员并与人本地列表合并后刷新界面 */
    private void fetchClassmates(final List<Contact> local) {
        HttpUtil.get(host, "/api/mates?page=1&size=200", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                List<Contact> all = new ArrayList<>();
                int classCount = 0;
                int mergedCount = 0;
                try {
                    JSONObject root = new JSONObject(body);
                    if (root.getInt("code") == 0) {
                        Type type = new TypeToken<List<Mate>>() {
                        }.getType();
                        List<Mate> mates = gson.fromJson(
                                root.getJSONObject("data").getJSONArray("list").toString(), type);
                        java.util.Set<String> keys = new java.util.HashSet<>();
                        for (Mate mate : mates) {
                            Contact c = fromMate(mate);
                            all.add(c);
                            keys.add(dedupeKey(c));
                            classCount++;
                        }
                        // 本地联系人：同号/同名的班级成员优先，避免一个人出现两次；
                        // 重复时不丢数据 —— 把本地补上的头像/邮箱并进那条班级记录
                        java.util.Map<String, Contact> byKey = new java.util.HashMap<>();
                        for (Contact c : all) {
                            byKey.put(dedupeKey(c), c);
                        }
                        for (Contact c : local) {
                            String key = dedupeKey(c);
                            Contact existing = byKey.get(key);
                            if (existing != null) {
                                mergedCount++;
                                if (TextUtils.isEmpty(existing.getAvatar())
                                        && !TextUtils.isEmpty(c.getAvatar())) {
                                    existing.setAvatar(c.getAvatar());
                                }
                                if (TextUtils.isEmpty(existing.getEmail())) {
                                    existing.setEmail(c.getEmail());
                                }
                                if (TextUtils.isEmpty(existing.getGender())) {
                                    existing.setGender(c.getGender());
                                }
                            } else if (keys.add(key)) {
                                all.add(c);
                                byKey.put(key, c);
                            }
                        }
                    } else {
                        all.addAll(local);
                    }
                } catch (Exception e) {
                    AppLog.w(TAG, "班级成员解析失败，只显示本地联系人", e);
                    all.addAll(local);
                }
                applyList(all, classCount, local.size(), mergedCount);
            }

            @Override
            public void onError(String message, Exception e) {
                AppLog.w(TAG, "班级成员拉取失败，只显示本地联系人: " + message);
                applyList(local, null, local.size(), 0);
            }
        });
    }

    /** 去重键：优先手机号，其次姓名 */
    private static String dedupeKey(Contact c) {
        String phone = normalizePhone(c.getPhone());
        if (!phone.isEmpty()) {
            return "p:" + phone;
        }
        return "n:" + ContactStyle.emptyIfNull(c.getName());
    }

    private Contact fromMate(Mate mate) {
        Contact c = new Contact();
        c.setRemoteId(mate.getId() == null ? -1 : mate.getId());
        c.setName(mate.getName());
        c.setPhone(mate.getPhone());
        c.setEmail(mate.getEmail());
        c.setGender(mate.getGender());
        c.setRemark(mate.getRemark());
        c.setSource(Contact.SOURCE_CLASS);
        c.setAvatar(ContactAvatarStore.get(host, c.getRemoteId()));
        return c;
    }

    private void applyList(List<Contact> data, Integer classCount, int localRows, int mergedCount) {
        adapter.setData(data);
        decoration.resetSection();
        indexBar.setAvailableLetters(adapter.availableLetters());
        recyclerView.invalidate();

        int total = data.size();
        int classes = classCount == null ? 0 : classCount;
        if (classCount == null) {
            for (Contact c : data) {
                if (c.isFromClass()) {
                    classes++;
                }
            }
        }
        // 「本地」是本地库里的行数，不是列表里剩下多少条本地行：
        // 同号同名的本地联系人会并进班级那条（班级优先），旧口径会把它算成 0。
        boolean empty = data.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (mergedCount > 0) {
            subtitle.setText(host.getString(R.string.contacts_count_merged,
                    total, localRows, classes, mergedCount));
        } else {
            subtitle.setText(host.getString(R.string.contacts_count_mixed,
                    total, localRows, classes));
        }

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
                recyclerView.scrollToPosition(0);
            }
        }
        firstLoad = false;
        AppLog.i(TAG, "列表已刷新：共 " + total + " 人（本地库 " + localRows
                + " / 班级 " + classes + " / 已合并 " + mergedCount + "）");
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
        AppLog.i(TAG, "空库，事务导入种子数据 " + seeds.size() + " 条");
    }

    // ================= 跳转 / 删除 =================

    /** 本地联系人进本地详情；班级成员把字段一起带过去，保存走服务端 */
    private void openDetail(Contact contact) {
        Intent intent = new Intent(host, ContactDetailActivity.class);
        if (contact.isFromClass()) {
            intent.putExtra(ContactDetailActivity.EXTRA_SOURCE, Contact.SOURCE_CLASS);
            intent.putExtra(ContactDetailActivity.EXTRA_REMOTE_ID, contact.getRemoteId());
            intent.putExtra(ContactDetailActivity.EXTRA_NAME, contact.getName());
            intent.putExtra(ContactDetailActivity.EXTRA_PHONE, contact.getPhone());
            intent.putExtra(ContactDetailActivity.EXTRA_EMAIL, contact.getEmail());
            intent.putExtra(ContactDetailActivity.EXTRA_GENDER, contact.getGender());
            intent.putExtra(ContactDetailActivity.EXTRA_REMARK, contact.getRemark());
        } else {
            intent.putExtra(ContactDetailActivity.EXTRA_SOURCE, Contact.SOURCE_LOCAL);
            intent.putExtra(ContactDetailActivity.EXTRA_ID, contact.getId());
        }
        detailLauncher.launch(intent);
    }

    /**
     * FAB：直接进新增页，不再先弹「本地同学 / 班级成员」选择框。
     * 来源是表单里的「来源」行（本地同学 / 班级成员），保存时按它分派到 SQLite 或 /api/mate。
     */
    private void openNewDetail() {
        Intent intent = new Intent(host, ContactDetailActivity.class);
        intent.putExtra(ContactDetailActivity.EXTRA_ID, -1L);
        detailLauncher.launch(intent);
    }

    /** 长按删除：本地走 SQLite，班级成员走 DELETE /api/mate/{id} */
    private void confirmDelete(final Contact contact) {
        if (contact.isFromClass()) {
            confirmDeleteRemote(contact);
            return;
        }
        new MaterialAlertDialogBuilder(host)
                .setTitle(R.string.contact_delete_title)
                .setMessage(host.getString(R.string.contact_delete_message,
                        ContactStyle.emptyIfNull(contact.getName())))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.contact_delete_ok, (dialog, which) -> {
                    int rows = dao.delete(contact.getId());
                    deleteAvatarFile(contact.getAvatar());
                    reload();
                    Toast.makeText(host,
                            rows > 0 ? R.string.contact_deleted : R.string.contacts_delete_failed,
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** 删联系人时顺手删掉它的头像文件，别在私有目录里留孤儿 */
    private static void deleteAvatarFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        java.io.File file = new java.io.File(path);
        if (file.exists() && !file.delete()) {
            AppLog.w(TAG, "头像删除失败: " + path);
        }
    }

    private void confirmDeleteRemote(final Contact contact) {
        new MaterialAlertDialogBuilder(host)
                .setTitle(R.string.contact_delete_title)
                .setMessage(host.getString(R.string.contact_delete_message,
                        ContactStyle.emptyIfNull(contact.getName())))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.contact_delete_ok, (dialog, which) ->
                        HttpUtil.delete(host, "/api/mate/" + contact.getRemoteId(),
                                new HttpUtil.Callback() {
                                    @Override
                                    public void onSuccess(String body) {
                                        ContactAvatarStore.remove(host, contact.getRemoteId());
                                        Toast.makeText(host, R.string.mate_deleted,
                                                Toast.LENGTH_SHORT).show();
                                        reload();
                                    }

                                    @Override
                                    public void onError(String message, Exception e) {
                                        Toast.makeText(host, host.getString(
                                                R.string.mate_network_error, message),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
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
