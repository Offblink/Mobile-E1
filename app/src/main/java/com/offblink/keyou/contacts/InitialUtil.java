package com.offblink.keyou.contacts;

import android.icu.text.Transliterator;
import android.os.Build;

import java.text.Normalizer;

/**
 * 姓名 → 分组字母（A-Z，取不到归 '#'）。
 *
 * 规则（按顺序）：
 * 1. 首字符是拉丁字母 → 大写（"amy" → A）；
 * 2. 汉字 → 先查常见姓氏首字母表（{@link #SURNAMES}），命中即返回；
 *    未命中再用系统 ICU 的 "Han-Latin" 音译拿拼音首字母（"张三" → zhāng → Z，需 API 29+）；
 *    两者都失败 → '#' 组；
 * 3. 其余（数字/符号/空名）→ '#'。
 *
 * 姓氏表专治 API 26–28：那些机器没有可用的 Han-Latin 音译，
 * 没有这张表时中文姓名会整批落进 '#' 组（分组/索引条形同虚设）。
 * 表只收录常见姓氏，且按「做姓氏时的读音」归类（单 Shàn→S、仇 Qiú→Q、区 Ōu→O、查 Zhā→Z …）。
 *
 * 只在「展示前派生分组」时用，不落库 —— contact 表结构不因为分组而改动。
 */
public final class InitialUtil {

    /** Transliterator.getInstance 有开销，全局建一次复用（无状态、线程安全） */
    private static Transliterator hanLatin;

    /**
     * 常见姓氏首字母表：下标 0..25 依次对应 A..Z。
     * 一个字符只会出现在一个分组里；查表就是 26 次 indexOf（几十纳秒），比建 Map 更省。
     */
    private static final String[] SURNAMES = {
            /* A */ "安艾敖阿",
            /* B */ "白包毕边卞柏鲍班薄卜巴宾闭步",
            /* C */ "陈曹蔡崔程常车褚柴储迟楚池丛岑晁成",
            /* D */ "邓董杜丁段戴狄窦党刁但都邸豆",
            /* E */ "鄂",
            /* F */ "冯范方付傅樊房费封丰伏符富奉",
            /* G */ "郭高龚顾葛耿关甘古宫桂巩勾戈盖管谷国干公官苟",
            /* H */ "何胡黄韩郝侯贺洪华霍海杭衡花哈扈和黑郇",
            /* I */ "",
            /* J */ "姜蒋贾金江焦靳景井吉简季纪姬鞠冀荆敬计晋揭居嵇",
            /* K */ "孔康柯匡寇阚况",
            /* L */ "李刘林梁罗吕龙卢陆雷黎廖鲁柳凌蓝冷娄楼蔺鹿隆逯连练廉栗劳利来芦骆赖郎",
            /* M */ "马孟莫毛梅苗蒙米穆麦缪慕母茅明满门麻",
            /* N */ "宁倪聂牛农南那",
            /* O */ "欧区",
            /* P */ "彭潘庞裴蒲朴皮盘普浦平",
            /* Q */ "钱秦邱乔覃齐仇曲屈戚祁强卿权漆全阙亓",
            /* R */ "任冉阮饶荣芮戎茹",
            /* S */ "孙宋沈苏史石施邵尚舒盛时司申束帅索宿佘商沙桑斯隋单",
            /* T */ "田唐陶汤童谭涂滕佟屠谈檀",
            /* U */ "",
            /* V */ "",
            /* W */ "王吴魏汪伍温文万韦翁巫邬闻位乌危",
            /* X */ "徐谢许萧夏薛邢熊向席辛胥冼鲜相宣修幸信解",
            /* Y */ "杨姚叶袁于余尹严颜易岳殷俞虞郁燕原伊苑游喻阴阳尧由印冶应玉银元乐",
            /* Z */ "张赵周郑朱钟曾邹章翟庄詹祝左宗仲臧甄卓祖支战诸湛智展昝植查",
    };

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
        // 常见姓氏先查表：API 26–28 没有音译能力，全靠这张表分组
        if (c >= 0x4E00 && c <= 0x9FFF) {
            char table = surnameInitial(c);
            if (table != 0) {
                return table;
            }
        }
        char pinyin = pinyinInitial(String.valueOf(c));
        return pinyin != 0 ? pinyin : '#';
    }

    /** 姓氏表查首字母；不是表里的姓返回 0 */
    private static char surnameInitial(char c) {
        for (int i = 0; i < SURNAMES.length; i++) {
            if (SURNAMES[i].indexOf(c) >= 0) {
                return (char) ('A' + i);
            }
        }
        return 0;
    }

    /**
     * 单字 → 拼音首字母；失败返回 0。
     *
     * 坑：Han-Latin 的输出带声调（"ān"、"zhāng"），'ā' 不在 a-z 区间，
     * 直接扫 ASCII 会漏掉首字母、取到后面的辅音（"安然" 曾被判成 N）。
     * 必须先剥掉组合音标再取首字母。
     */
    private static char pinyinInitial(String han) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return 0;   // 29 以下没有可用的 Han-Latin 音译：表外字符落 '#' 组
        }
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
