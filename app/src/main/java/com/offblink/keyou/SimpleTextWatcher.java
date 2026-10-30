package com.offblink.keyou;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * TextWatcher 空实现基类：只覆写关心的回调（搜索框输入即查用）。
 */
public abstract class SimpleTextWatcher implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
}
