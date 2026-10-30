package com.offblink.keyou;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * ContactDao —— 参数化 SQL 增删改查 + 模糊查询 + 事务演示。
 *
 * 安全要点：
 * - 全部走参数化（? 占位符 / ContentValues），不用字符串拼接 SQL —— 防注入；
 * - Cursor 必须 close，查询统一 try-with-resources；
 * - 事务必须 setTransactionSuccessful，否则 endTransaction 时整体回滚。
 */
public class ContactDao {

    private static final String TAG = "Keyou/ContactDao";

    private final ContactDBHelper helper;

    public ContactDao(Context context) {
        this.helper = new ContactDBHelper(context);
    }

    /** 增：ContentValues 参数化写入，返回新行 id */
    public long insert(Contact c) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", c.getName());
        cv.put("phone", c.getPhone());
        cv.put("email", c.getEmail());
        cv.put("gender", c.getGender());
        long id = db.insert("contact", null, cv);
        Log.i(TAG, "insert: " + c.getName() + " -> id=" + id);
        return id;
    }

    /** 改：按 id 更新 */
    public int update(Contact c) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", c.getName());
        cv.put("phone", c.getPhone());
        cv.put("email", c.getEmail());
        cv.put("gender", c.getGender());
        int rows = db.update("contact", cv, "id = ?",
                new String[]{String.valueOf(c.getId())});
        Log.i(TAG, "update: id=" + c.getId() + " 影响 " + rows + " 行");
        return rows;
    }

    /** 删：按 id */
    public int delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        int rows = db.delete("contact", "id = ?", new String[]{String.valueOf(id)});
        Log.i(TAG, "delete: id=" + id + " 影响 " + rows + " 行");
        return rows;
    }

    /** 查：按姓名模糊查询（kw 为空查全部）；LIKE 参数化，防注入 */
    public List<Contact> query(String kw) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<Contact> list = new ArrayList<>();
        String selection = null;
        String[] args = null;
        if (kw != null && !kw.isEmpty()) {
            selection = "name LIKE ?";
            args = new String[]{"%" + kw + "%"};
        }
        // try-with-resources：Cursor 与骨架都保证关闭（防泄漏）
        try (Cursor cursor = db.query("contact", null, selection, args,
                null, null, "id DESC")) {
            while (cursor.moveToNext()) {
                Contact c = new Contact();
                c.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                c.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                c.setPhone(cursor.getString(cursor.getColumnIndexOrThrow("phone")));
                c.setEmail(cursor.getString(cursor.getColumnIndexOrThrow("email")));
                c.setGender(cursor.getString(cursor.getColumnIndexOrThrow("gender")));
                list.add(c);
            }
        }
        Log.i(TAG, "query(kw=" + kw + "): " + list.size() + " 条");
        return list;
    }

    /**
     * 事务演示：批量导入两条联系人，任一条失败则整体回滚。
     * 忘记 setTransactionSuccessful() 时，endTransaction 会静默回滚全部写入 ——
     * 这是思考题"事务不加 setTransactionSuccessful 会怎样"的实测答案。
     */
    public void importBatch(List<Contact> contacts) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Contact c : contacts) {
                ContentValues cv = new ContentValues();
                cv.put("name", c.getName());
                cv.put("phone", c.getPhone());
                cv.put("email", c.getEmail());
                cv.put("gender", c.getGender());
                db.insert("contact", null, cv);
            }
            db.setTransactionSuccessful();  // 没有这行 → endTransaction 时全部回滚
            Log.i(TAG, "importBatch: 事务提交，导入 " + contacts.size() + " 条");
        } finally {
            db.endTransaction();
        }
    }

    public void close() {
        helper.close();
    }
}
