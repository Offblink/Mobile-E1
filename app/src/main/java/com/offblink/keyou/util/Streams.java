package com.offblink.keyou.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 流读取兜底。
 *
 * {@code InputStream.readAllBytes()} 是 API 33 才有的方法，本项目 minSdk 26，
 * 所以在 26–32 的设备上直接用它会 NoSuchMethodError；这里给一份等价实现。
 */
public final class Streams {

    private static final int BUFFER_SIZE = 8 * 1024;

    private Streams() {
    }

    /** 读完整个流（调用方负责 close，通常配合 try-with-resources） */
    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
