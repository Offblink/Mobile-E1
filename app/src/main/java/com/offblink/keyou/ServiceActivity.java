package com.offblink.keyou;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 服务演示：绑定式 Service（bindService 拿 IBinder 直接调方法）。
 * 与启动式 DownloadService 对比：绑定的生命周期跟调用方绑定，解绑即断开。
 */
public class ServiceActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Service";

    private BoundNoteService boundService;
    private boolean serviceBound = false;

    private final android.content.ServiceConnection serviceConnection =
            new android.content.ServiceConnection() {
                @Override
                public void onServiceConnected(android.content.ComponentName name,
                                               android.os.IBinder service) {
                    boundService = ((BoundNoteService.LocalBinder) service).getService();
                    serviceBound = true;
                    AppLog.i(TAG, "onServiceConnected: 绑定成功");
                }

                @Override
                public void onServiceDisconnected(android.content.ComponentName name) {
                    boundService = null;
                    serviceBound = false;
                    AppLog.i(TAG, "onServiceDisconnected: 连接断开");
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);
        AppLog.d(TAG, "onCreate");

        TextView tvNote = findViewById(R.id.tv_bound_note);
        findViewById(R.id.btn_bind_service).setOnClickListener(v ->
                bindService(new Intent(this, BoundNoteService.class),
                        serviceConnection, BIND_AUTO_CREATE));
        findViewById(R.id.btn_unbind_service).setOnClickListener(v -> {
            if (serviceBound) {
                unbindService(serviceConnection);
                serviceBound = false;
                boundService = null;
                Toast.makeText(this, R.string.service_unbound, Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_make_note).setOnClickListener(v -> {
            if (serviceBound && boundService != null) {
                tvNote.setText(boundService.makeNote("明天下午体育课改到 401"));
            } else {
                Toast.makeText(this, R.string.service_not_bound, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}
