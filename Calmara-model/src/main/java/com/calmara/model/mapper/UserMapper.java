package com.calmara.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calmara.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM user WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    @Select("SELECT * FROM user WHERE role = #{role} AND status = 1")
    List<User> findByRole(@Param("role") String role);

    @Select("SELECT COUNT(*) FROM user WHERE role = 'USER' AND status = 1")
    Long countUsers();

    @Select("SELECT * FROM user WHERE student_id = #{studentId}")
    User findByStudentId(@Param("studentId") String studentId);

    @Select("SELECT * FROM user WHERE status = 1 ORDER BY created_at DESC LIMIT #{limit}")
    List<User> findRecentUsers(@Param("limit") int limit);

    @Update("UPDATE user SET last_login_at = NOW() WHERE id = #{userId}")
    int updateLastLoginTime(@Param("userId") Long userId);

    @Update("UPDATE user SET status = #{status}, updated_at = NOW() WHERE id = #{userId}")
    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM user WHERE role = 'USER' AND status = 1 AND DATE(created_at) = CURDATE()")
    Long countTodayNewUsers();

    @Select("SELECT * FROM user WHERE role = 'USER' AND status = 1 AND id IN " +
            "(SELECT DISTINCT user_id FROM emotion_record WHERE risk_level = 'HIGH')")
    List<User> findHighRiskUsers();
}
