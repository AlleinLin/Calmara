package com.calmara.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calmara.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_message WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<ChatMessage> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<ChatMessage> findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM chat_message WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatMessage> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM chat_message WHERE user_id = #{userId}")
    Long countByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM chat_message WHERE session_id = #{sessionId}")
    Long countBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM chat_message WHERE created_at >= #{startTime} ORDER BY created_at DESC")
    List<ChatMessage> findRecentMessages(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT SUM(tokens_used) FROM chat_message WHERE user_id = #{userId}")
    Long sumTokensByUserId(@Param("userId") Long userId);
}
