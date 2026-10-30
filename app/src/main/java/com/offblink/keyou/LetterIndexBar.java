package com.offblink.keyou;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.HashSet;
import java.util.Set;

/**
 * 右侧字母索引条：A-Z 与 # 共 27 个槽，槽高 = 可用高度 / 27。
 *
 * - 按下/拖动：命中槽 → 高亮，并回调 onLetterSelected（没有同学的字母变灰、不回调）；
 * - 按住时在左侧浮出圆形气泡显示当前字母，松手 300ms 淡出；
 * - 滚动联动由外部调 setActiveLetter()，颜色直切不做动画（只有颜色变化，走临界阻尼）。
 */
public class LetterIndexBar extends View {

    public interface OnLetterSelectedListener {
        void onLetterSelected(char letter);
    }

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    private static final int COUNT = LETTERS.length();

    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bubbleRect = new RectF();
    private final Set<Character> available = new HashSet<>();

    private final float bubbleRadius;
    private final float bubbleGap;

    private OnLetterSelectedListener listener;
    private char activeLetter = 0;
    private int touchIndex = -1;
    private float bubbleAlpha = 0f;
    private float slotH = 1f;
    private ValueAnimator fadeAnimator;

    public LetterIndexBar(Context context) {
        this(context, null);
    }

    public LetterIndexBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LetterIndexBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        letterPaint.setTextAlign(Paint.Align.CENTER);
        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);
        bubbleTextPaint.setFakeBoldText(true);
        bubbleTextPaint.setColor(ContactStyle.color(context, ContactStyle.ATTR_ON_PRIMARY_CONTAINER));
        bubblePaint.setColor(ContactStyle.color(context, ContactStyle.ATTR_PRIMARY_CONTAINER));
        bubbleRadius = ContactStyle.dp(context, 22);
        bubbleGap = ContactStyle.dp(context, 12);
    }

    public void setOnLetterSelectedListener(OnLetterSelectedListener listener) {
        this.listener = listener;
    }

    /** 有同学的字母（其余槽画灰、点了不跳） */
    public void setAvailableLetters(Set<Character> letters) {
        available.clear();
        if (letters != null) {
            available.addAll(letters);
        }
        invalidate();
    }

    public void setActiveLetter(char letter) {
        if (activeLetter == letter) {
            return;
        }
        activeLetter = letter;
        invalidate();
    }

    public static char letterAt(int index) {
        return LETTERS.charAt(Math.max(0, Math.min(COUNT - 1, index)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        slotH = h / (float) COUNT;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float textSize = Math.min(ContactStyle.sp(getContext(), 11f), slotH * 0.82f);
        letterPaint.setTextSize(textSize);

        for (int i = 0; i < COUNT; i++) {
            char c = LETTERS.charAt(i);
            boolean has = available.contains(c);
            boolean on = c == activeLetter;
            letterPaint.setColor(ContactStyle.color(getContext(),
                    on ? ContactStyle.ATTR_PRIMARY : ContactStyle.ATTR_ON_SURFACE_VARIANT));
            letterPaint.setAlpha(has ? 255 : 64);
            letterPaint.setFakeBoldText(on);
            float y = slotH * i + slotH / 2f + textSize * 0.36f;
            canvas.drawText(String.valueOf(c), getWidth() / 2f, y, letterPaint);
        }

        if (bubbleAlpha > 0.01f && touchIndex >= 0) {
            float cy = slotH * touchIndex + slotH / 2f;
            float cx = -bubbleGap - bubbleRadius;
            bubbleRect.set(cx - bubbleRadius, cy - bubbleRadius, cx + bubbleRadius, cy + bubbleRadius);
            int alpha = (int) (255 * bubbleAlpha);
            bubblePaint.setAlpha(alpha);
            canvas.drawCircle(cx, cy, bubbleRadius, bubblePaint);
            bubbleTextPaint.setAlpha(alpha);
            bubbleTextPaint.setTextSize(ContactStyle.sp(getContext(), 20f));
            canvas.drawText(String.valueOf(LETTERS.charAt(touchIndex)),
                    cx, cy + ContactStyle.sp(getContext(), 7f), bubbleTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                int index = hit(event.getY());
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                boolean changed = index != touchIndex;
                touchIndex = index;
                showBubble();
                char letter = LETTERS.charAt(index);
                if (changed && available.contains(letter) && listener != null) {
                    listener.onLetterSelected(letter);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                hideBubble();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private int hit(float y) {
        int index = (int) (y / slotH);
        return Math.max(0, Math.min(COUNT - 1, index));
    }

    private void showBubble() {
        bubbleAlpha = 1f;
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
    }

    private void hideBubble() {
        if (touchIndex < 0) {
            return;
        }
        fadeAnimator = ValueAnimator.ofFloat(bubbleAlpha, 0f);
        fadeAnimator.setDuration(300);
        fadeAnimator.setInterpolator(new DecelerateInterpolator());
        fadeAnimator.addUpdateListener(animation -> {
            bubbleAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        fadeAnimator.start();
    }
}
