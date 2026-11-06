package com.offblink.keyou.chat;

import com.offblink.keyou.R;

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
 * 聊天时间线适配器：文本气泡 + 文件卡片两种行。
 *
 * - 文本：同一 item_chat 布局，按 isSelf 切左右与气泡底色；
 * - 文件：item_chat_file 卡片，第二行是「大小 · 上传人 · 时间」，第三行是动作提示
 *   （本机有副本 → 点击打开；没有 → 点击下载）。点击整卡回调 ChatPanel。
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TEXT = 0;
    private static final int TYPE_FILE = 1;

    public interface OnFileClick {
        void onFile(ChatFile file);
    }

    private final List<ChatMessage> data;
    private final OnFileClick onFileClick;

    public ChatAdapter(List<ChatMessage> data, OnFileClick onFileClick) {
        this.data = data;
        this.onFileClick = onFileClick;
    }

    static class TextVH extends RecyclerView.ViewHolder {
        final LinearLayout bubbleRow;
        final TextView tvContent;

        TextVH(@NonNull View itemView) {
            super(itemView);
            bubbleRow = itemView.findViewById(R.id.bubble_row);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }

    static class FileVH extends RecyclerView.ViewHolder {
        final LinearLayout fileRow;
        final LinearLayout card;
        final TextView tvName;
        final TextView tvMeta;
        final TextView tvAction;

        FileVH(@NonNull View itemView) {
            super(itemView);
            fileRow = itemView.findViewById(R.id.file_row);
            card = itemView.findViewById(R.id.file_card);
            tvName = itemView.findViewById(R.id.tv_file_name);
            tvMeta = itemView.findViewById(R.id.tv_file_meta);
            tvAction = itemView.findViewById(R.id.tv_file_action);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return data.get(position).isFile() ? TYPE_FILE : TYPE_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FILE) {
            return new FileVH(inflater.inflate(R.layout.item_chat_file, parent, false));
        }
        return new TextVH(inflater.inflate(R.layout.item_chat, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        ChatMessage msg = data.get(pos);
        if (holder instanceof TextVH) {
            bindText((TextVH) holder, msg);
        } else {
            bindFile((FileVH) holder, msg);
        }
    }

    private void bindText(TextVH h, ChatMessage msg) {
        h.tvContent.setText(msg.getContent());
        align(h.bubbleRow, msg.isSelf());
        h.tvContent.setBackgroundResource(msg.isSelf()
                ? R.drawable.bubble_right : R.drawable.bubble_left);
        h.tvContent.setTextColor(h.itemView.getContext().getColor(msg.isSelf()
                ? R.color.md_on_primary_container : R.color.md_on_surface));
    }

    private void bindFile(FileVH h, ChatMessage msg) {
        ChatFile file = msg.getFile();
        h.tvName.setText(file.getName());
        h.tvMeta.setText(file.meta());
        h.tvAction.setText(file.hasLocalCopy()
                ? R.string.chat_file_open : R.string.chat_file_download);

        boolean self = msg.isSelf();
        align(h.fileRow, self);
        h.card.setBackgroundResource(self ? R.drawable.bubble_right : R.drawable.bubble_left);
        h.tvName.setTextColor(h.itemView.getContext().getColor(
                self ? R.color.md_on_primary_container : R.color.md_on_surface));
        h.tvMeta.setTextColor(h.itemView.getContext().getColor(R.color.md_on_surface_variant));
        h.tvAction.setTextColor(h.itemView.getContext().getColor(R.color.md_primary));
        h.card.setOnClickListener(v -> onFileClick.onFile(file));
    }

    /** item 根是 FrameLayout，子项 gravity 用 FrameLayout.LayoutParams.gravity */
    private static void align(View row, boolean self) {
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).gravity = self ? Gravity.END : Gravity.START;
            row.setLayoutParams(lp);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
