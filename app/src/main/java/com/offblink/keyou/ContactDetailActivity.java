package com.offblink.keyou;

import android.graphics.Rect;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

    /** -1 = 新增；> 0 = 编辑指定 id */
    public static final String EXTRA_ID = "contact_id";

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

    private TextView topTitle;
    private TextView saveButton;
    private View deleteButton;
    private TextView nameView;
    private TextView subView;
    private TextView avatarLetter;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_detail);
        Log.d(TAG, "onCreate");

        dao = new ContactDao(this);
        contactId = getIntent().getLongExtra(EXTRA_ID, -1);
        isNew = contactId <= 0;

        topTitle = findViewById(R.id.tv_top_title);
        saveButton = findViewById(R.id.btn_save);
        deleteButton = findViewById(R.id.btn_delete);
        nameView = findViewById(R.id.tv_name);
        subView = findViewById(R.id.tv_sub);
        avatarLetter = findViewById(R.id.tv_avatar_letter);

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
        updateHeader(contact.getName(), contact.getPhone(), contact.getEmail());
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

        Contact contact = new Contact();
        contact.setId(contactId);
        contact.setName(values.get(KEY_NAME));
        contact.setPhone(values.get(KEY_PHONE));
        contact.setEmail(values.get(KEY_EMAIL));
        contact.setGender(values.get(KEY_GENDER));

        int rows = dao.update(contact);
        if (rows <= 0) {
            revert();
            Toast.makeText(this, R.string.contacts_save_failed, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "update 影响 0 行，id=" + contactId);
            return;
        }
        render(contact);   // 用刚写入的值回填（去空白后的），并记为新的已保存快照
        setResult(RESULT_OK);
        Toast.makeText(this, R.string.contacts_saved, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "已回写 id=" + contactId + "，影响 " + rows + " 行");
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

        long id = dao.insert(contact);
        if (id <= 0) {
            Toast.makeText(this, R.string.contacts_save_failed, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "insert 失败，返回 id=" + id);
            return;
        }
        closing = true;
        setResult(RESULT_OK);
        Toast.makeText(this, R.string.contacts_saved, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "新增成功 id=" + id);
        finish();
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
                    int rows = dao.delete(contactId);
                    setResult(RESULT_OK);
                    Toast.makeText(this,
                            rows > 0 ? R.string.contact_deleted : R.string.contacts_delete_failed,
                            Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "删除 id=" + contactId + "，影响 " + rows + " 行");
                    finish();
                })
                .show();
    }

    // ================= 头部 / 退出 =================

    private void updateHeader(String name, String phone, String email) {
        String n = ContactStyle.emptyIfNull(name);
        nameView.setText(n.isEmpty() ? getString(R.string.contacts_name_placeholder) : n);
        avatarLetter.setText(n.isEmpty() ? "?" : n.substring(0, 1));

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
        Log.d(TAG, "onDestroy");
    }
}
