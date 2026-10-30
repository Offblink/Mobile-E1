package com.offblink.keyou;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 任务 3：RecyclerView 适配器（指导书 ContactAdapter 样例口径）。
 *
 * ViewHolder 机制：onCreateViewHolder 只在 onCreateViewHolder 时 inflate 一次，
 * onBindViewHolder 复用已创建的.itemView 只做绑定 —— 这是相对 ListView
 * 「每行都 findViewById」性能问题的核心改进；ListView 时代需要手写 ViewHolder
 * 缓存，RecyclerView 把这套机制做进了框架。
 */
public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.VH> {

    /** 行点击回调（点击事件绑定在 itemView 上） */
    public interface OnItemClickListener {
        void onItemClick(Contact contact);
    }

    /** 行长按回调（删除确认入口） */
    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }

    private final List<Contact> data;
    private final OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;

    public ContactAdapter(List<Contact> data, OnItemClickListener listener) {
        this.data = data;
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        this.longClickListener = l;
    }

    public static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView phone;

        public VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_name);
            phone = itemView.findViewById(R.id.tv_phone);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Contact c = data.get(pos);
        h.name.setText(c.getName());
        h.phone.setText(c.getPhone());
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(c);
            }
        });
        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(h.getBindingAdapterPosition());
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
