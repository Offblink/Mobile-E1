package com.offblink.keyou;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页内嵌的「班级成员」列表：分页加载、点击编辑、长按删除、右上角添加。
 * 数据来自服务端 /api/mates（与班级成员统计同源），不单独开页面。
 */
public final class HomeMembers {

    private static final String TAG = "Keyou/HomeMembers";
    private static final int PAGE_SIZE = 10;

    /** 列表状态回调：总数给统计卡用，错误给空态用 */
    public interface Listener {
        void onTotal(long total);

        void onError(String message);
    }

    private final Activity host;
    private final View root;
    private final Listener listener;
    private final List<Mate> data = new ArrayList<>();
    private final Gson gson = new Gson();

    private MateAdapter adapter;
    private TextView tvEmpty;
    private int currentPage = 0;
    private boolean hasMore = true;
    private boolean loading = false;
    private boolean loadedOnce = false;

    public HomeMembers(Activity host, View root, Listener listener) {
        this.host = host;
        this.root = root;
        this.listener = listener;
    }

    public void attach() {
        tvEmpty = root.findViewById(R.id.tv_home_mates_empty);
        RecyclerView rv = root.findViewById(R.id.rv_home_mates);
        LinearLayoutManager lm = new LinearLayoutManager(host);
        rv.setLayoutManager(lm);
        adapter = new MateAdapter(data, this::edit);
        adapter.setOnItemLongClickListener(pos -> {
            if (pos >= 0 && pos < data.size()) {
                confirmDelete(data.get(pos));
            }
        });
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) {
                    return;
                }
                if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 1) {
                    loadPage(currentPage + 1);
                }
            }
        });
        rv.setAdapter(adapter);
        root.findViewById(R.id.btn_mate_add).setOnClickListener(v -> edit(null));
        reload();
    }

    /** 重新加载第一页（新增/编辑/删除之后调用） */
    public void reload() {
        currentPage = 0;
        hasMore = true;
        data.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        loadPage(1);
    }

    private void loadPage(int page) {
        if (loading) {
            return;
        }
        loading = true;
        HttpUtil.get(host, "/api/mates?page=" + page + "&size=" + PAGE_SIZE,
                new HttpUtil.Callback() {
                    @Override
                    public void onSuccess(String body) {
                        loading = false;
                        loadedOnce = true;
                        try {
                            JSONObject root2 = new JSONObject(body);
                            if (root2.getInt("code") != 0) {
                                fail(root2.getString("message"));
                                return;
                            }
                            JSONObject payload = root2.getJSONObject("data");
                            long total = payload.getLong("total");
                            Type type = new TypeToken<List<Mate>>() {
                            }.getType();
                            List<Mate> list = gson.fromJson(
                                    payload.getJSONArray("list").toString(), type);
                            data.addAll(list);
                            adapter.notifyDataSetChanged();
                            currentPage = page;
                            hasMore = data.size() < total;
                            tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                            listener.onTotal(total);
                            Log.i(TAG, "第 " + page + " 页 " + list.size() + " 条, total=" + total);
                        } catch (Exception e) {
                            Log.e(TAG, "响应解析失败", e);
                            fail(host.getString(R.string.mate_parse_error));
                        }
                    }

                    @Override
                    public void onError(String message, Exception e) {
                        loading = false;
                        loadedOnce = true;
                        fail(host.getString(R.string.mate_network_error, message));
                    }
                });
    }

    private void fail(String message) {
        listener.onError(message);
        if (data.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(host.getString(R.string.mate_error_title));
        }
    }

    /** 新增（mate==null）或编辑：表单对话框 → POST / PUT */
    private void edit(Mate mate) {
        MateFormDialog.show(host, mate, saved -> {
            String json = gson.toJson(saved);
            HttpUtil.Callback callback = new HttpUtil.Callback() {
                @Override
                public void onSuccess(String body) {
                    try {
                        if (new JSONObject(body).getInt("code") == 0) {
                            Toast.makeText(host, R.string.mate_saved, Toast.LENGTH_SHORT).show();
                            reload();
                        } else {
                            Toast.makeText(host, new JSONObject(body).getString("message"),
                                    Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(host, R.string.mate_parse_error, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String message, Exception e) {
                    Toast.makeText(host, host.getString(R.string.mate_network_error, message),
                            Toast.LENGTH_LONG).show();
                }
            };
            if (saved.getId() != null) {
                HttpUtil.putJson(host, "/api/mate", json, callback);
            } else {
                HttpUtil.postJson(host, "/api/mate", json, callback);
            }
        });
    }

    private void confirmDelete(Mate mate) {
        new AlertDialog.Builder(host)
                .setTitle(R.string.mate_delete_title)
                .setMessage(host.getString(R.string.mate_delete_message, mate.getName()))
                .setPositiveButton(R.string.contact_delete_ok, (d, w) ->
                        HttpUtil.delete(host, "/api/mate/" + mate.getId(), new HttpUtil.Callback() {
                            @Override
                            public void onSuccess(String body) {
                                Toast.makeText(host, R.string.mate_deleted, Toast.LENGTH_SHORT).show();
                                reload();
                            }

                            @Override
                            public void onError(String message, Exception e) {
                                Toast.makeText(host,
                                        host.getString(R.string.mate_network_error, message),
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    public boolean isLoadedOnce() {
        return loadedOnce;
    }
}
