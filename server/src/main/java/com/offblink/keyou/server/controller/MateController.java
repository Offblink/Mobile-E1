package com.offblink.keyou.server.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offblink.keyou.server.entity.Mate;
import com.offblink.keyou.server.mapper.MateMapper;

/**
 * 任务 9：班级成员 REST 接口（指导书"员工管理"平替，CRUD + 分页验收点逐一对应）。
 *
 * GET    /api/mates?page=&size=&kw=   分页列表（姓名模糊查询）
 * POST   /api/mate                    新增（JSON body）
 * PUT    /api/mate                    修改（JSON body 带 id）
 * DELETE /api/mate/{id}               删除
 *
 * 统一响应：{code, message, data}（见 ApiResult）。
 */
@RestController
@RequestMapping("/api")
public class MateController {

    @Autowired
    private MateMapper mateMapper;

    @GetMapping("/mates")
    public ApiResult list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(defaultValue = "") String kw) {
        // 防御非法分页参数（缺省 400 语义前移到可读提示）
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 10;
        }
        int offset = (page - 1) * size;
        List<Mate> list = mateMapper.selectPage(kw, offset, size);
        long total = mateMapper.countByKw(kw);
        return ApiResult.page(list, total);
    }

    @PostMapping("/mate")
    public ApiResult add(@RequestBody Mate mate) {
        if (mate.getName() == null || mate.getName().trim().isEmpty()) {
            return ApiResult.error(400, "name 不能为空");
        }
        mateMapper.insert(mate);
        return ApiResult.ok(List.of(mate));
    }

    @PutMapping("/mate")
    public ApiResult update(@RequestBody Mate mate) {
        if (mate.getId() == null) {
            return ApiResult.error(400, "修改必须带 id");
        }
        int rows = mateMapper.update(mate);
        if (rows == 0) {
            return ApiResult.error(404, "id=" + mate.getId() + " 不存在");
        }
        return ApiResult.ok();
    }

    @DeleteMapping("/mate/{id}")
    public ApiResult delete(@PathVariable("id") long id) {
        int rows = mateMapper.deleteById(id);
        if (rows == 0) {
            return ApiResult.error(404, "id=" + id + " 不存在");
        }
        return ApiResult.ok();
    }
}
