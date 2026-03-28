package com.calmara.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calmara.model.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {

    @Select("SELECT * FROM alert_record WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<AlertRecord> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM alert_record WHERE status = #{status} ORDER BY created_at DESC")
    List<AlertRecord> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM alert_record WHERE risk_level = #{riskLevel} ORDER BY created_at DESC")
    List<AlertRecord> findByRiskLevel(@Param("riskLevel") String riskLevel);

    @Select("SELECT COUNT(*) FROM alert_record WHERE status = #{status}")
    Long countByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM alert_record WHERE risk_level = #{riskLevel}")
    Long countByRiskLevel(@Param("riskLevel") String riskLevel);

    @Select("SELECT * FROM alert_record WHERE created_at >= #{startTime} ORDER BY created_at DESC")
    List<AlertRecord> findRecentAlerts(@Param("startTime") LocalDateTime startTime);

    @Update("UPDATE alert_record SET status = #{status}, handler_id = #{handlerId}, " +
            "handler_name = #{handlerName}, handle_note = #{handleNote}, " +
            "handled_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("handlerId") Long handlerId, @Param("handlerName") String handlerName,
                     @Param("handleNote") String handleNote);

    @Update("UPDATE alert_record SET email_sent = 1, email_sent_at = NOW() WHERE id = #{id}")
    int markEmailSent(@Param("id") Long id);

    @Select("SELECT DATE(created_at) as date, risk_level, COUNT(*) as count " +
            "FROM alert_record WHERE created_at >= #{startTime} " +
            "GROUP BY DATE(created_at), risk_level ORDER BY date")
    List<Map<String, Object>> dailyAlertStats(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT * FROM alert_record WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT #{limit}")
    List<AlertRecord> findPendingAlerts(@Param("limit") int limit);
}
