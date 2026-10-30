package com.offblink.keyou;

/**
 * 联系人实体（对应 SQLite contact 表）。
 * 字段 id/name/phone/email + gender（gender 由 VERSION 2 升级新增，见 ContactDBHelper）。
 * 可变对象：编辑页直接 set 后整体 update。
 */
public class Contact {

    private long id;
    private String name;
    private String phone;
    private String email;
    private String gender;

    public Contact() {
    }

    public Contact(long id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    public Contact(long id, String name, String phone, String email, String gender) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.gender = gender;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
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
}
