package com.offblink.keyou;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 任务 3：聊天室适配器 —— 区分左右气泡。
 * 同一 item 布局，按 isSelf 切换外层 gravity（左=对方 / 右=自己）与气泡背景。
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

    private final List<ChatMessage> data;

    public ChatAdapter(List<ChatMessage> data) {
        this.data = data;
    }

    public static class VH extends RecyclerView.ViewHolder {
        final LinearLayout bubbleRow;
        final TextView tvContent;

        public VH(@NonNull View itemView) {
            super(itemView);
            bubbleRow = itemView.findViewById(R.id.bubble_row);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ChatMessage msg = data.get(pos);
        h.tvContent.setText(msg.getContent());

        // 自己：气泡靠右、主题色底；对方：靠左、灰色底
        // item 根是 FrameLayout，子项 gravity 用 FrameLayout.LayoutParams.gravity
        ViewGroup.LayoutParams lp = h.bubbleRow.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).gravity =
                    msg.isSelf() ? Gravity.END : Gravity.START;
            h.bubbleRow.setLayoutParams(lp);
        }
        h.tvContent.setBackgroundResource(msg.isSelf()
                ? R.drawable.bubble_right : R.drawable.bubble_left);
        h.tvContent.setTextColor(getContextColor(h, msg.isSelf()));
    }

    private int getContextColor(VH h, boolean self) {
        return h.itemView.getContext().getColor(
                self ? R.color.md_on_primary_container : R.color.md_on_surface);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
