package com.offblink.keyou;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具箱：把内容、设备、网络、调试四类功能各收成一个独立页面，这里只做入口列表。
 */
public class ToolsActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Tools";

    private static final class Item {
        final String section;   // 非空 = 分组标题
        final int title;
        final int subtitle;
        final Class<?> target;

        Item(String section, int title, int subtitle, Class<?> target) {
            this.section = section;
            this.title = title;
            this.subtitle = subtitle;
            this.target = target;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);
        AppLog.d(TAG, "onCreate");

        List<Item> items = new ArrayList<>();
        items.add(new Item(getString(R.string.tools_section_content), 0, 0, null));
        items.add(new Item(null, R.string.tools_entry_draft,
                R.string.tools_entry_draft_sub, DraftActivity.class));
        items.add(new Item(null, R.string.tools_entry_download,
                R.string.tools_entry_download_sub, DownloadActivity.class));
        items.add(new Item(getString(R.string.tools_section_device), 0, 0, null));
        items.add(new Item(null, R.string.tools_entry_device,
                R.string.tools_entry_device_sub, DeviceActivity.class));
        items.add(new Item(null, R.string.tools_entry_service,
                R.string.tools_entry_service_sub, ServiceActivity.class));
        items.add(new Item(getString(R.string.tools_section_network), 0, 0, null));
        items.add(new Item(null, R.string.json_compare_title,
                R.string.tools_entry_json_sub, JsonCompareActivity.class));
        items.add(new Item(null, R.string.rss_title,
                R.string.tools_entry_rss_sub, RssParseActivity.class));
        items.add(new Item(getString(R.string.tools_section_debug), 0, 0, null));
        items.add(new Item(null, R.string.tools_entry_diag,
                R.string.tools_entry_diag_sub, DiagnosticsActivity.class));

        RecyclerView rv = findViewById(R.id.rv_tools);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new ToolsAdapter(items));
    }

    private final class ToolsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ENTRY = 1;

        private final List<Item> items;

        ToolsAdapter(List<Item> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).section != null ? TYPE_HEADER : TYPE_ENTRY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderHolder(inflater.inflate(R.layout.item_tool_header, parent, false));
            }
            return new EntryHolder(inflater.inflate(R.layout.item_tool_entry, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).section.setText(item.section);
                return;
            }
            EntryHolder h = (EntryHolder) holder;
            h.title.setText(item.title);
            h.subtitle.setText(item.subtitle);
            h.itemView.setOnClickListener(v -> startActivity(new Intent(ToolsActivity.this, item.target)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class HeaderHolder extends RecyclerView.ViewHolder {
            final TextView section;

            HeaderHolder(@NonNull View itemView) {
                super(itemView);
                section = itemView.findViewById(R.id.tv_section);
            }
        }

        final class EntryHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;

            EntryHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_entry_title);
                subtitle = itemView.findViewById(R.id.tv_entry_subtitle);
            }
        }
    }
}
