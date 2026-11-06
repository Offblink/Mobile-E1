package com.offblink.keyou.server.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.offblink.keyou.server.entity.AppFile;
import com.offblink.keyou.server.util.Times;

/**
 * 班级群文件接口（客户端"班级群 - 文件"页）。
 *
 * GET  /api/files            文件列表（id 倒序）
 * POST /api/file             multipart/form-data，字段 file（必填）+ uploader（可选），同名覆盖
 * GET  /api/file/{name}      二进制下载（文件名 URL 解码后取 basename）
 *
 * 文件本体落在 ${catalina.base}/files（可用 -Dkeyou.files.dir=... 覆盖），不进 WAR。
 * 统一响应：{code, message, data}（见 ApiResult）；下载接口直接吐字节流。
 */
@RestController
@RequestMapping("/api")
public class FileController {

    private static final Log log = LogFactory.getLog(FileController.class);

    private static final String COLUMNS = "id, name, size, uploader, uploaded_at";

    /** app_file.name 是 VARCHAR(200)。 */
    private static final int NAME_MAX = 200;

    private static final RowMapper<AppFile> ROW = (rs, i) -> {
        AppFile f = new AppFile();
        f.setId(rs.getInt("id"));
        f.setName(rs.getString("name"));
        f.setSize(rs.getLong("size"));
        f.setUploader(rs.getString("uploader"));
        f.setUploadedAt(Times.fmt(rs.getTimestamp("uploaded_at")));
        return f;
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/files")
    public ApiResult list() {
        List<AppFile> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM app_file ORDER BY id DESC", ROW);
        return ApiResult.ok(list);
    }

    @PostMapping("/file")
    public ApiResult upload(@RequestParam(value = "file", required = false) MultipartFile file,
                            @RequestParam(value = "uploader", required = false) String uploader) {
        if (file == null) {
            return ApiResult.error(400, "file 不能为空");
        }
        String name = safeName(file.getOriginalFilename());
        if (name.isEmpty()) {
            return ApiResult.error(400, "文件名不能为空");
        }
        if (name.length() > NAME_MAX) {
            return ApiResult.error(400, "文件名超长（最多 " + NAME_MAX + " 字符）");
        }

        Path dir = storageDir();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(name).normalize();
            if (!target.startsWith(dir)) {
                return ApiResult.error(400, "非法文件名");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("save uploaded file '" + name + "' to " + dir + " failed", e);
            return ApiResult.error(500, "文件保存失败：" + e.getMessage());
        }

        // uk_name 保证同名唯一：先更新（覆盖旧记录），没有再插入
        long size = file.getSize();
        int rows = jdbcTemplate.update(
                "UPDATE app_file SET size = ?, uploader = ?, uploaded_at = CURRENT_TIMESTAMP WHERE name = ?",
                size, uploader, name);
        if (rows == 0) {
            jdbcTemplate.update(
                    "INSERT INTO app_file(name, size, uploader) VALUES (?, ?, ?)", name, size, uploader);
        }
        return ApiResult.ok(List.of(byName(name)));
    }

    @GetMapping("/file/{name:.+}")
    public void download(@PathVariable("name") String name, HttpServletResponse response) throws IOException {
        // 路径变量已由容器按 UTF-8 解码过一次，这里只做 basename 收敛 + 目录内校验
        String fileName = safeName(name);
        Path dir = storageDir();
        Path target = dir.resolve(fileName).normalize();
        if (fileName.isEmpty() || !target.startsWith(dir) || !Files.isRegularFile(target)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(Files.size(target));
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        try (InputStream in = Files.newInputStream(target)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }

    private AppFile byName(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT " + COLUMNS + " FROM app_file WHERE name = ?", ROW, name);
    }

    /** 只取 basename，剔除 Windows/Unix 分隔符与空字节，挡住 ../ 路径穿越。 */
    private static String safeName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replace("\u0000", "").trim();
        return ".".equals(cleaned) || "..".equals(cleaned) ? "" : cleaned;
    }

    /** 默认 ${catalina.base}/files，可用系统属性 keyou.files.dir 覆盖（测试/换盘不用重打包）。 */
    private static Path storageDir() {
        String override = System.getProperty("keyou.files.dir");
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim()).toAbsolutePath().normalize();
        }
        String base = System.getProperty("catalina.base");
        if (base == null || base.trim().isEmpty()) {
            base = System.getProperty("java.io.tmpdir");
        }
        return Paths.get(base, "files").toAbsolutePath().normalize();
    }
}
