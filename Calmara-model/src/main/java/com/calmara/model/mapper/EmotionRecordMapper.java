package com.calmara.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calmara.model.entity.EmotionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface EmotionRecordMapper extends BaseMapper<EmotionRecord> {

    @Select("SELECT * FROM emotion_record WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<EmotionRecord> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM emotion_record WHERE session_id = #{sessionId} ORDER BY created_at DESC")
    List<EmotionRecord> findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM emotion_record WHERE risk_level = #{riskLevel} ORDER BY created_at DESC")
    List<EmotionRecord> findByRiskLevel(@Param("riskLevel") String riskLevel);

    @Select("SELECT COUNT(*) FROM emotion_record WHERE risk_level = #{riskLevel}")
    Long countByRiskLevel(@Param("riskLevel") String riskLevel);

    @Select("SELECT fusion_emotion as emotion, COUNT(*) as count FROM emotion_record " +
            "WHERE created_at >= #{startTime} GROUP BY fusion_emotion")
    List<Map<String, Object>> countByEmotion(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT risk_level, COUNT(*) as count FROM emotion_record " +
            "WHERE created_at >= #{startTime} GROUP BY risk_level")
    List<Map<String, Object>> countByRiskLevelAfter(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT DATE(created_at) as date, fusion_emotion as emotion, COUNT(*) as count " +
            "FROM emotion_record WHERE created_at >= #{startTime} " +
            "GROUP BY DATE(created_at), fusion_emotion ORDER BY date")
    List<Map<String, Object>> dailyEmotionStats(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT * FROM emotion_record WHERE created_at >= #{startTime} ORDER BY created_at DESC")
    List<EmotionRecord> findRecentRecords(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT COUNT(DISTINCT user_id) FROM emotion_record WHERE risk_level = 'HIGH'")
    Long countHighRiskUsers();
}
