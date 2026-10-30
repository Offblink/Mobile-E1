package com.offblink.keyou;

import java.util.List;

/**
 * 任务 9：Gson 序列化/反序列化目标实体（字段与 server 的 Mate 一致）。
 * Gson 按字段名自动映射 JSON key；setter 供反序列化赋值使用。
 */
public class Mate {

    private Long id;
    private String name;
    private String phone;
    private String email;
    private String gender;
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    /** 列表行展示文本 */
    public String brief() {
        return name + " · " + (phone != null ? phone : "-")
                + (gender != null ? " · " + gender : "");
    }
}
