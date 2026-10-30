package com.offblink.keyou;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * 班级成员新增/编辑表单（AlertDialog 内嵌表单布局）。
 * 复用 ContactEditActivity 的表单布局（同一组字段），保存回调交给页面发请求。
 */
public final class MateFormDialog {

    public interface OnSavedListener {
        void onSaved(Mate mate);
    }

    private MateFormDialog() {
    }

    /** mate==null 为新增；否则编辑（保留 id 走 PUT） */
    public static void show(Context context, Mate mate, OnSavedListener listener) {
        LinearLayout form = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.dialog_mate_form, null);
        android.widget.EditText etName = form.findViewById(R.id.et_m_name);
        android.widget.EditText etPhone = form.findViewById(R.id.et_m_phone);
        android.widget.EditText etEmail = form.findViewById(R.id.et_m_email);
        android.widget.EditText etRemark = form.findViewById(R.id.et_m_remark);

        if (mate != null) {
            etName.setText(mate.getName());
            etPhone.setText(mate.getPhone());
            etEmail.setText(mate.getEmail());
            etRemark.setText(mate.getRemark());
        }

        new AlertDialog.Builder(context)
                .setTitle(mate == null ? R.string.mate_add_title : R.string.mate_edit_title)
                .setView(form)
                .setPositiveButton(R.string.contact_save, (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(context, R.string.contact_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Mate saved = new Mate();
                    if (mate != null) {
                        saved.setId(mate.getId());   // 有 id = PUT 编辑
                    }
                    saved.setName(name);
                    saved.setPhone(etPhone.getText().toString().trim());
                    saved.setEmail(etEmail.getText().toString().trim());
                    saved.setRemark(etRemark.getText().toString().trim());
                    saved.setGender(mate != null ? mate.getGender() : "保密");
                    listener.onSaved(saved);
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }
}
