package com.offblink.keyou;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 同学录列表适配器：把联系人扁平成 [分组字母条, 联系人行, 联系人行, ...] 两种 viewType。
 *
 * 分组顺序 A→Z→#；组内按拼音序（Collator(CHINA)，SQLite 的二进制 id 序不参与展示）。
 * 同时维护「字母 → 该组首行 position」的映射，供右侧索引条跳转；
 * 以及「position → 所属字母」，供吸顶字母条与索引条联动。
 */
public class ContactGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onContactClick(Contact contact);

        void onContactLongClick(Contact contact);
    }

    static final int TYPE_HEADER = 0;
    static final int TYPE_CONTACT = 1;

    /** 一行：分组字母条 或 联系人卡片 */
    static class Row {
        final int type;
        final char letter;
        final Contact contact;
        final boolean firstInGroup;
        final boolean lastInGroup;

        Row(int type, char letter, Contact contact, boolean firstInGroup, boolean lastInGroup) {
            this.type = type;
            this.letter = letter;
            this.contact = contact;
            this.firstInGroup = firstInGroup;
            this.lastInGroup = lastInGroup;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<Character, Integer> letterPosition = new HashMap<>();
    private final Set<Character> letters = new LinkedHashSet<>();
    private final android.content.Context context;
    private final LayoutInflater inflater;
    private Listener listener;

    public ContactGroupAdapter(android.content.Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ================= 数据 =================

    public void setData(List<Contact> source) {
        rows.clear();
        letterPosition.clear();
        letters.clear();

        Map<Character, List<Contact>> byLetter = new HashMap<>();
        if (source != null) {
            for (Contact contact : source) {
                char letter = InitialUtil.of(contact.getName());
                byLetter.computeIfAbsent(letter, k -> new ArrayList<>()).add(contact);
            }
        }

        final Collator collator = Collator.getInstance(Locale.CHINA);
        List<Character> order = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            order.add(c);
        }
        order.add('#');

        for (char letter : order) {
            List<Contact> group = byLetter.get(letter);
            if (group == null || group.isEmpty()) {
                continue;
            }
            group.sort((a, b) -> collator.compare(
                    ContactStyle.emptyIfNull(a.getName()), ContactStyle.emptyIfNull(b.getName())));

            letters.add(letter);
            letterPosition.put(letter, rows.size());
            rows.add(new Row(TYPE_HEADER, letter, null, false, false));
            for (int i = 0; i < group.size(); i++) {
                rows.add(new Row(TYPE_CONTACT, letter, group.get(i), i == 0, i == group.size() - 1));
            }
        }
        notifyDataSetChanged();
    }

    /** 该字母分组的首行位置；没有该分组返回 null */
    public Integer positionOfLetter(char letter) {
        return letterPosition.get(letter);
    }

    public Set<Character> availableLetters() {
        return letters;
    }

    public int letterCount() {
        return letters.size();
    }

    Row rowAt(int position) {
        return rows.get(position);
    }

    /** 该位置所属分组的字母（索引条联动用），越界返回 0 */
    char letterAt(int position) {
        if (position < 0 || position >= rows.size()) {
            return 0;
        }
        return rows.get(position).letter;
    }

    /** 从 from 之后找下一个分组字母条的位置，没有返回 -1（吸顶头推挤用） */
    int nextHeaderPosition(int from) {
        for (int i = from + 1; i < rows.size(); i++) {
            if (rows.get(i).type == TYPE_HEADER) {
                return i;
            }
        }
        return -1;
    }

    // ================= RecyclerView =================

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_contact_letter, parent, false);
            return new HeaderHolder(view);
        }
        View view = inflater.inflate(R.layout.item_contact_grouped, parent, false);
        return new ContactHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.type == TYPE_HEADER && holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).section.setText(String.valueOf(row.letter));
            return;
        }
        if (!(holder instanceof ContactHolder)) {
            return;
        }

        ContactHolder h = (ContactHolder) holder;
        Contact contact = row.contact;
        String name = ContactStyle.emptyIfNull(contact.getName());
        h.name.setText(name);
        h.phone.setText(subtitle(contact));
        h.letter.setText(name.isEmpty() ? "?" : name.substring(0, 1));

        // 组与组之间留 10dp；同组行紧贴，圆角卡片由 ContactGroupDecoration 画
        RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) h.itemView.getLayoutParams();
        int topMargin = row.firstInGroup ? ContactStyle.dp(h.itemView.getContext(), 10) : 0;
        if (lp.topMargin != topMargin) {
            lp.topMargin = topMargin;
            h.itemView.setLayoutParams(lp);
        }
    }

    /** 副标题：优先电话，没电话退到邮箱，都没有显示占位符 */
    private String subtitle(Contact contact) {
        String phone = ContactStyle.emptyIfNull(contact.getPhone());
        if (!phone.isEmpty()) {
            return phone;
        }
        String email = ContactStyle.emptyIfNull(contact.getEmail());
        return email.isEmpty()
                ? context.getString(R.string.contacts_value_empty)
                : email;
    }

    // ================= ViewHolder =================

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView section;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            section = itemView.findViewById(R.id.tv_section);
        }
    }

    class ContactHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView phone;
        final TextView letter;

        ContactHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_name);
            phone = itemView.findViewById(R.id.tv_phone);
            letter = itemView.findViewById(R.id.tv_letter);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION || listener == null) {
                    return;
                }
                Row row = rows.get(position);
                if (row.type == TYPE_CONTACT) {
                    listener.onContactClick(row.contact);
                }
            });
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION || listener == null) {
                    return false;
                }
                Row row = rows.get(position);
                if (row.type != TYPE_CONTACT) {
                    return false;
                }
                listener.onContactLongClick(row.contact);
                return true;
            });
        }
    }
}
