package com.offblink.keyou.server.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.offblink.keyou.server.entity.Mate;

/**
 * 班级成员 Mapper：参数化 SQL（XML 实现见 resources/mapper/MateMapper.xml）。
 */
public interface MateMapper {

    /** 分页 + 姓名模糊查询 */
    List<Mate> selectPage(@Param("kw") String kw,
                          @Param("offset") int offset,
                          @Param("limit") int limit);

    /** 模糊查询总数（分页用） */
    long countByKw(@Param("kw") String kw);

    int insert(Mate mate);

    int update(Mate mate);

    int deleteById(@Param("id") long id);

    Mate selectById(@Param("id") long id);
}
