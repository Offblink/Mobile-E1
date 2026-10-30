package com.offblink.keyou;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务 3：RecyclerView 联系人列表（模拟数据源）。
 * 任务 5 会把数据源换成 SQLite（ContactDao 查询），页面与适配器结构不变。
 */
public class ContactListActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/ContactList";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);
        Log.d(TAG, "onCreate");

        RecyclerView rv = findViewById(R.id.rv_contacts);
        // 必须显式设置 LayoutManager 与 Adapter，否则列表不显示（指导书排错点）
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<Contact> data = mockData();
        ContactAdapter adapter = new ContactAdapter(data, contact ->
                Toast.makeText(this,
                        getString(R.string.contact_click_toast, contact.getName()),
                        Toast.LENGTH_SHORT).show());
        rv.setAdapter(adapter);
        Log.i(TAG, "加载联系人 " + data.size() + " 条");
    }

    /** 模拟数据源（任务 5 换成 SQLite 查询） */
    private List<Contact> mockData() {
        List<Contact> list = new ArrayList<>();
        list.add(new Contact(1, "张三", "13800000001"));
        list.add(new Contact(2, "李四", "13800000002"));
        list.add(new Contact(3, "王五", "13800000003"));
        list.add(new Contact(4, "赵六", "13800000004"));
        list.add(new Contact(5, "孙七", "13800000005"));
        list.add(new Contact(6, "周八", "13800000006"));
        list.add(new Contact(7, "吴九", "13800000007"));
        list.add(new Contact(8, "郑十", "13800000008"));
        return list;
    }
}
