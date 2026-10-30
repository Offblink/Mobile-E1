package com.offblink.keyou;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

/**
 * 定位与音频：一次定位（运行时权限）+ MediaPlayer 播放 assets 音频。
 */
public class DeviceActivity extends KeyouActivity {

    private static final String TAG = "Keyou/Device";

    private TextView tvLocation;
    private MediaPlayer mediaPlayer;

    /** 定位权限（fine + coarse 一次申请，不申请后台定位） */
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        AppLog.d(TAG, "onCreate");

        tvLocation = findViewById(R.id.tv_location);
        Button btnLocation = findViewById(R.id.btn_location);
        btnLocation.setOnClickListener(v -> requestLocation());
        Button btnPlay = findViewById(R.id.btn_play_music);
        btnPlay.setOnClickListener(v -> playAssetMusic());
        Button btnStop = findViewById(R.id.btn_stop_music);
        btnStop.setOnClickListener(v -> stopMusic());
    }

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

    /** 一次定位：getLastKnownLocation（持续定位需 requestLocationUpdates） */
    @SuppressWarnings("MissingPermission")  // 上方已校验授权
    private void fetchLocation() {
        LocationManager lm = getSystemService(LocationManager.class);
        Location loc = null;
        try {
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
            AppLog.e(TAG, "定位权限运行中被撤销", e);
        }
        if (loc != null) {
            tvLocation.setText(getString(R.string.location_result,
                    loc.getLatitude(), loc.getLongitude(), loc.getProvider()));
            AppLog.i(TAG, "定位: " + loc.getLatitude() + ", " + loc.getLongitude());
        } else {
            tvLocation.setText(R.string.location_none);
        }
    }

    /** 播放 assets 内置音频（setDataSource 后 prepareAsync，规避状态机异常） */
    private void playAssetMusic() {
        stopMusic();
        try {
            mediaPlayer = new MediaPlayer();
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("bgm.wav");
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Toast.makeText(this, R.string.music_playing, Toast.LENGTH_SHORT).show();
            });
            mediaPlayer.setOnCompletionListener(mp ->
                    Toast.makeText(this, R.string.music_done, Toast.LENGTH_SHORT).show());
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            AppLog.e(TAG, "MediaPlayer 播放失败", e);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMusic();
    }
}
