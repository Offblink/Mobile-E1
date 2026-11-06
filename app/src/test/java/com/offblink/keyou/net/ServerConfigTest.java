package com.offblink.keyou.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * 真机连本机局域网时用户手输地址的规范化规则。
 * 只测纯逻辑（ServerConfig.normalize 不碰 Android API，可在 JVM 里跑）。
 */
public class ServerConfigTest {

    @Test
    public void 只填ip时补默认端口() {
        assertEquals("http://192.168.1.5:8081", ServerConfig.normalize("192.168.1.5"));
        assertEquals("http://10.0.2.2:8081", ServerConfig.normalize("10.0.2.2:8081"));
    }

    @Test
    public void 带端口或带协议原样保留() {
        assertEquals("http://192.168.1.5:9000", ServerConfig.normalize("192.168.1.5:9000"));
        assertEquals("http://192.168.1.5:8081", ServerConfig.normalize("http://192.168.1.5:8081"));
        assertEquals("https://mypc.local:8081", ServerConfig.normalize("https://mypc.local"));
    }

    @Test
    public void 忽略首尾空白与尾部斜杠() {
        assertEquals("http://192.168.43.15:8081", ServerConfig.normalize("  192.168.43.15  "));
        assertEquals("http://192.168.43.15:8081", ServerConfig.normalize("http://192.168.43.15:8081/"));
    }

    @Test
    public void 带路径的地址不接受() {
        // 路径由 HttpUtil 拼，地址里再带一段会拼成 //api/mates
        assertNull(ServerConfig.normalize("192.168.1.5:8081/api/mates"));
        assertNull(ServerConfig.normalize("http://192.168.1.5:8081/api"));
    }

    @Test
    public void 非法输入返回null() {
        assertNull(ServerConfig.normalize(null));
        assertNull(ServerConfig.normalize(""));
        assertNull(ServerConfig.normalize("   "));
        assertNull(ServerConfig.normalize("192.168.1.5:0"));
        assertNull(ServerConfig.normalize("192.168.1.5:99999"));
    }

    @Test
    public void 默认地址往返不变() {
        assertEquals(ServerConfig.HOST_EMULATOR, ServerConfig.normalize(ServerConfig.HOST_EMULATOR));
        assertEquals(ServerConfig.HOST_LOOPBACK, ServerConfig.normalize(ServerConfig.HOST_LOOPBACK));
    }
}
