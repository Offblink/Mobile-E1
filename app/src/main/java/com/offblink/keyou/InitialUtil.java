package com.offblink.keyou;

import android.icu.text.Transliterator;

import java.text.Normalizer;

/**
 * 姓名 → 分组字母（A-Z，取不到归 '#'）。
 *
 * 规则（按顺序）：
 * 1. 首字符是拉丁字母 → 大写（"amy" → A）；
 * 2. 汉字 → 用系统 ICU 的 "Han-Latin" 音译拿拼音首字母（"张三" → zhāng → Z）；
 *    android.icu 是 API 24+ 的框架能力，音译失败时退到第 3 步；
 * 3. 其余（数字/符号/空名）→ '#'。
 *
 * 只在「展示前派生分组」时用，不落库 —— contact 表结构不因为分组而改动。
 */
public final class InitialUtil {

    /** Transliterator.getInstance 有开销，全局建一次复用（无状态、线程安全） */
    private static Transliterator hanLatin;

    private InitialUtil() {
    }

    public static char of(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return '#';
        }
        char c = n.charAt(0);
        if (c >= 'a' && c <= 'z') {
            return Character.toUpperCase(c);
        }
        if (c >= 'A' && c <= 'Z') {
            return c;
        }
        char pinyin = pinyinInitial(String.valueOf(c));
        return pinyin != 0 ? pinyin : '#';
    }

    /**
     * 单字 → 拼音首字母；失败返回 0。
     *
     * 坑：Han-Latin 的输出带声调（"ān"、"zhāng"），'ā' 不在 a-z 区间，
     * 直接扫 ASCII 会漏掉首字母、取到后面的辅音（"安然" 曾被判成 N）。
     * 必须先剥掉组合音标再取首字母。
     */
    private static char pinyinInitial(String han) {
        try {
            if (hanLatin == null) {
                hanLatin = Transliterator.getInstance("Han-Latin");
            }
            String latin = hanLatin.transliterate(han);
            latin = Normalizer.normalize(latin, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "");
            for (int i = 0; i < latin.length(); i++) {
                char ch = Character.toLowerCase(latin.charAt(i));
                if (ch >= 'a' && ch <= 'z') {
                    return Character.toUpperCase(ch);
                }
            }
        } catch (Throwable ignored) {
            // 个别 ROM 没有 Han-Latin 音译表：吞掉异常，调用方让该条落到 '#' 组
        }
        return 0;
    }
}
