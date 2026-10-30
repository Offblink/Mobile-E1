package com.offblink.keyou;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

/**
 * 自定义 ContentProvider —— 把 的 contact.db 对外共享。
 *
 * 验收点：
 * - UriMatcher 匹配两种 URI：整表 /content://<authority>/contact 与单行 /contact/{id}；
 * - insert/query/update/delete 四方法；
 * - Manifest 注册 exported + read/write 权限。
 *
 * 安全：
 * - 自定义权限 protectionLevel="signature"：只有同签名应用可申请到（跨 App 演示
 *   用的两个 App 都是 debug 签名，自动授与；第三方应用拿不到）；
 * - 读写权限拆分（readPermission / writePermission），最小授权。
 */
public class ContactProvider extends ContentProvider {

    private static final String TAG = "Keyou/ContactProvider";

    public static final String AUTHORITY = "com.offblink.keyou.provider.contact";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/contact");

    private static final int MATCH_CONTACTS = 1;
    private static final int MATCH_CONTACT_ID = 2;

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI(AUTHORITY, "contact", MATCH_CONTACTS);
        URI_MATCHER.addURI(AUTHORITY, "contact/#", MATCH_CONTACT_ID);
    }

    private ContactDBHelper dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = new ContactDBHelper(getContext());
        AppLog.i(TAG, "onCreate: Provider 就绪, authority=" + AUTHORITY);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        switch (URI_MATCHER.match(uri)) {
            case MATCH_CONTACTS:
                return dbHelper.getReadableDatabase()
                        .query("contact", projection, selection, selectionArgs,
                                null, null, sortOrder);
            case MATCH_CONTACT_ID:
                String id = uri.getLastPathSegment();
                return dbHelper.getReadableDatabase()
                        .query("contact", projection, "id = ?",
                                new String[]{id}, null, null, sortOrder);
            default:
                throw new IllegalArgumentException("未知 URI: " + uri);
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case MATCH_CONTACTS:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".contact";
            case MATCH_CONTACT_ID:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".contact";
            default:
                throw new IllegalArgumentException("未知 URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (URI_MATCHER.match(uri) != MATCH_CONTACTS) {
            throw new IllegalArgumentException("insert 只支持整表 URI: " + uri);
        }
        long id = dbHelper.getWritableDatabase().insert("contact", null, values);
        Uri newUri = ContentUris.withAppendedId(CONTENT_URI, id);
        AppLog.i(TAG, "insert: id=" + id);
        // 通知内容观察者，reader 端 registerContentChangeListener 可感知变化
        Context ctx = getContext();
        if (ctx != null) {
            ctx.getContentResolver().notifyChange(newUri, null);
        }
        return newUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int rows;
        switch (URI_MATCHER.match(uri)) {
            case MATCH_CONTACTS:
                rows = dbHelper.getWritableDatabase()
                        .delete("contact", selection, selectionArgs);
                break;
            case MATCH_CONTACT_ID:
                rows = dbHelper.getWritableDatabase().delete("contact", "id = ?",
                        new String[]{uri.getLastPathSegment()});
                break;
            default:
                throw new IllegalArgumentException("未知 URI: " + uri);
        }
        AppLog.i(TAG, "delete: " + uri + " 影响 " + rows + " 行");
        return rows;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        int rows;
        switch (URI_MATCHER.match(uri)) {
            case MATCH_CONTACTS:
                rows = dbHelper.getWritableDatabase()
                        .update("contact", values, selection, selectionArgs);
                break;
            case MATCH_CONTACT_ID:
                rows = dbHelper.getWritableDatabase().update("contact", values, "id = ?",
                        new String[]{uri.getLastPathSegment()});
                break;
            default:
                throw new IllegalArgumentException("未知 URI: " + uri);
        }
        AppLog.i(TAG, "update: " + uri + " 影响 " + rows + " 行");
        return rows;
    }
}
