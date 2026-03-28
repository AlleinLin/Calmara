package com.calmara.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calmara.model.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    @Select("SELECT p.* FROM permission p " +
            "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN role r ON r.id = rp.role_id " +
            "WHERE r.role_key = #{roleKey}")
    List<Permission> findPermissionsByRoleKey(@Param("roleKey") String roleKey);

    @Select("SELECT p.* FROM permission p " +
            "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN user u ON u.role = (SELECT role_key FROM role WHERE id = rp.role_id) " +
            "WHERE u.id = #{userId}")
    List<Permission> findPermissionsByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM permission WHERE resource_type = #{resourceType}")
    List<Permission> findByResourceType(@Param("resourceType") String resourceType);

    @Select("SELECT * FROM permission WHERE parent_id = #{parentId}")
    List<Permission> findByParentId(@Param("parentId") Long parentId);

    @Select("SELECT * FROM permission ORDER BY parent_id, id")
    List<Permission> findAllPermissions();
}
