package com.offblink.keyou.chat;

import com.offblink.keyou.R;
import com.offblink.keyou.log.AppLog;
import com.offblink.keyou.net.HttpUtil;
import com.offblink.keyou.notice.NoticeListActivity;
import com.offblink.keyou.notice.NoticeStore;
import com.offblink.keyou.util.Streams;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「班级群」Tab 内嵌的聊天面板：消息时间线 + 输入行 + 标题行「上传」+ 左下公告 FAB。
 *
 * 时间线由两条服务端数据合并而成（都按 "yyyy-MM-dd HH:mm" 排序，文件就像消息一样插在中间）：
 * - GET /api/messages?after=<id>&size=100  文本消息，id 升序增量；
 * - GET /api/files                         群文件，每轮全量（文件少，简单可靠）；
 *   每项渲染成卡片：本机有副本（自己上传的 / 下载过的）→ 点击直接打开，
 *   否则 → 点击流式下载到 files/chat/downloads，下载完卡片动作变「点击打开」。
 *
 * 公告：GET /api/notices?size=1 只看最新 id，和 NoticeStore.lastSeenId 比出未读；
 * 左下 FAB 红点提示，点进去是 {@link NoticeListActivity}（列表里未读行带红点）。
 */
public final class ChatPanel {

    private static final String TAG = "Keyou/Chat";

    /** 增量拉取间隔：够电脑端发完消息基本"秒到"，又不至于把服务端打满 */
    private static final long POLL_INTERVAL_MS = 4000L;

    /** 必须与 AndroidManifest 里 FileProvider 的 authorities 一致 */
    private static final String FILE_PROVIDER_AUTHORITY = "com.offblink.keyou.fileprovider";

