package com.offblink.keyou;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志页：展示应用内日志缓冲（AppLog），新日志实时追加，可按级别过滤。
 *
 * 实时性来自 AppLog 的订阅回调（主线程推送到列表），不依赖系统 logcat 权限。
 */
public class LogActivity extends KeyouActivity implements AppLog.Listener {

    private static final String TAG = "Keyou/Logs";

    private final List<AppLog.Entry> shown = new ArrayList<>();
    private LogAdapter adapter;
    private RecyclerView rvLogs;
    private TextView tvCount;
    private TextView tvEmpty;
    private LinearLayoutManager layoutManager;
    private TextView[] chips;
    private AppLog.Level filter;   // null = 全部

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        rvLogs = findViewById(R.id.rv_logs);
        tvCount = findViewById(R.id.tv_log_count);
        tvEmpty = findViewById(R.id.tv_log_empty);
        layoutManager = new LinearLayoutManager(this);
        rvLogs.setLayoutManager(layoutManager);
        adapter = new LogAdapter();
        rvLogs.setAdapter(adapter);

        chips = new TextView[]{
                findViewById(R.id.chip_all), findViewById(R.id.chip_v), findViewById(R.id.chip_d),
                findViewById(R.id.chip_i), findViewById(R.id.chip_w), findViewById(R.id.chip_e)};
        for (int i = 0; i < chips.length; i++) {
            final int index = i;
            chips[i].setOnClickListener(v -> selectFilter(index));
        }
        selectFilter(0);

        findViewById(R.id.btn_log_clear).setOnClickListener(v -> {
            AppLog.clear();
            shown.clear();
            adapter.notifyDataSetChanged();
            refreshHeader();
            // 清空本身也留一条：顺便验证「新日志会实时出现在列表里」
            AppLog.i(TAG, "日志已清空");
        });

        shown.addAll(AppLog.snapshot());
        adapter.notifyDataSetChanged();
        refreshHeader();
        if (!shown.isEmpty()) {
            rvLogs.scrollToPosition(shown.size() - 1);
        }
        AppLog.addListener(this);
    }

    /** 0 = 全部，1..5 对应 V/D/I/W/E */
    private void selectFilter(int index) {
        filter = index == 0 ? null : AppLog.Level.values()[index - 1];
        for (int i = 0; i < chips.length; i++) {
            chips[i].setBackgroundResource(i == index
                    ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chips[i].setTextColor(ContextCompat.getColor(this,
                    i == index ? R.color.nav_label_on : R.color.nav_label_off));
        }
        shown.clear();
        for (AppLog.Entry e : AppLog.snapshot()) {
            if (accepts(e)) {
                shown.add(e);
            }
        }
        adapter.notifyDataSetChanged();
        refreshHeader();
        if (!shown.isEmpty()) {
            rvLogs.scrollToPosition(shown.size() - 1);
        }
    }

    private boolean accepts(AppLog.Entry entry) {
        return filter == null || entry.level == filter;
    }

    private void refreshHeader() {
        tvCount.setText(getString(R.string.log_count, shown.size()));
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onEntry(AppLog.Entry entry) {
        if (!accepts(entry)) {
            refreshHeader();
            return;
        }
        shown.add(entry);
        adapter.notifyItemInserted(shown.size() - 1);
        rvLogs.scrollToPosition(shown.size() - 1);
        refreshHeader();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLog.removeListener(this);
    }

    /** 每条日志一行：时间 / 级别 / TAG / 正文 */
    private final class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {

        final class VH extends RecyclerView.ViewHolder {
            final TextView time;
            final TextView level;
            final TextView tag;
            final TextView msg;

            VH(@NonNull View itemView) {
                super(itemView);
                time = itemView.findViewById(R.id.tv_log_time);
                level = itemView.findViewById(R.id.tv_log_level);
                tag = itemView.findViewById(R.id.tv_log_tag);
                msg = itemView.findViewById(R.id.tv_log_msg);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_log, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AppLog.Entry e = shown.get(position);
            h.time.setText(e.timeText());
            h.level.setText(e.level.name());
            h.level.setTextColor(ContextCompat.getColor(LogActivity.this, levelColor(e.level)));
            h.tag.setText(e.tag);
            h.msg.setText(e.message);
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        private int levelColor(AppLog.Level level) {
            switch (level) {
                case V:
                    return R.color.log_v;
                case D:
                    return R.color.log_d;
                case I:
                    return R.color.log_i;
                case W:
                    return R.color.log_w;
                default:
                    return R.color.log_e;
            }
        }
    }
}
