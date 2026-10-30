package com.offblink.keyou;

/**
 * 联系人实体（任务 3 模拟数据源版；任务 5 会升级为 SQLite 表 contact 的映射，
 * 届时补 gender 字段并接 ContactDao）。
 */
public class Contact {

    private long id;
    private String name;
    private String phone;

    public Contact(long id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
