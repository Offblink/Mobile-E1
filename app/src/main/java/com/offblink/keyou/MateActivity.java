package com.offblink.keyou;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务 9（综合案例）：班级成员管理 —— 与 SSM 后端整合的完整闭环。
 *
 * 验收点（指导书任务 9 前端清单）：
 * - 启动加载分页列表（RecyclerView + 滚动到底加载下一页）；
 * - 表单新增（POST JSON，Gson 序列化，Content-Type: application/json）；
 * - 长按删除、点击编辑（PUT 请求）；
 * - 加载中对话框、空数据提示、网络异常统一提示。
 *
 * 联调排错记录（写报告"错误与改正"素材）：
 * 1. 415 Unsupported Media Type：POST/PUT 忘设 Content-Type: application/json；
 * 2. 中文乱码：请求流必须 UTF-8（HttpUtil 已统一封装）；
 * 3. 列表不刷新：请求回调忘了切主线程 notifyDataSetChanged（HttpUtil 统一 post 回主线程）。
 */
public class MateActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Mate";

    private static final int PAGE_SIZE = 10;

    private MateAdapter adapter;
    private final List<Mate> data = new ArrayList<>();
    private EditText etKeyword;
    private TextView tvEmpty;

    private int currentPage = 1;
    private boolean hasMore = true;
    private boolean loading = false;
    private ProgressDialog progressDialog;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mate);
        Log.d(TAG, "onCreate");

        etKeyword = findViewById(R.id.et_mate_keyword);
        tvEmpty = findViewById(R.id.tv_mate_empty);

        RecyclerView rv = findViewById(R.id.rv_mates);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        adapter = new MateAdapter(data, this::openEdit);
        // 滚动到底加载下一页（分页加载）
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) {
                    return;
                }
                int lastVisible = lm.findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - 1) {
                    loadPage(currentPage + 1);
                }
            }
        });
        adapter.setOnItemLongClickListener(position -> confirmDelete(data.get(position)));
        rv.setAdapter(adapter);

        findViewById(R.id.btn_mate_search).setOnClickListener(v -> reload());

        // 新增：空表单编辑页（Gson 序列化后 POST）
        findViewById(R.id.btn_mate_add).setOnClickListener(v -> openEdit(null));

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.mate_loading));
        progressDialog.setCancelable(false);

        reload();
    }

    /** 重新加载第一页 */
    private void reload() {
        currentPage = 1;
        hasMore = true;
        data.clear();
        adapter.notifyDataSetChanged();
        loadPage(1);
    }

    /**
     * 加载指定页：GET /api/mates?page=&size=&kw=
     * 成功后 Gson 解析 data.list 追加；total 判断是否还有下一页。
     */
    private void loadPage(int page) {
        if (loading) {
            return;
        }
        loading = true;
        if (page == 1) {
            progressDialog.show();
        }
        String kw = etKeyword.getText().toString().trim();
        String path = "/api/mates?page=" + page + "&size=" + PAGE_SIZE + "&kw=" + kw;
        HttpUtil.get(this, path, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                dismissLoading();
                try {
                    JSONObject root = new JSONObject(body);
                    int code = root.getInt("code");
                    if (code != 0) {
                        showError(root.getString("message"));
                        return;
                    }
                    JSONObject payload = root.getJSONObject("data");
                    long total = payload.getLong("total");
                    Type type = new TypeToken<List<Mate>>() {
                    }.getType();
                    List<Mate> pageList = new Gson()
                            .fromJson(payload.getJSONArray("list").toString(), type);
                    data.addAll(pageList);
                    adapter.notifyDataSetChanged();
                    currentPage = page;
                    hasMore = data.size() < total;
                    // 空态提示
                    boolean empty = data.isEmpty();
                    tvEmpty.setVisibility(empty ? TextView.VISIBLE : TextView.GONE);
                    Log.i(TAG, "第 " + page + " 页: " + pageList.size()
                            + " 条, total=" + total);
                } catch (Exception e) {
                    Log.e(TAG, "响应解析失败", e);
                    showError(getString(R.string.mate_parse_error));
                } finally {
                    loading = false;
                }
            }

            @Override
            public void onError(String message, Exception e) {
                dismissLoading();
                loading = false;
                // 网络异常统一提示（连不上 Tomcat / 超时 / 500）
                showRetryableError(getString(R.string.mate_network_error, message));
            }
        });
    }

    /** 新增（mate==null）或编辑（带 id 的 PUT） */
    private void openEdit(Mate mate) {
        MateFormDialog.show(this, mate, saved -> {
            String json = gson.toJson(saved);
            HttpUtil.Callback callback = new HttpUtil.Callback() {
                @Override
                public void onSuccess(String body) {
                    try {
                        JSONObject root = new JSONObject(body);
                        if (root.getInt("code") == 0) {
                            Toast.makeText(MateActivity.this, R.string.mate_saved,
                                    Toast.LENGTH_SHORT).show();
                            reload();
                        } else {
                            showError(root.getString("message"));
                        }
                    } catch (Exception e) {
                        showError(getString(R.string.mate_parse_error));
                    }
                }

                @Override
                public void onError(String message, Exception e) {
                    showRetryableError(getString(R.string.mate_network_error, message));
                }
            };
            if (saved.getId() != null) {
                HttpUtil.putJson(this, "/api/mate", json, callback);
            } else {
                HttpUtil.postJson(this, "/api/mate", json, callback);
            }
        });
    }

    /** 长按删除确认（指导书：长按删除） */
    private void confirmDelete(Mate mate) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.mate_delete_title)
                .setMessage(getString(R.string.mate_delete_message, mate.getName()))
                .setPositiveButton(R.string.contact_delete_ok, (d, w) ->
                        HttpUtil.delete(this, "/api/mate/" + mate.getId(), new HttpUtil.Callback() {
                            @Override
                            public void onSuccess(String body) {
                                Toast.makeText(MateActivity.this, R.string.mate_deleted,
                                        Toast.LENGTH_SHORT).show();
                                reload();
                            }

                            @Override
                            public void onError(String message, Exception e) {
                                showRetryableError(getString(R.string.mate_network_error, message));
                            }
                        }))
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** 网络类错误：弹可重试对话框（任务 9 验收点：异常统一提示） */
    private void showRetryableError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.mate_error_title)
                .setMessage(message)
                .setPositiveButton(R.string.mate_retry, (d, w) -> reload())
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void dismissLoading() {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissLoading();
        Log.d(TAG, "onDestroy");
    }
}