    private final List<ChatMessage> texts = new ArrayList<>();
    private final List<ChatFile> files = new ArrayList<>();
    /** 适配器直接读这个列表：文本 + 文件卡片，按时间排好 */
    private final List<ChatMessage> entries = new ArrayList<>();
    private final Set<String> downloading = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ChatAdapter adapter;
    private AppCompatActivity host;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private EditText etInput;
    private Button btnUpload;
    private View noticeDot;
    /** 选文件（SAF）：拿 Uri → 落到 cacheDir → multipart 上传 */
    private ActivityResultLauncher<String[]> filePicker;
    private String sender = "我";
    /** 已收到的最大消息 id，增量拉取游标 */
    private long lastId;
    private boolean polling;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!polling) {
                return;
            }
            fetchNew(false);
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public ChatPanel() {
    }

    /** @param myName 本机昵称，作为发消息时的 sender / 文件卡片的「自己」判据 */
    public void attach(AppCompatActivity host, View root, String myName) {
        this.host = host;
        if (myName != null && !myName.trim().isEmpty()) {
            this.sender = myName.trim();
        }

        recyclerView = root.findViewById(R.id.rv_messages);
        layoutManager = new LinearLayoutManager(host);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ChatAdapter(entries, this::onFileClick);
        recyclerView.setAdapter(adapter);

        etInput = root.findViewById(R.id.et_message);
        Button btnSend = root.findViewById(R.id.btn_send);

        Runnable send = () -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(host, R.string.chat_empty_tip, Toast.LENGTH_SHORT).show();
                return;
            }
            sendToServer(text);
        };
        btnSend.setOnClickListener(v -> send.run());
        // 键盘上的「发送」键也能直接发（回车不换行）
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                send.run();
                return true;
            }
            return false;
        });

        // 上传群文件：SAF 选一个文件，拿 Uri 后先落到 cacheDir 再发（与电脑端客户端对齐）
        btnUpload = root.findViewById(R.id.btn_upload);
        if (btnUpload != null) {
            filePicker = host.registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            upload(uri);
                        }
                    });
            btnUpload.setOnClickListener(v -> filePicker.launch(new String[]{"*/*"}));
        }

        // 左下公告 FAB：红点 = 有比上次看过更新的公告
        View fabNotice = root.findViewById(R.id.fab_notice);
        noticeDot = root.findViewById(R.id.dot_notice);
        if (fabNotice != null) {
            fabNotice.setOnClickListener(v -> host.startActivity(
                    new Intent(host, NoticeListActivity.class)));
        }

        fetchNew(true);
    }

    /** 回到会话：恢复轮询并补拉断线期间的消息 */
    public void onHostResume() {
        if (host == null) {
            return;
        }
        polling = true;
        handler.removeCallbacks(pollTask);
        handler.post(pollTask);
    }

    /** 离开会话：停轮询（页面常驻，消息列表不清空） */
    public void onHostPause() {
        polling = false;
        handler.removeCallbacks(pollTask);
    }

    // ================= 数据 =================

    private void fetchNew(boolean reset) {
        if (host == null) {
            return;
        }
        fetchMessages(reset);
        fetchFiles();
        refreshNoticeDot();
    }

    /** @param reset true=全量拉取（首次进入），false=按 after 游标增量 */
    private void fetchMessages(boolean reset) {
        long after = reset ? 0 : lastId;
        HttpUtil.get(host, "/api/messages?after=" + after + "&size=100", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                applyMessages(body, reset);
            }

            @Override
            public void onError(String message, Exception e) {
                // 断网时保持列表不动，下次轮询再试
                AppLog.w(TAG, "拉取群消息失败: " + message);
            }
        });
    }

    private void applyMessages(String body, boolean reset) {
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", -1) != 0) {
                AppLog.w(TAG, "服务端返回错误: " + root.optString("message"));
                return;
            }
            JSONArray array = root.optJSONArray("data");
            if (array == null) {
                return;
            }
            if (reset) {
                texts.clear();
                lastId = 0;
            }
            int added = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                long id = item.optLong("id", 0);
                if (id <= lastId) {
                    continue;
                }
                lastId = Math.max(lastId, id);
                texts.add(ChatMessage.text(id,
                        item.optString("sender", ""),
                        item.optString("content", ""),
                        sender.equals(item.optString("sender", "")),
                        item.optString("createdAt", "")));
                added++;
            }
            if (added > 0 || reset) {
                rebuildEntries(true);
                AppLog.d(TAG, "收到 " + added + " 条群消息，lastId=" + lastId);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "群消息解析失败", e);
        }
    }

    private void fetchFiles() {
        HttpUtil.get(host, "/api/files", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                applyFiles(body);
            }

            @Override
            public void onError(String message, Exception e) {
                AppLog.w(TAG, "拉取群文件失败: " + message);
            }
        });
    }

    private void applyFiles(String body) {
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", -1) != 0) {
                return;
            }
            JSONArray array = root.optJSONArray("data");
            if (array == null) {
                return;
            }
            files.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String name = item.optString("name", "");
                if (name.isEmpty()) {
                    continue;
                }
                files.add(new ChatFile(name,
                        item.optLong("size", 0),
                        item.optString("uploader", ""),
                        item.optString("uploadedAt", ""),
                        ChatFileStore.get(host, name)));
            }
            rebuildEntries(false);
        } catch (Exception e) {
            AppLog.e(TAG, "群文件解析失败", e);
        }
    }

    /** 文本 + 文件合成一条时间线；时间同分钟时保持文本在前（稳定排序） */
    private void rebuildEntries(boolean scrollIfAtBottom) {
        boolean atBottom = isAtBottom();
        entries.clear();
        entries.addAll(ChatTimeline.merge(texts, files, sender));
        adapter.notifyDataSetChanged();
        if (scrollIfAtBottom && atBottom && !entries.isEmpty()) {
            recyclerView.scrollToPosition(entries.size() - 1);
        }
    }

    private boolean isAtBottom() {
        if (layoutManager == null || adapter == null || adapter.getItemCount() == 0) {
            return true;
        }
        return layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 2;
    }

    // ================= 发消息 =================

    private void sendToServer(String text) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sender", sender);
            payload.put("content", text);
            HttpUtil.postJson(host, "/api/message", payload.toString(), new HttpUtil.Callback() {
                @Override
                public void onSuccess(String body) {
                    try {
                        JSONObject root = new JSONObject(body);
                        if (root.optInt("code", -1) != 0) {
                            Toast.makeText(host, root.optString("message"), Toast.LENGTH_LONG).show();
                            return;
                        }
                        // data 是数组：取刚插入的那条（id + createdAt）
                        JSONArray data = root.optJSONArray("data");
                        JSONObject item = data == null ? null : data.optJSONObject(0);
                        long id = item == null ? 0 : item.optLong("id", 0);
                        if (id <= 0) {
                            fetchMessages(false);   // 响应里没 id：靠轮询补，别本地插一条重复的
                            return;
                        }
                        lastId = Math.max(lastId, id);
                        texts.add(ChatMessage.text(id, sender, text, true,
                                item.optString("createdAt", "")));
                        etInput.setText("");
                        rebuildEntries(true);
                        AppLog.i(TAG, "已发送群消息 id=" + id);
                    } catch (Exception e) {
                        Toast.makeText(host, R.string.chat_send_failed,
                                Toast.LENGTH_SHORT).show();
                        AppLog.e(TAG, "发送结果解析失败", e);
                    }
                }

                @Override
                public void onError(String message, Exception e) {
                    Toast.makeText(host, host.getString(R.string.chat_send_failed, message),
                            Toast.LENGTH_LONG).show();
                    AppLog.w(TAG, "发送失败: " + message);
                }
            });
        } catch (Exception e) {
            AppLog.e(TAG, "构造消息失败", e);
        }
    }

    // ================= 上传 =================

    /** 上传：Uri → 临时文件 → POST /api/file；成功后把原件搬进 files/chat/uploads */
    private void upload(Uri uri) {
        if (host == null) {
            return;
        }
        String picked = queryDisplayName(uri);
        final String fileName = picked.isEmpty()
                ? "upload_" + System.currentTimeMillis() : picked;
        final File temp = new File(host.getCacheDir(), "upload_" + System.currentTimeMillis());
        try (InputStream in = host.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(temp)) {
            if (in == null) {
                throw new IllegalStateException("openInputStream 返回 null");
            }
            long bytes = Streams.copy(in, out);
            AppLog.i(TAG, "待上传 " + fileName + "（" + bytes + "B）");
        } catch (Exception e) {
            temp.delete();
            Toast.makeText(host, host.getString(R.string.chat_upload_failed,
                    String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
            AppLog.e(TAG, "读取待上传文件失败", e);
            return;
        }

        setUploading(true);
        HttpUtil.postMultipart(host, "/api/file", fileName, temp, sender, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                setUploading(false);
                try {
                    JSONObject root = new JSONObject(body);
                    if (root.optInt("code", -1) != 0) {
                        temp.delete();
                        Toast.makeText(host, root.optString("message"),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    JSONArray data = root.optJSONArray("data");
                    JSONObject item = data == null ? null : data.optJSONObject(0);
                    String saved = item == null ? fileName : item.optString("name", fileName);
                    keepUploadedCopy(temp, saved);
                    fetchFiles();   // 时间线里立刻出现这张卡片
                    Toast.makeText(host, host.getString(R.string.chat_upload_done, saved),
                            Toast.LENGTH_LONG).show();
                    AppLog.i(TAG, "群文件已上传: " + saved);
                } catch (Exception e) {
                    temp.delete();
                    Toast.makeText(host, R.string.chat_upload_parse_failed,
                            Toast.LENGTH_SHORT).show();
                    AppLog.e(TAG, "上传响应解析失败", e);
                }
            }

            @Override
            public void onError(String message, Exception e) {
                setUploading(false);
                temp.delete();
                Toast.makeText(host, host.getString(R.string.chat_upload_failed, message),
                        Toast.LENGTH_LONG).show();
                AppLog.w(TAG, "上传失败: " + message);
            }
        });
    }

    /** 上传成功的原件留在本机：本机再点这张卡片就是「直接打开」而不是重新下载 */
    private void keepUploadedCopy(File temp, String name) {
        String safe = new File(name).getName();
        File target = new File(ChatFileStore.uploadsDir(host), safe);
        boolean moved = temp.renameTo(target);
        if (!moved) {
            try (InputStream in = new java.io.FileInputStream(temp);
                 OutputStream out = new FileOutputStream(target)) {
                Streams.copy(in, out);
                moved = true;
            } catch (Exception e) {
                AppLog.e(TAG, "保存上传副本失败: " + safe, e);
            }
        }
        temp.delete();
        if (moved) {
            ChatFileStore.put(host, name, target);
        }
    }

    /** SAF Uri 的显示文件名（有些 Provider 不给，返回空串由调用方兜一个名字） */
    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor cursor = host.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                return name == null ? "" : name.trim();
            }
        } catch (Exception e) {
            AppLog.w(TAG, "读取文件名失败: " + e.getMessage());
        }
        return "";
    }

    private void setUploading(boolean uploading) {
        if (btnUpload == null) {
            return;
        }
        btnUpload.setEnabled(!uploading);
        btnUpload.setText(uploading ? R.string.chat_uploading : R.string.chat_upload);
    }

    // ================= 文件卡片 =================

    private void onFileClick(ChatFile file) {
        String local = ChatFileStore.get(host, file.getName());
        if (local != null) {
            openFile(new File(local));
            return;
        }
        if (downloading.contains(file.getName())) {
            Toast.makeText(host, R.string.chat_file_downloading, Toast.LENGTH_SHORT).show();
            return;
        }
        downloadFile(file);
    }

    private void downloadFile(ChatFile file) {
        final String name = file.getName();
        final File target = ChatFileStore.newDownload(host, name);
        downloading.add(name);
        Toast.makeText(host, host.getString(R.string.chat_file_download_start, name),
                Toast.LENGTH_SHORT).show();
        HttpUtil.download(host, "/api/file/" + Uri.encode(name), target, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String path) {
                downloading.remove(name);
                ChatFileStore.put(host, name, target);
                Toast.makeText(host, R.string.chat_file_downloaded, Toast.LENGTH_SHORT).show();
                fetchFiles();   // 卡片动作 → 点击打开
                AppLog.i(TAG, "群文件已下载: " + path);
            }

            @Override
            public void onError(String message, Exception e) {
                downloading.remove(name);
                target.delete();
                Toast.makeText(host, host.getString(R.string.chat_file_download_failed, message),
                        Toast.LENGTH_LONG).show();
                AppLog.w(TAG, "下载群文件失败: " + message);
            }
        });
    }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(host, FILE_PROVIDER_AUTHORITY, file);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, guessMime(file.getName()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            host.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(host, R.string.chat_file_no_app, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            AppLog.e(TAG, "打开文件失败: " + file, e);
            Toast.makeText(host, R.string.chat_file_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) {
                return mime;
            }
        }
        return "*/*";
    }

    // ================= 公告红点 =================

    /** 只看最新一条公告的 id，和服务端无关地记在本机 SP：新 id > 上次看过 → 亮红点 */
    private void refreshNoticeDot() {
        HttpUtil.get(host, "/api/notices?size=1", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                long latest = 0;
                try {
                    JSONObject root = new JSONObject(body);
                    JSONArray data = root.optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        latest = data.getJSONObject(0).optLong("id", 0);
                    }
                } catch (Exception e) {
                    AppLog.w(TAG, "公告红点解析失败", e);
                }
                setNoticeDot(latest > NoticeStore.lastSeenId(host));
            }

            @Override
            public void onError(String message, Exception e) {
                AppLog.w(TAG, "拉取公告失败: " + message);
            }
        });
    }

    private void setNoticeDot(boolean unread) {
        if (noticeDot != null) {
            noticeDot.setVisibility(unread ? View.VISIBLE : View.GONE);
        }
    }
}
