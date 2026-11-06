package com.offblink.keyou.contacts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 分组首字母：姓氏表兜底（API 26–28 没有 Han-Latin 音译时全靠它）。
 * JVM 单测里 Build.VERSION.SDK_INT = 0，走的就是「只查表」这条真实降级路径。
 */
public class InitialUtilTest {

    @Test
    public void commonSurnamesComeFromTable() {
        assertEquals('Z', InitialUtil.of("张三"));
        assertEquals('W', InitialUtil.of("王小明"));
        assertEquals('L', InitialUtil.of("李雷"));
        assertEquals('C', InitialUtil.of("陈思远"));
        assertEquals('S', InitialUtil.of("孙悦"));
    }

    @Test
    public void surnameReadingsFollowSurnamePronunciation() {
        assertEquals('S', InitialUtil.of("单田芳"));   // Shàn，不是 dān
        assertEquals('Q', InitialUtil.of("仇英"));     // Qiú，不是 chóu
        assertEquals('O', InitialUtil.of("区伯"));     // Ōu，不是 qū
        assertEquals('X', InitialUtil.of("解缙"));     // Xiè，不是 jiě
        assertEquals('M', InitialUtil.of("缪斯"));     // Miào
        assertEquals('Z', InitialUtil.of("查良镛"));   // Zhā
        assertEquals('Y', InitialUtil.of("乐毅"));     // Yuè
    }

    @Test
    public void latinAndFallbackBuckets() {
        assertEquals('A', InitialUtil.of("amy"));
        assertEquals('A', InitialUtil.of("Amy"));
        assertEquals('L', InitialUtil.of("  李雷 "));
        assertEquals('#', InitialUtil.of("123"));
        assertEquals('#', InitialUtil.of("2019 级班长"));
        assertEquals('#', InitialUtil.of(""));
        assertEquals('#', InitialUtil.of(null));
    }
}
