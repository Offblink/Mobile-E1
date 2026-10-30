package com.offblink.keyou;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 全 App 二级页面基类：统一「返回」能力。
 *
 * 做法：onContentChanged() 在 setContentView 之后回调，基类在这里查找
 * 布局中的 R.id.btn_back（view_back_bar.xml 的 include），自动绑定 finish()。
 * 好处：每个页面只改 extends 一行，不需要重复写 findViewById + onClickListener。
 */
public class KeyouActivity extends AppCompatActivity {

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        View back = findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
    }
}
