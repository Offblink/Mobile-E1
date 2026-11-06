package com.offblink.keyou.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 上传 body 的线格式：与 desktop/…/Api.upload 逐字节一致，
 * 服务端 FileController 才认得出 file / uploader 两段（中文名按 UTF-8 写 header）。
 */
public class MultipartBodyTest {

    private static final String BOUNDARY = "----KeyouBoundary1a2b3c";
    private static final String UPLOADER = "我";

    @Test
    public void bodyMatchesDesktopWireFormat() throws Exception {
        byte[] data = "hello 中文".getBytes(StandardCharsets.UTF_8);
        String head = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"报告.txt\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String tail = "\r\n--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"uploader\"\r\n\r\n"
                + UPLOADER + "\r\n--" + BOUNDARY + "--\r\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MultipartBody.write(out, BOUNDARY, "报告.txt", MultipartBody.MIME, data, UPLOADER);
        byte[] body = out.toByteArray();
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);

        assertEquals(body.length,
                MultipartBody.contentLength(BOUNDARY, "报告.txt", UPLOADER, data.length));
        assertArrayEquals(headBytes, Arrays.copyOfRange(body, 0, headBytes.length));
        assertArrayEquals(data, Arrays.copyOfRange(body, headBytes.length,
                headBytes.length + data.length));
        assertArrayEquals(tailBytes, Arrays.copyOfRange(body,
                body.length - tailBytes.length, body.length));
    }

    @Test
    public void fileNameIsReducedToBasenameWithoutQuotesOrBreaks() {
        assertEquals("报告.txt", MultipartBody.safeFileName("../目录/报告.txt"));
        assertEquals("a.txt", MultipartBody.safeFileName("C:\\tmp\\a.txt"));
        assertEquals("evil.txt", MultipartBody.safeFileName("ev\"il.txt"));
        assertEquals("x.txt", MultipartBody.safeFileName("x.txt\r\nInjected: 1"));
        assertEquals("", MultipartBody.safeFileName(null));
        assertEquals("", MultipartBody.safeFileName("   "));
    }

    @Test
    public void uploaderNewlinesAreNeutralised() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MultipartBody.write(out, BOUNDARY, "a.txt", MultipartBody.MIME,
                new byte[]{1}, "我\r\n伪造: 1");
        String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.contains("name=\"uploader\"\r\n\r\n我  伪造: 1\r\n--"));
    }
}
