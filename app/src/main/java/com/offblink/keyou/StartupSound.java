package com.offblink.keyou;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

/**
 * 启动音：App 进程冷启动时播一次 assets/bgm.wav —— 固定开启，没有开关。
 *
 * 只在进程内响一次 —— 进登录页响过之后，登录进主页、旋转屏、切 Tab 都不会再响；
 * 杀掉进程再打开才会重新响，这就是「开机启动音」的边界。
 */
public final class StartupSound {

    private static final String TAG = "Keyou/Sound";
    private static final String ASSET = "bgm.wav";
    private static final float VOLUME = 0.6f;

    private static boolean playedThisProcess;
    private static MediaPlayer player;

    private StartupSound() {
    }

    /** 冷启动入口调用：每个进程只响一次 */
    public static void playOnceOnLaunch(Context ctx) {
        if (playedThisProcess) {
            return;
        }
        playedThisProcess = true;
        play(ctx);
    }

    private static void play(Context ctx) {
        stop();
        try {
            AssetFileDescriptor afd = ctx.getAssets().openFd(ASSET);
            final MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setVolume(VOLUME, VOLUME);
            mp.setOnCompletionListener(m -> {
                m.release();
                if (player == m) {
                    player = null;
                }
            });
            mp.setOnErrorListener((m, what, extra) -> {
                AppLog.e(TAG, "MediaPlayer 错误 what=" + what + " extra=" + extra);
                m.release();
                if (player == m) {
                    player = null;
                }
                return true;
            });
            mp.prepare();
            mp.start();
            player = mp;
            AppLog.i(TAG, "播放启动音 " + ASSET);
        } catch (Exception e) {
            AppLog.e(TAG, "启动音播放失败", e);
        }
    }

    private static void stop() {
        if (player == null) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.stop();
            }
        } catch (IllegalStateException ignore) {
            // 已结束/未就绪，直接释放
        }
        player.release();
        player = null;
    }
}
