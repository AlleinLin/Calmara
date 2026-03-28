package com.calmara.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calmara.model.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT * FROM role WHERE role_key = #{roleKey}")
    Role findByRoleKey(@Param("roleKey") String roleKey);

    @Select("SELECT * FROM role ORDER BY id")
    List<Role> findAllRoles();

    @Select("SELECT r.* FROM role r " +
            "INNER JOIN user u ON u.role = r.role_key " +
            "WHERE u.id = #{userId}")
    Role findRoleByUserId(@Param("userId") Long userId);
}
