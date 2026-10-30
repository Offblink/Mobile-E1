package com.offblink.keyou;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * 任务 8：我的工具箱 —— 定位 / 多媒体。
 *
 * 验收点：
 * - 定位：ACCESS_FINE/COARSE_LOCATION 运行时申请 + 一次定位显示经纬度
 *   （地图 SDK 需注册 Key，超出课内闭环；报告注明取舍，坐标+方向足够演示定位链路）；
 * - MediaPlayer：播放 assets 内置音频（prepare 异步，规避 IllegalStateException）；
 * - 相机：ACTION_IMAGE_CAPTURE 系统相机拍照回缩略图（onPictureReceived Bitmap）。
 */
public class ToolsActivity extends AppCompatActivity {

    private static final String TAG = "Keyou/Tools";

    private TextView tvLocation;
    private ImageView ivPhoto;
    private MediaPlayer mediaPlayer;

    /** 任务 8 绑定式服务：绑定后拿 IBinder 直接调方法 */
    private BoundNoteService boundService;
    private boolean serviceBound = false;

    private final android.content.ServiceConnection serviceConnection =
            new android.content.ServiceConnection() {
                @Override
                public void onServiceConnected(android.content.ComponentName name,
                                               android.os.IBinder service) {
                    boundService = ((BoundNoteService.LocalBinder) service).getService();
                    serviceBound = true;
                    Log.i(TAG, "onServiceConnected: 绑定成功");
                }

                @Override
                public void onServiceDisconnected(android.content.ComponentName name) {
                    boundService = null;
                    serviceBound = false;
                    Log.i(TAG, "onServiceDisconnected: 连接断开");
                }
            };

    /** 定位权限（fine+coarse 一次申请，foreground 一组，不申请后台定位） */
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> {
                Boolean fine = results.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarse = results.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    fetchLocation();
                } else {
                    tvLocation.setText(R.string.location_permission_denied);
                }
            });

    /** 系统相机回缩略图（getExtras 的 data 即缩略图 Bitmap） */
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Bitmap thumb = (Bitmap) result.getData().getExtras().get("data");
                            if (thumb != null) {
                                ivPhoto.setImageBitmap(thumb);
                                Log.i(TAG, "拍照回缩略图: " + thumb.getWidth() + "x" + thumb.getHeight());
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);
        Log.d(TAG, "onCreate");

        tvLocation = findViewById(R.id.tv_location);
        ivPhoto = findViewById(R.id.iv_photo);

        Button btnLocation = findViewById(R.id.btn_location);
        btnLocation.setOnClickListener(v -> requestLocation());

        Button btnPlay = findViewById(R.id.btn_play_music);
        btnPlay.setOnClickListener(v -> playAssetMusic());

        Button btnStop = findViewById(R.id.btn_stop_music);
        btnStop.setOnClickListener(v -> stopMusic());

        Button btnCamera = findViewById(R.id.btn_camera);
        btnCamera.setOnClickListener(v -> takePhoto());

        // 任务 8：绑定式 Service —— bindService 拿引用直接调方法，unbindService 断开
        TextView tvNote = findViewById(R.id.tv_bound_note);
        Button btnBind = findViewById(R.id.btn_bind_service);
        btnBind.setOnClickListener(v -> {
            Intent intent = new Intent(this, BoundNoteService.class);
            bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        });
        Button btnMakeNote = findViewById(R.id.btn_make_note);
        btnMakeNote.setOnClickListener(v -> {
            if (serviceBound && boundService != null) {
                tvNote.setText(boundService.makeNote("明天下午体育课改到 401"));
            } else {
                Toast.makeText(this, R.string.service_not_bound, Toast.LENGTH_SHORT).show();
            }
        });
        Button btnUnbind = findViewById(R.id.btn_unbind_service);
        btnUnbind.setOnClickListener(v -> {
            if (serviceBound) {
                unbindService(serviceConnection);
                serviceBound = false;
                boundService = null;
                Toast.makeText(this, R.string.service_unbound, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- 定位 ----------

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    /** 一次定位：getLastKnownLocation（教学演示；持续定位需 requestLocationUpdates） */
    @SuppressWarnings("MissingPermission")  // 上方已校验授权
    private void fetchLocation() {
        LocationManager lm = getSystemService(LocationManager.class);
        Location loc = null;
        try {
            // 现代 API 优先 GPS，回退网络；老 getLastKnownLocation 在模拟器可用
            for (String provider : new String[]{LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER}) {
                try {
                    loc = lm.getLastKnownLocation(provider);
                } catch (Exception ignore) {
                    // 单个 provider 失败继续试下一个
                }
                if (loc != null) {
                    break;
                }
            }
        } catch (SecurityException e) {
            // 权限可能在运行中被撤销（android/skills 口径：不缓存权限状态）
            Log.e(TAG, "定位权限运行中被撤销", e);
        }
        if (loc != null) {
            tvLocation.setText(getString(R.string.location_result,
                    loc.getLatitude(), loc.getLongitude(), loc.getProvider()));
            Log.i(TAG, "定位: " + loc.getLatitude() + ", " + loc.getLongitude());
        } else {
            tvLocation.setText(R.string.location_none);
        }
    }

    // ---------- MediaPlayer ----------

    /** 播放 assets 内置音频（setDataSource 后 prepareAsync，规避状态机异常） */
    private void playAssetMusic() {
        stopMusic();
        try {
            mediaPlayer = new MediaPlayer();
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("bgm.wav");
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            // prepareAsync：异步准备完成回调 OnPreparedListener 再 start，
            // 直接 start 未 prepare 的 MediaPlayer 会抛 IllegalStateException（排错表原题）
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Toast.makeText(this, R.string.music_playing, Toast.LENGTH_SHORT).show();
            });
            mediaPlayer.setOnCompletionListener(mp ->
                    Toast.makeText(this, R.string.music_done, Toast.LENGTH_SHORT).show());
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "MediaPlayer 播放失败", e);
            Toast.makeText(this, getString(R.string.music_failed, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopMusic() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ---------- 相机 ----------

    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(intent);
        } else {
            Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMusic();
        // 绑定式服务必须成对解绑（否则 ServiceConnection 泄漏）
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        Log.d(TAG, "onDestroy");
    }
}
