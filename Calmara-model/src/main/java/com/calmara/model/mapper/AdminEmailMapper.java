package com.calmara.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calmara.model.entity.AdminEmail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminEmailMapper extends BaseMapper<AdminEmail> {

    @Select("SELECT * FROM admin_email WHERE is_active = 1")
    List<AdminEmail> findActiveAdmins();

    @Select("SELECT * FROM admin_email WHERE email = #{email}")
    AdminEmail findByEmail(String email);

    @Select("SELECT email FROM admin_email WHERE is_active = 1")
    List<String> findActiveEmails();
}
