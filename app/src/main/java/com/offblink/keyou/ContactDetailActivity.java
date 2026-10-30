package com.offblink.keyou;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同学详情页 / 新增页（同一个布局，两种模式）：
 * - 编辑模式（EXTRA_ID > 0）：表格格子失焦或回车就 UPDATE 回写本地 contact 表，
 *   成功后用刚写入的值回填，并 toast 提示；
 * - 新增模式（EXTRA_ID = -1）：顶栏出现「保存」，校验姓名非空后 INSERT。
 *
 * 表格用法（TableLayout 行列线）：TableLayout 底色就是网格线色，每个格子只留
 * 「右 + 下」1dp 外边距，第一列补左、第一行补上 —— 这样外框与内线恒为 1dp，
 * 不会出现「有粗有细」；格子高度用 MATCH_PARENT，行间不会露灰带。
 *
 * 数据只走 ContactDao（本地 SQLite），不涉及服务端。
 */
public class ContactDetailActivity extends KeyouActivity {

    /** -1 = 新增；> 0 = 编辑指定 id（本地联系人） */
    public static final String EXTRA_ID = "contact_id";
    /** 来源：0 = 本地 SQLite，1 = 服务端班级成员 */
    public static final String EXTRA_SOURCE = "contact_source";
    /** 班级成员的服务端 id 与字段（列表页直接带过来，省一次请求） */
    public static final String EXTRA_REMOTE_ID = "contact_remote_id";
    public static final String EXTRA_NAME = "contact_name";
    public static final String EXTRA_PHONE = "contact_phone";
    public static final String EXTRA_EMAIL = "contact_email";
    public static final String EXTRA_GENDER = "contact_gender";
    public static final String EXTRA_REMARK = "contact_remark";

    private static final String TAG = "Keyou/ContactDetail";

    private static final String KEY_NAME = "name";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_GENDER = "gender";

    private static final String GENDER_SECRET = "保密";

    private final Map<String, EditText> cells = new LinkedHashMap<>();
    private final Map<String, String> saved = new LinkedHashMap<>();

    private ContactDao dao;
    private long contactId = -1;
    private boolean isNew;
    /** 程序回填期间为 true：别把 setText 当成用户输入 */
    private boolean silent;
    /** 正在关页（删除/返回）：此后不再回写，避免删完又 update 出一条幽灵记录 */
    private boolean closing;

    private TextView genderCell;
    private String genderValue = GENDER_SECRET;

    private int source = Contact.SOURCE_LOCAL;
    private long remoteId = -1;
    private String remark;
    private String avatarPath;
    private ImageView avatarImage;
    private TextView sourceBadge;
    private final Gson gson = new Gson();

    private TextView topTitle;
    private TextView saveButton;
    private View deleteButton;
    private TextView nameView;
    private TextView subView;
    private TextView avatarLetter;

