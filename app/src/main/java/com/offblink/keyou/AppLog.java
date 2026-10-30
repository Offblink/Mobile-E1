package com.offblink.keyou;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 应用内日志：既打 Logcat，也留一份内存环形缓冲，供「日志」页实时展示。
 *
 * 为什么要自己缓冲：读系统 logcat 需要权限（普通 App 拿不到别人的日志），
 * 而应用内事件（页面切换、网络、数据库、权限）本身就在进程里，直接记下来最可靠。
 * 缓冲只保留最近 {@link #CAPACITY} 条，页面订阅后按条推送（主线程回调）。
 */
public final class AppLog {

    public enum Level {
        V, D, I, W, E
    }

    public static final class Entry {
        public final long time;
        public final Level level;
        public final String tag;
        public final String message;

        Entry(long time, Level level, String tag, String message) {
            this.time = time;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        /** HH:mm:ss.SSS */
        public String timeText() {
            return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date(time));
        }
    }

    public interface Listener {
        void onEntry(Entry entry);
    }

    private static final int CAPACITY = 500;

    private static final List<Entry> BUFFER = Collections.synchronizedList(new ArrayList<>());
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppLog() {
    }

    public static void v(String tag, String msg) {
        write(Level.V, tag, msg, null);
    }

    public static void d(String tag, String msg) {
        write(Level.D, tag, msg, null);
    }

    public static void i(String tag, String msg) {
        write(Level.I, tag, msg, null);
    }

    public static void w(String tag, String msg) {
        write(Level.W, tag, msg, null);
    }

    public static void e(String tag, String msg) {
        write(Level.E, tag, msg, null);
    }

    public static void v(String tag, String msg, Throwable tr) {
        write(Level.V, tag, msg, tr);
    }

    public static void d(String tag, String msg, Throwable tr) {
        write(Level.D, tag, msg, tr);
    }

    public static void i(String tag, String msg, Throwable tr) {
        write(Level.I, tag, msg, tr);
    }

    public static void w(String tag, String msg, Throwable tr) {
        write(Level.W, tag, msg, tr);
    }

    public static void e(String tag, String msg, Throwable tr) {
        write(Level.E, tag, msg, tr);
    }

    /** 当前缓冲快照（按时间正序） */
    public static List<Entry> snapshot() {
        synchronized (BUFFER) {
            return new ArrayList<>(BUFFER);
        }
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void clear() {
        BUFFER.clear();
    }

    private static void write(Level level, String tag, String message, Throwable tr) {
        String text = tr == null ? message : message + " · " + tr;
        switch (level) {
            case V:
                Log.v(tag, text);
                break;
            case D:
                Log.d(tag, text);
                break;
            case I:
                Log.i(tag, text);
                break;
            case W:
                Log.w(tag, text);
                break;
            default:
                Log.e(tag, text, tr);
                break;
        }
        Entry entry = new Entry(System.currentTimeMillis(), level, tag, message);
        BUFFER.add(entry);
        while (BUFFER.size() > CAPACITY) {
            BUFFER.remove(0);
        }
        MAIN.post(() -> notifyListeners(entry));
    }

    private static void notifyListeners(@NonNull Entry entry) {
        for (Listener listener : LISTENERS) {
            listener.onEntry(entry);
        }
    }
}
