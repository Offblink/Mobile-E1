package com.offblink.keyou.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * multipart/form-data 拼装（纯 java.io，单测可在 JVM 直接跑）。
 *
 * 与电脑端（desktop/…/Api.upload）同一条线格式：
 * file 段在前（Content-Type 固定 application/octet-stream），uploader 段在后，
 * 每段 CRLF 分隔、结尾 --boundary--；文件名按 UTF-8 原样写进 header
 * —— 服务端 FileController 就是这么解出来的中文名。
 *
 * 文件名只留 basename 并去掉引号/换行：既防 header 注入，也防 ../ 路径穿越。
 */
public final class MultipartBody {

    /** 上传段固定用二进制流：服务端不看类型，下载也是一律 octet-stream */
    public static final String MIME = "application/octet-stream";

    private MultipartBody() {
    }

    /** 边界串带时间戳与文件大小：同一毫秒内重复上传也不会撞 */
    public static String newBoundary(long fileSize) {
        return "----KeyouBoundary" + System.currentTimeMillis()
                + Integer.toHexString((int) (fileSize ^ (fileSize >>> 32)));
    }

    public static String contentType(String boundary) {
        return "multipart/form-data; boundary=" + boundary;
    }

    /** 只留文件名本身：去路径、在第一个 CR/LF 处截断、去引号 */
    public static String safeFileName(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '\r' || ch == '\n') {
                name = name.substring(0, i);
                break;
            }
        }
        return name.replace("\"", "").trim();
    }

    static byte[] head(String boundary, String fileName, String mime) {
        return ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + safeFileName(fileName) + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    static byte[] tail(String boundary, String uploader) {
        String who = uploader == null ? "" : uploader.replace("\r", " ").replace("\n", " ");
        return ("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"uploader\"\r\n\r\n"
                + who + "\r\n--" + boundary + "--\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** 整个 body 的字节数：HttpURLConnection 用它设 fixed-length streaming */
    public static long contentLength(String boundary, String fileName, String uploader,
                                     long fileSize) {
        return head(boundary, fileName, MIME).length + fileSize
                + tail(boundary, uploader).length;
    }

    /** 边读文件边写 socket，大文件不进内存 */
    public static void write(OutputStream out, String boundary, String fileName,
                             String mime, File file, String uploader) throws IOException {
        out.write(head(boundary, fileName, mime));
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        out.write(tail(boundary, uploader));
    }

    /** 小数据版本（单测用）：与 File 版本字节完全一致 */
    public static void write(OutputStream out, String boundary, String fileName,
                             String mime, byte[] data, String uploader) throws IOException {
        out.write(head(boundary, fileName, mime));
        out.write(data);
        out.write(tail(boundary, uploader));
    }
}
