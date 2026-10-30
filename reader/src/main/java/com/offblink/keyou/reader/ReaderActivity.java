package com.offblink.keyou.reader;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 6 的「第二个 App」：跨 App 读取课友主 App 的 ContactProvider。
 * P0 骨架阶段只占位，任务 5/6 落地时实现 ContentResolver 查询。
 */
public class ReaderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
    }
}
