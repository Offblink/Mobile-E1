package com.offblink.keyou;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 任务 9：班级成员列表适配器（服务端数据，分页追加）。
 */
public class MateAdapter extends RecyclerView.Adapter<MateAdapter.VH> {

    public interface OnItemClickListener {
        void onItemClick(Mate mate);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }

    private final List<Mate> data;
    private final OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;

    public MateAdapter(List<Mate> data, OnItemClickListener listener) {
        this.data = data;
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        this.longClickListener = l;
    }

    public static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView detail;

        public VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_mate_name);
            detail = itemView.findViewById(R.id.tv_mate_detail);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mate, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Mate m = data.get(pos);
        h.name.setText(m.getName());
        h.detail.setText(m.brief());
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(m);
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
