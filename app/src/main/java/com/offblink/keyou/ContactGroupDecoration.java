package com.offblink.keyou;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 列表装饰器，干两件事：
 *
 * 1) onDraw —— 给每个联系人行画「所属分组卡片」的背景：组首行圆上两角、组尾行圆下两角，
 *    组内行之间画 1px 内嵌分隔线（左起 73dp = 16 内边距 + 44 头像 + 13 间距，让开用户名）。
 *    这样看起来是「按字母分组的圆角卡片」，又不用把整组塞进一个 View。
 *
 * 2) onDrawOver —— 吸顶分组字母条：滚动时当前字母固定在顶部，下一个字母顶上来时把它推走。
 */
public class ContactGroupDecoration extends RecyclerView.ItemDecoration {

    /** 吸顶字母条变了就回调，索引条据此联动（保证「顶部字母」与「右侧高亮」永远一致） */
    public interface OnSectionChangedListener {
        void onSectionChanged(char letter);
    }

    private final ContactGroupAdapter adapter;
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint();
    private final Paint bandPaint = new Paint();
    private final Paint bandTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final float radius;
    private final float bandHeight;
    private final float dividerInset;
    private final float dividerHeight;
    private final float bandTextX;
    private final float bandTextOffset;

    private OnSectionChangedListener sectionListener;
    private char lastSection = 0;

    public ContactGroupDecoration(Context context, ContactGroupAdapter adapter) {
        this.adapter = adapter;
        radius = ContactStyle.dp(context, 16);
        bandHeight = ContactStyle.dp(context, 30);
        dividerInset = ContactStyle.dp(context, 73);
        dividerHeight = Math.max(1f, context.getResources().getDisplayMetrics().density * 0.5f);
        bandTextX = ContactStyle.dp(context, 20);

        int surface = ContactStyle.color(context, ContactStyle.ATTR_SURFACE);
        int onSurfaceVariant = ContactStyle.color(context, ContactStyle.ATTR_ON_SURFACE_VARIANT);

        cardPaint.setStyle(Paint.Style.FILL);
        // 卡片底色 = 底色上掺一档 onSurfaceVariant（≈ M3 的 surface container 层级，深浅色都对）
        cardPaint.setColor(ContactStyle.blend(surface, onSurfaceVariant, 0.08f));
        dividerPaint.setColor(ContactStyle.blend(surface, onSurfaceVariant, 0.22f));
        bandPaint.setColor(surface);
        bandTextPaint.setColor(onSurfaceVariant);
        bandTextPaint.setTextSize(ContactStyle.sp(context, 12f));
        bandTextOffset = bandHeight / 2f + bandTextPaint.getTextSize() * 0.36f;
    }

    public void setOnSectionChangedListener(OnSectionChangedListener listener) {
        this.sectionListener = listener;
    }

    /** 数据整体换了以后调用：清掉缓存，让下次绘制重新回调当前分组字母 */
    public void resetSection() {
        lastSection = 0;
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) {
                continue;
            }
            ContactGroupAdapter.Row row = adapter.rowAt(position);
            if (row.type != ContactGroupAdapter.TYPE_CONTACT) {
                continue;   // 分组字母条不画卡片
            }

            float left = child.getLeft();
            float top = child.getTop();
            float right = child.getRight();
            float bottom = child.getBottom();

            float tl = row.firstInGroup ? radius : 0f;
            float bl = row.lastInGroup ? radius : 0f;
            path.reset();
            path.addRoundRect(left, top, right, bottom,
                    new float[]{tl, tl, tl, tl, bl, bl, bl, bl}, Path.Direction.CW);
            canvas.drawPath(path, cardPaint);

            if (!row.lastInGroup) {
                canvas.drawRect(left + dividerInset, bottom - dividerHeight, right, bottom, dividerPaint);
            }
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (adapter.getItemCount() == 0) {
            return;
        }
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) {
            return;
        }

        int first = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return;
        }

        char letter = adapter.letterAt(first);
        if (letter == 0) {
            return;
        }

        float top = 0f;
        int nextHeader = adapter.nextHeaderPosition(first);
        if (nextHeader != -1) {
            View next = lm.findViewByPosition(nextHeader);
            if (next != null && next.getTop() >= 0 && next.getTop() < bandHeight) {
                top = next.getTop() - bandHeight;   // 下一个字母条把当前字母条顶上去
            }
        }

        // 索引条高亮跟「视觉顶部的分组」走：滑到列表末尾时首个可见行可能只露出一条缝
        // （上一组的最后一行），字母条已被下一个字母顶出屏幕 —— 这时高亮该跟着顶进来的字母走。
        char railLetter = (top < 0f && nextHeader != -1) ? adapter.letterAt(nextHeader) : letter;
        if (railLetter != lastSection) {
            lastSection = railLetter;
            if (sectionListener != null) {
                sectionListener.onSectionChanged(railLetter);
            }
        }

        canvas.save();
        canvas.translate(0f, top);
        canvas.drawRect(0f, 0f, parent.getWidth(), bandHeight, bandPaint);
        canvas.drawText(String.valueOf(letter), bandTextX, bandTextOffset, bandTextPaint);
        canvas.restore();
    }
}
