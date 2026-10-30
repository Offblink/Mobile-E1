package com.offblink.keyou;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 任务 2：编辑昵称并回传 —— setResult + finish，由 MainActivity 的
 * registerForActivityResult 接收（对应指导书 SecondActivity 回传样例）。
 */
public class EditNicknameActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/EditNickname";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_nickname);
        Log.d(TAG, "onCreate");

        EditText etNickname = findViewById(R.id.et_nickname);
        etNickname.setText(getIntent().getStringExtra("nickname"));

        Button btnSubmit = findViewById(R.id.btn_submit);
        btnSubmit.setOnClickListener(v -> {
            Intent reply = new Intent();
            reply.putExtra("nickname", etNickname.getText().toString().trim());
            setResult(RESULT_OK, reply);   // 先 setResult 再 finish，否则回传为空
            finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