    /** 相册选头像（Photo Picker，无需存储权限） */
    private final ActivityResultLauncher<PickVisualMediaRequest> avatarPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    saveAvatar(uri);
                }
            });

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_detail);
        AppLog.d(TAG, "onCreate");

        dao = new ContactDao(this);
        contactId = getIntent().getLongExtra(EXTRA_ID, -1);
        source = getIntent().getIntExtra(EXTRA_SOURCE, Contact.SOURCE_LOCAL);
        remoteId = getIntent().getLongExtra(EXTRA_REMOTE_ID, -1);
        isNew = source == Contact.SOURCE_LOCAL && contactId <= 0;

        topTitle = findViewById(R.id.tv_top_title);
        saveButton = findViewById(R.id.btn_save);
        deleteButton = findViewById(R.id.btn_delete);
        nameView = findViewById(R.id.tv_name);
        subView = findViewById(R.id.tv_sub);
        avatarLetter = findViewById(R.id.tv_avatar_letter);
        avatarImage = findViewById(R.id.iv_avatar);
        sourceBadge = findViewById(R.id.tv_source);
        findViewById(R.id.fl_contact_avatar).setOnClickListener(v -> avatarPicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        buildTable(findViewById(R.id.tl_info));

        // 基类已经把 btn_back 接到 finish()；这里覆盖成「先回写再关页」
        findViewById(R.id.btn_back).setOnClickListener(v -> handleBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });

        if (isNew) {
            setupNewMode();
        } else {
            setupEditMode();
        }
    }

    // ================= 表格 =================

    private void buildTable(TableLayout table) {
        table.removeAllViews();
        int surface = ContactStyle.color(this, ContactStyle.ATTR_SURFACE);
        int onSurfaceVariant = ContactStyle.color(this, ContactStyle.ATTR_ON_SURFACE_VARIANT);
        // 底色 = 网格线：格子之间只留 1dp 缝，缝里露出来的就是这个色
        table.setBackgroundColor(ContactStyle.blend(surface, onSurfaceVariant, 0.28f));

        int one = ContactStyle.dp(this, 1);
        int labelWidth = ContactStyle.dp(this, 88);

        addTextRow(table, 0, R.string.contacts_label_name, KEY_NAME,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                one, labelWidth);
        addTextRow(table, 1, R.string.contacts_label_phone, KEY_PHONE,
                InputType.TYPE_CLASS_PHONE, one, labelWidth);
        addTextRow(table, 2, R.string.contacts_label_email, KEY_EMAIL,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                one, labelWidth);
        addGenderRow(table, 3, one, labelWidth);
    }

    private void addTextRow(TableLayout table, int index, int labelRes, String key,
                            int inputType, int one, int labelWidth) {
        TableRow row = new TableRow(this);
        int topMargin = index == 0 ? one : 0;

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(13);
        label.setTextColor(ContactStyle.color(this, ContactStyle.ATTR_ON_SURFACE_VARIANT));
        label.setGravity(Gravity.CENTER);
        label.setBackgroundResource(R.drawable.contact_bg_cell_label);

        EditText cell = new EditText(this);
        cell.setTextSize(13);
        cell.setSingleLine(true);
        // NO_SUGGESTIONS：否则邮箱/电话会被拼写检查画红波浪线
        cell.setInputType(inputType | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        cell.setImeOptions(EditorInfo.IME_ACTION_DONE);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setTextColor(ContactStyle.color(this, ContactStyle.ATTR_ON_SURFACE));
        cell.setBackgroundResource(R.drawable.contact_bg_cell_value);
        cell.setPadding(ContactStyle.dp(this, 10), 0, ContactStyle.dp(this, 6), 0);
        cell.setMinHeight(ContactStyle.dp(this, 44));

        TableRow.LayoutParams labelParams =
                new TableRow.LayoutParams(labelWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        labelParams.setMargins(one, topMargin, one, one);

        TableRow.LayoutParams cellParams =
                new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        cellParams.setMargins(0, topMargin, one, one);

        row.addView(label, labelParams);
        row.addView(cell, cellParams);
        table.addView(row, new TableLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cells.put(key, cell);
        wireCell(cell, key);
    }

    /** 性别不是自由文本：点一下弹出三选一，选中即回写 */
    private void addGenderRow(TableLayout table, int index, int one, int labelWidth) {
        TableRow row = new TableRow(this);
        int topMargin = index == 0 ? one : 0;

        TextView label = new TextView(this);
        label.setText(R.string.contacts_label_gender);
        label.setTextSize(13);
        label.setTextColor(ContactStyle.color(this, ContactStyle.ATTR_ON_SURFACE_VARIANT));
        label.setGravity(Gravity.CENTER);
        label.setBackgroundResource(R.drawable.contact_bg_cell_label);

        genderCell = new TextView(this);
        genderCell.setTextSize(13);
        genderCell.setSingleLine(true);
        genderCell.setTextColor(ContactStyle.color(this, ContactStyle.ATTR_ON_SURFACE));
        genderCell.setBackgroundResource(R.drawable.contact_bg_cell_value_ripple);
        genderCell.setGravity(Gravity.CENTER_VERTICAL);
        genderCell.setPadding(ContactStyle.dp(this, 10), 0, ContactStyle.dp(this, 6), 0);
        genderCell.setMinHeight(ContactStyle.dp(this, 44));
        genderCell.setText(GENDER_SECRET);
        genderCell.setOnClickListener(v -> pickGender());

        TableRow.LayoutParams labelParams =
                new TableRow.LayoutParams(labelWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        labelParams.setMargins(one, topMargin, one, one);

        // 与可编辑格子同样高：TableRow 会按行高重测，不会露出行间灰带
        TableRow.LayoutParams cellParams =
                new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        cellParams.setMargins(0, topMargin, one, one);

        row.addView(label, labelParams);
        row.addView(genderCell, cellParams);
        table.addView(row, new TableLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void wireCell(final EditText cell, final String key) {
        cell.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus || silent) {
                return;
            }
            if (KEY_NAME.equals(key)) {
                updateHeader(cell.getText().toString().trim(),
                        textOf(KEY_PHONE), textOf(KEY_EMAIL));
            }
            commit();
        });

        cell.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                view.clearFocus();   // 清焦点触发上面的失焦提交
                return true;
            }
            return false;
        });

        if (KEY_NAME.equals(key)) {
            // 姓名边打边同步头部（头像首字 + 大标题），体验上才跟得上
            cell.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (silent) {
                        return;
                    }
                    updateHeader(s.toString().trim(), textOf(KEY_PHONE), textOf(KEY_EMAIL));
                }
            });
        }
    }

    // ================= 模式 =================

    private void setupEditMode() {
        topTitle.setText(R.string.contacts_detail_title);
        saveButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setOnClickListener(v -> confirmDelete());

        if (source == Contact.SOURCE_CLASS) {
            // 班级成员：字段由列表页带过来，改动直接 PUT 回服务端
            Contact remote = new Contact();
            remote.setName(getIntent().getStringExtra(EXTRA_NAME));
            remote.setPhone(getIntent().getStringExtra(EXTRA_PHONE));
            remote.setEmail(getIntent().getStringExtra(EXTRA_EMAIL));
            remote.setGender(getIntent().getStringExtra(EXTRA_GENDER));
            remote.setRemark(getIntent().getStringExtra(EXTRA_REMARK));
            remote.setSource(Contact.SOURCE_CLASS);
            remote.setRemoteId(remoteId);
            remark = remote.getRemark();
            avatarPath = ContactAvatarStore.get(this, remoteId);
            remote.setAvatar(avatarPath);
            render(remote);
            return;
        }

        Contact found = null;
        // 数据层没有按 id 取单条的接口（ContactDao 未改动），整表查一次再匹配
        for (Contact contact : dao.query("")) {
            if (contact.getId() == contactId) {
                found = contact;
                break;
            }
        }
        if (found == null) {
            Toast.makeText(this, R.string.contacts_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        render(found);
    }

    private void setupNewMode() {
        topTitle.setText(R.string.contacts_new_title);
        saveButton.setVisibility(View.VISIBLE);
        deleteButton.setVisibility(View.GONE);
        saveButton.setOnClickListener(v -> saveNew());

        silent = true;
        for (EditText cell : cells.values()) {
            cell.setText("");
        }
        genderValue = GENDER_SECRET;
        genderCell.setText(genderValue);
        silent = false;
        saved.clear();
        updateHeader("", "", "");
    }

    // ================= 绑定 / 回写 =================

    /** 用一条 Contact 填满表格与头部，并把当前值记为「已保存快照」 */
    private void render(Contact contact) {
        silent = true;
        cells.get(KEY_NAME).setText(ContactStyle.emptyIfNull(contact.getName()));
        cells.get(KEY_PHONE).setText(ContactStyle.emptyIfNull(contact.getPhone()));
        cells.get(KEY_EMAIL).setText(ContactStyle.emptyIfNull(contact.getEmail()));
        String gender = ContactStyle.emptyIfNull(contact.getGender());
        genderValue = gender.isEmpty() ? GENDER_SECRET : gender;
        genderCell.setText(genderValue);
        silent = false;

        saved.clear();
        saved.putAll(values());
        remark = contact.getRemark();
        avatarPath = contact.getAvatar();
        renderAvatar(contact.getName());
        renderSource();
        updateHeader(contact.getName(), contact.getPhone(), contact.getEmail());
    }

    /** 有头像就圆裁显示（盖住首字），没有就露首字 */
    private void renderAvatar(String name) {
        String text = ContactStyle.emptyIfNull(name);
        avatarLetter.setText(text.isEmpty() ? "?" : new String(Character.toChars(text.codePointAt(0))));
        if (avatarPath == null || !new File(avatarPath).exists()) {
            avatarImage.setVisibility(View.GONE);
            return;
        }
        avatarImage.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(new File(avatarPath))
                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                .into(avatarImage);
    }

    private void renderSource() {
        boolean fromClass = source == Contact.SOURCE_CLASS;
        sourceBadge.setText(fromClass
                ? R.string.contacts_source_class : R.string.contacts_source_local);
        sourceBadge.setBackgroundResource(fromClass
                ? R.drawable.bg_badge_class : R.drawable.bg_badge_local);
    }

    private Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(KEY_NAME, textOf(KEY_NAME));
        values.put(KEY_PHONE, textOf(KEY_PHONE));
        values.put(KEY_EMAIL, textOf(KEY_EMAIL));
        values.put(KEY_GENDER, genderValue);
        return values;
    }

    private String textOf(String key) {
        EditText cell = cells.get(key);
        return cell == null ? "" : cell.getText().toString().trim();
    }

    private boolean isDirty() {
        return !saved.equals(values());
    }

    /** 回滚到上次保存的值（姓名为空 / 写库失败时用） */
    private void revert() {
        silent = true;
        for (String key : new String[]{KEY_NAME, KEY_PHONE, KEY_EMAIL}) {
            EditText cell = cells.get(key);
            String value = saved.get(key);
            cell.setText(value == null ? "" : value);
        }
        String gender = saved.get(KEY_GENDER);
        genderValue = gender == null || gender.isEmpty() ? GENDER_SECRET : gender;
        genderCell.setText(genderValue);
        silent = false;
        updateHeader(saved.get(KEY_NAME), saved.get(KEY_PHONE), saved.get(KEY_EMAIL));
    }

    /** 编辑模式的提交：失焦 / 回车 / 点空白 / 返回前都会走到这里（本地库同步写） */
    private void commit() {
        if (isNew || closing || !isDirty()) {
            return;
        }
        Map<String, String> values = values();
        if (values.get(KEY_NAME).isEmpty()) {
            revert();
            Toast.makeText(this, R.string.contacts_name_required_reverted, Toast.LENGTH_SHORT).show();
            return;
        }

        if (source == Contact.SOURCE_CLASS) {
            commitRemote(values);
            return;
        }

        Contact contact = new Contact();
        contact.setId(contactId);
        contact.setName(values.get(KEY_NAME));
        contact.setPhone(values.get(KEY_PHONE));
        contact.setEmail(values.get(KEY_EMAIL));
        contact.setGender(values.get(KEY_GENDER));
        contact.setAvatar(avatarPath);

        int rows = dao.update(contact);
        if (rows <= 0) {
            revert();
            Toast.makeText(this, R.string.contacts_save_failed, Toast.LENGTH_SHORT).show();
            AppLog.w(TAG, "update 影响 0 行，id=" + contactId);
            return;
        }
        render(contact);   // 用刚写入的值回填（去空白后的），并记为新的已保存快照
        setResult(RESULT_OK);
        Toast.makeText(this, R.string.contacts_saved, Toast.LENGTH_SHORT).show();
        AppLog.i(TAG, "已回写 id=" + contactId + "，影响 " + rows + " 行");
    }

    /** 新增模式：校验姓名非空 → insert → 回传 RESULT_OK */
    private void saveNew() {
        if (closing) {
            return;
        }
        Map<String, String> values = values();
        if (values.get(KEY_NAME).isEmpty()) {
            Toast.makeText(this, R.string.contact_name_required, Toast.LENGTH_SHORT).show();
            return;
        }

        Contact contact = new Contact();
        contact.setName(values.get(KEY_NAME));
        contact.setPhone(values.get(KEY_PHONE));
        contact.setEmail(values.get(KEY_EMAIL));
        contact.setGender(values.get(KEY_GENDER));
        contact.setAvatar(avatarPath);

        long id = dao.insert(contact);
        if (id <= 0) {
            Toast.makeText(this, R.string.contacts_save_failed, Toast.LENGTH_SHORT).show();
            AppLog.w(TAG, "insert 失败，返回 id=" + id);
            return;
        }
        closing = true;
        setResult(RESULT_OK);
        Toast.makeText(this, R.string.contacts_saved, Toast.LENGTH_SHORT).show();
        AppLog.i(TAG, "新增成功 id=" + id);
        finish();
    }

    /** 班级成员：PUT /api/mate（Gson 序列化，改完用新值回填） */
    private void commitRemote(Map<String, String> values) {
        Mate mate = new Mate();
        mate.setId(remoteId);
        mate.setName(values.get(KEY_NAME));
        mate.setPhone(values.get(KEY_PHONE));
        mate.setEmail(values.get(KEY_EMAIL));
        mate.setGender(values.get(KEY_GENDER));
        mate.setRemark(remark);
        HttpUtil.putJson(this, "/api/mate", gson.toJson(mate), new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                try {
                    if (new JSONObject(body).getInt("code") != 0) {
                        revert();
                        Toast.makeText(ContactDetailActivity.this,
                                new JSONObject(body).getString("message"),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                } catch (Exception e) {
                    AppLog.w(TAG, "PUT 响应解析失败", e);
                }
                Contact updated = new Contact();
                updated.setName(values.get(KEY_NAME));
                updated.setPhone(values.get(KEY_PHONE));
                updated.setEmail(values.get(KEY_EMAIL));
                updated.setGender(values.get(KEY_GENDER));
                updated.setRemark(remark);
                updated.setSource(Contact.SOURCE_CLASS);
                updated.setRemoteId(remoteId);
                updated.setAvatar(avatarPath);
                render(updated);
                setResult(RESULT_OK);
                Toast.makeText(ContactDetailActivity.this, R.string.contacts_saved,
                        Toast.LENGTH_SHORT).show();
                AppLog.i(TAG, "班级成员已回写 id=" + remoteId);
            }

            @Override
            public void onError(String message, Exception e) {
                revert();
                Toast.makeText(ContactDetailActivity.this,
                        getString(R.string.mate_network_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** 班级成员删除：DELETE /api/mate/{id} */
    private void deleteRemote() {
        HttpUtil.delete(this, "/api/mate/" + remoteId, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                ContactAvatarStore.remove(ContactDetailActivity.this, remoteId);
                setResult(RESULT_OK);
                Toast.makeText(ContactDetailActivity.this, R.string.mate_deleted,
                        Toast.LENGTH_SHORT).show();
                AppLog.i(TAG, "删除班级成员 id=" + remoteId);
                finish();
            }

            @Override
            public void onError(String message, Exception e) {
                closing = false;
                Toast.makeText(ContactDetailActivity.this,
                        getString(R.string.mate_network_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** 相册选图 → 缩到 512px → 本地联系人写库 / 班级成员记在 ContactAvatarStore */
    private void saveAvatar(Uri uri) {
        File target = ContactAvatarStore.newAvatarFile(this);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalStateException("openInputStream 返回 null");
            }
            byte[] raw = in.readAllBytes();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 512) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
            if (bitmap == null) {
                throw new IllegalStateException("图片解码失败");
            }
            try (FileOutputStream out = new FileOutputStream(target)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            bitmap.recycle();

            String previous = avatarPath;
            avatarPath = target.getAbsolutePath();
            if (source == Contact.SOURCE_CLASS) {
                ContactAvatarStore.put(this, remoteId, avatarPath);
            } else if (!isNew) {
                Contact contact = new Contact();
                contact.setId(contactId);
                contact.setName(textOf(KEY_NAME));
                contact.setPhone(textOf(KEY_PHONE));
                contact.setEmail(textOf(KEY_EMAIL));
                contact.setGender(genderValue);
                contact.setAvatar(avatarPath);
                dao.update(contact);
                setResult(RESULT_OK);
            }
            if (previous != null && !previous.equals(avatarPath)) {
                File old = new File(previous);
                if (old.exists() && !old.delete()) {
                    AppLog.w(TAG, "旧头像删除失败: " + previous);
                }
            }
            renderAvatar(textOf(KEY_NAME));
            Toast.makeText(this, R.string.avatar_saved, Toast.LENGTH_SHORT).show();
            AppLog.i(TAG, "头像已更新: " + avatarPath);
        } catch (Exception e) {
            AppLog.e(TAG, "头像保存失败", e);
            Toast.makeText(this, R.string.avatar_pick_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void pickGender() {
        if (silent) {
            return;
        }
        final String[] options = {
                getString(R.string.gender_male),
                getString(R.string.gender_female),
                getString(R.string.gender_secret)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.contacts_gender_pick)
                .setItems(options, (dialog, which) -> {
                    genderValue = options[which];
                    genderCell.setText(genderValue);
                    commit();
                })
                .show();
    }

    // ================= 删除 =================

    private void confirmDelete() {
        if (isNew) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.contact_delete_title)
                .setMessage(getString(R.string.contact_delete_message,
                        ContactStyle.emptyIfNull(textOf(KEY_NAME))))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.contact_delete_ok, (dialog, which) -> {
                    closing = true;   // 先挡住失焦回写，再删
                    if (source == Contact.SOURCE_CLASS) {
                        deleteRemote();
                        return;
                    }
                    int rows = dao.delete(contactId);
                    if (avatarPath != null) {
                        File old = new File(avatarPath);
                        if (old.exists() && !old.delete()) {
                            AppLog.w(TAG, "头像删除失败: " + avatarPath);
                        }
                    }
                    setResult(RESULT_OK);
                    Toast.makeText(this,
                            rows > 0 ? R.string.contact_deleted : R.string.contacts_delete_failed,
                            Toast.LENGTH_SHORT).show();
                    AppLog.i(TAG, "删除 id=" + contactId + "，影响 " + rows + " 行");
                    finish();
                })
                .show();
    }

    // ================= 头部 / 退出 =================

    private void updateHeader(String name, String phone, String email) {
        String n = ContactStyle.emptyIfNull(name);
        nameView.setText(n.isEmpty() ? getString(R.string.contacts_name_placeholder) : n);
        renderAvatar(n);

        String p = ContactStyle.emptyIfNull(phone);
        String e = ContactStyle.emptyIfNull(email);
        String sub = p.isEmpty() ? e : (e.isEmpty() ? p : p + " · " + e);
        subView.setText(sub.isEmpty() ? getString(R.string.contacts_value_empty) : sub);
    }

    private void handleBack() {
        if (closing) {
            return;
        }
        if (!isNew) {
            commit();     // 先把停在格子里的改动回写（本地库同步写，返回时已完成）
        }
        closing = true;
        finish();
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        View focused = getCurrentFocus();
        if (imm != null && focused != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    /**
     * 点表格外的空白处主动清焦点 —— Android 不会自动让 EditText 失焦，
     * 「点空白即回写」全靠这里兜住。
     */
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                Rect rect = new Rect();
                focused.getGlobalVisibleRect(rect);
                if (!rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    focused.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dao.close();
        AppLog.d(TAG, "onDestroy");
    }
}
