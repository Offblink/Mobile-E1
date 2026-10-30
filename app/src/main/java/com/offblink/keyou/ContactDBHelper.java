package com.offblink.keyou;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLiteOpenHelper —— contact.db 与 contact 表。
 *
 * 版本升级演示：
 * - VERSION=2；onUpgrade 处理 oldV<2：ALTER TABLE contact ADD COLUMN gender。
 * - ⚠️ 教材样例的坑：onCreate 建的是
 *   不含 gender 的 v1 表、VERSION 却是 2 —— 全新安装时 onCreate 直接建库，
 *   onUpgrade 永远不触发，gender 列永远不会出现。
 *   正确姿势（本类）：onCreate 建"当前版本完整 schema"，onUpgrade 只负责
 *   把老库迁移到当前版本。
 */
public class ContactDBHelper extends SQLiteOpenHelper {

    private static final String TAG = "Keyou/ContactDB";

    public static final String DB_NAME = "contact.db";
    public static final int VERSION = 3;

    public ContactDBHelper(Context ctx) {
        super(ctx, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 当前版本（v2）完整 schema，含 gender
        db.execSQL("CREATE TABLE contact(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "phone TEXT," +
                "email TEXT," +
                "gender TEXT," +
                "avatar TEXT)");
        AppLog.i(TAG, "onCreate: 建表 contact（v3 schema，含 gender / avatar）");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // 参数化版本号升级路径：未来 VERSION=3 加列时接在 oldV<3 分支
        if (oldV < 2) {
            db.execSQL("ALTER TABLE contact ADD COLUMN gender TEXT");
            AppLog.i(TAG, "onUpgrade: " + oldV + " -> " + newV + "，新增 gender 列");
        }
        if (oldV < 3) {
            db.execSQL("ALTER TABLE contact ADD COLUMN avatar TEXT");
            AppLog.i(TAG, "onUpgrade: " + oldV + " -> " + newV + "，新增 avatar 列");
        }
    }
}
