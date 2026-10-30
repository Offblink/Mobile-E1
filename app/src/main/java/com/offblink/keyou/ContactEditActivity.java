package com.offblink.keyou;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 5：联系人新增/编辑页。
 * EXTRA_ID 有值 = 编辑（预填并保存时 update），无值 = 新增（insert）。
 * 保存成功 setResult(RESULT_OK) + finish，列表页经 registerForActivityResult 刷新。
 */
public class ContactEditActivity extends KeyouActivity {

    public static final String EXTRA_ID = "contact_id";

    private static final String TAG = "Keyou/ContactEdit";

    private ContactDao dao;
    private long editId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_edit);
        Log.d(TAG, "onCreate");

        dao = new ContactDao(this);

        EditText etName = findViewById(R.id.et_name);
        EditText etPhone = findViewById(R.id.et_phone);
        EditText etEmail = findViewById(R.id.et_email);
        RadioGroup rgGender = findViewById(R.id.rg_gender);

        editId = getIntent().getLongExtra(EXTRA_ID, -1);
        if (editId >= 0) {
            // 编辑模式：按 id 查出预填
            for (Contact c : dao.query("")) {
                if (c.getId() == editId) {
                    etName.setText(c.getName());
                    etPhone.setText(c.getPhone());
                    etEmail.setText(c.getEmail());
                    if ("男".equals(c.getGender())) {
                        rgGender.check(R.id.rb_male);
                    } else if ("女".equals(c.getGender())) {
                        rgGender.check(R.id.rb_female);
                    }
                    break;
                }
            }
        }

        Button btn = findViewById(R.id.btn_save_contact);
        btn.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.contact_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            Contact c = new Contact();
            c.setId(editId);
            c.setName(name);
            c.setPhone(etPhone.getText().toString().trim());
            c.setEmail(etEmail.getText().toString().trim());
            int checkedId = rgGender.getCheckedRadioButtonId();
            c.setGender(checkedId == R.id.rb_male ? "男"
                    : checkedId == R.id.rb_female ? "女" : "保密");

            if (editId >= 0) {
                dao.update(c);
            } else {
                dao.insert(c);
            }
            setResult(RESULT_OK);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dao.close();
        Log.d(TAG, "onDestroy");
    }
}
