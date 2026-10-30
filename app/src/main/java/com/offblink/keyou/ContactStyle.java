package com.offblink.keyou;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.TypedValue;

/**
 * 同学录这一套界面共用的小工具：dp/sp 换算、主题角色色解析、混色。
 *
 * 颜色一律从 ?attr 读（Material3 DayNight 主题下深色模式自动跟随）；
 * 需要「比底色深一档」的层级（分组卡片、表格网格线）时用 blend() 在代码里混，
 * 不往 colors.xml 里塞硬编码色值 —— 否则深色模式会花。
 */
final class ContactStyle {

    static final int ATTR_SURFACE =
            com.google.android.material.R.attr.colorSurface;
    static final int ATTR_SURFACE_VARIANT =
            com.google.android.material.R.attr.colorSurfaceVariant;
    static final int ATTR_ON_SURFACE =
            com.google.android.material.R.attr.colorOnSurface;
    static final int ATTR_ON_SURFACE_VARIANT =
            com.google.android.material.R.attr.colorOnSurfaceVariant;
    static final int ATTR_PRIMARY = androidx.appcompat.R.attr.colorPrimary;
    static final int ATTR_PRIMARY_CONTAINER =
            com.google.android.material.R.attr.colorPrimaryContainer;
    static final int ATTR_ON_PRIMARY_CONTAINER =
            com.google.android.material.R.attr.colorOnPrimaryContainer;

    private ContactStyle() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static float sp(Context context, float value) {
        // TypedValue.applyDimension 走系统字体缩放；API 34 起 DisplayMetrics.scaledDensity 已废弃
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                context.getResources().getDisplayMetrics());
    }

    /** 解析主题属性（如 ?attr/colorSurface）为具体颜色 */
    static int color(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int value = ta.getColor(0, Color.BLACK);
        ta.recycle();
        return value;
    }

    /** 把 overlay 按 ratio(0~1) 混到 base 上：ratio 越大越接近 overlay */
    static int blend(int base, int overlay, float ratio) {
        float keep = 1f - ratio;
        return Color.argb(
                Math.round(Color.alpha(base) * keep + Color.alpha(overlay) * ratio),
                Math.round(Color.red(base) * keep + Color.red(overlay) * ratio),
                Math.round(Color.green(base) * keep + Color.green(overlay) * ratio),
                Math.round(Color.blue(base) * keep + Color.blue(overlay) * ratio));
    }

    static String emptyIfNull(String value) {
        return value == null ? "" : value.trim();
    }
}
