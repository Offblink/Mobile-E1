package com.offblink.keyou;

import java.util.List;

/**
 * 任务 7：Gson 反序列化目标实体（字段与 server 的 Mate 对齐）。
 * Gson 会按字段名自动映射 JSON key；@SerializedName 可处理命名不一致。
 */
public class Mate {

    private long id;
    private String name;
    private String phone;
    private String email;
    private String gender;
    private String remark;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getGender() {
        return gender;
    }

    public String getRemark() {
        return remark;
    }

    /** Gson 解析结果的一行摘要（对比页展示用） */
    public String brief() {
        return id + " " + name + " " + phone;
    }

    /** 解析结果统计：从 List<Mate> 提取（对比页展示用） */
    public static String summarize(List<Mate> mates) {
        if (mates == null || mates.isEmpty()) {
            return "(空)";
        }
        StringBuilder sb = new StringBuilder();
        for (Mate m : mates) {
            sb.append(m.brief()).append('\n');
        }
        return sb.toString().trim();
    }
}
