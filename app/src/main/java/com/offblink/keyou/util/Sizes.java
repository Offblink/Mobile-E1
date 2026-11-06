package com.offblink.keyou.util;

import java.util.Locale;

/**
 * 文件大小显示（B / KB / MB），群文件卡片与群文件页共用一份实现。
 * 固定 Locale.US：中文系统下 String.format 默认 locale 不影响数字，但小数点的口径要稳。
 */
public final class Sizes {

    private Sizes() {
    }

    public static String human(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", size / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
    }
}
