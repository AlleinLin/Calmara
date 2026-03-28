package com.calmara.common.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RedisSessionManager {

    private static final String SESSION_KEY_PREFIX = "calmara:session:";
    private static final String EMOTION_HISTORY_KEY_PREFIX = "calmara:emotion:";
    private static final String CHAT_HISTORY_KEY_PREFIX = "calmara:chat:";
    private static final String USER_SESSIONS_KEY_PREFIX = "calmara:user:sessions:";
    
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final Duration EMOTION_HISTORY_TTL = Duration.ofDays(30);
    private static final Duration CHAT_HISTORY_TTL = Duration.ofDays(90);
    private static final int MAX_EMOTION_HISTORY = 1000;
    private static final int MAX_CHAT_HISTORY = 100;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public RedisSessionManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public Session createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        Session session = Session.builder()
                .sessionId(sessionId)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .lastAccessedAt(LocalDateTime.now())
                .state(SessionState.ACTIVE)
                .metadata(new HashMap<>())
                .build();
        
        saveSession(session);
        addUserSession(userId, sessionId);
        
        log.info("创建会话: sessionId={}, userId={}", sessionId, userId);
        return session;
    }

    public Optional<Session> getSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        Object data = redisTemplate.opsForValue().get(key);
        
        if (data == null) {
            return Optional.empty();
        }
        
        try {
            Session session = objectMapper.readValue(data.toString(), Session.class);
            session.setLastAccessedAt(LocalDateTime.now());
            saveSession(session);
            return Optional.of(session);
        } catch (JsonProcessingException e) {
            log.error("解析会话失败: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    public void saveSession(Session session) {
        String key = SESSION_KEY_PREFIX + session.getSessionId();
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, json, SESSION_TTL.toSeconds(), TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("保存会话失败: sessionId={}", session.getSessionId(), e);
        }
    }

    public void invalidateSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.info("会话已失效: sessionId={}", sessionId);
    }

    public void addUserSession(String userId, String sessionId) {
        String key = USER_SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, SESSION_TTL.toSeconds(), TimeUnit.SECONDS);
    }

    public Set<String> getUserSessions(String userId) {
        String key = USER_SESSIONS_KEY_PREFIX + userId;
        Set<Object> sessions = redisTemplate.opsForSet().members(key);
        if (sessions == null) {
            return Collections.emptySet();
        }
        return sessions.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    public void addEmotionHistory(String sessionId, EmotionHistoryEntry entry) {
        String key = EMOTION_HISTORY_KEY_PREFIX + sessionId;
        
        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_EMOTION_HISTORY, -1);
            redisTemplate.expire(key, EMOTION_HISTORY_TTL.toSeconds(), TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("添加情绪历史失败", e);
        }
    }

    public List<EmotionHistoryEntry> getEmotionHistory(String sessionId) {
        String key = EMOTION_HISTORY_KEY_PREFIX + sessionId;
        List<Object> data = redisTemplate.opsForList().range(key, 0, -1);
        
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }
        
        return data.stream()
                .map(obj -> {
                    try {
                        return objectMapper.readValue(obj.toString(), EmotionHistoryEntry.class);
                    } catch (JsonProcessingException e) {
                        log.error("解析情绪历史失败", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Optional<EmotionHistoryEntry> getLatestEmotion(String sessionId) {
        String key = EMOTION_HISTORY_KEY_PREFIX + sessionId;
        Object data = redisTemplate.opsForList().index(key, -1);
        
        if (data == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(objectMapper.readValue(data.toString(), EmotionHistoryEntry.class));
        } catch (JsonProcessingException e) {
            log.error("解析最新情绪失败", e);
            return Optional.empty();
        }
    }

    public EmotionTrend analyzeEmotionTrend(String sessionId, int recentCount) {
        List<EmotionHistoryEntry> history = getEmotionHistory(sessionId);
        
        if (history.isEmpty()) {
            return EmotionTrend.builder()
                    .trend("STABLE")
                    .averageScore(0.0)
                    .changeRate(0.0)
                    .build();
        }
        
        int count = Math.min(recentCount, history.size());
        List<EmotionHistoryEntry> recent = history.subList(history.size() - count, history.size());
        
        double avgScore = recent.stream()
                .mapToDouble(EmotionHistoryEntry::getScore)
                .average()
                .orElse(0.0);
        
        double changeRate = 0.0;
        if (recent.size() >= 2) {
            double firstScore = recent.get(0).getScore();
            double lastScore = recent.get(recent.size() - 1).getScore();
            changeRate = (lastScore - firstScore) / Math.max(firstScore, 0.1);
        }
        
        String trend;
        if (changeRate > 0.2) {
            trend = "WORSENING";
        } else if (changeRate < -0.2) {
            trend = "IMPROVING";
        } else {
            trend = "STABLE";
        }
        
        return EmotionTrend.builder()
                .trend(trend)
                .averageScore(avgScore)
                .changeRate(changeRate)
                .dataPoints(count)
                .build();
    }

    public void addChatMessage(String sessionId, ChatMessage message) {
        String key = CHAT_HISTORY_KEY_PREFIX + sessionId;
        
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_CHAT_HISTORY, -1);
            redisTemplate.expire(key, CHAT_HISTORY_TTL.toSeconds(), TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("添加聊天消息失败", e);
        }
    }

    public List<ChatMessage> getChatHistory(String sessionId) {
        String key = CHAT_HISTORY_KEY_PREFIX + sessionId;
        List<Object> data = redisTemplate.opsForList().range(key, 0, -1);
        
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }
        
        return data.stream()
                .map(obj -> {
                    try {
                        return objectMapper.readValue(obj.toString(), ChatMessage.class);
                    } catch (JsonProcessingException e) {
                        log.error("解析聊天消息失败", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<ChatMessage> getRecentChatHistory(String sessionId, int count) {
        List<ChatMessage> history = getChatHistory(sessionId);
        if (history.size() <= count) {
            return history;
        }
        return history.subList(history.size() - count, history.size());
    }

    public void updateSessionMetadata(String sessionId, String key, Object value) {
        Optional<Session> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            session.getMetadata().put(key, value);
            saveSession(session);
        }
    }

    public Optional<Object> getSessionMetadata(String sessionId, String key) {
        return getSession(sessionId)
                .map(s -> s.getMetadata().get(key));
    }

    public void clearSessionData(String sessionId) {
        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        redisTemplate.delete(EMOTION_HISTORY_KEY_PREFIX + sessionId);
        redisTemplate.delete(CHAT_HISTORY_KEY_PREFIX + sessionId);
        log.info("清除会话数据: sessionId={}", sessionId);
    }

    public SessionStatistics getSessionStatistics(String sessionId) {
        List<EmotionHistoryEntry> emotions = getEmotionHistory(sessionId);
        List<ChatMessage> chats = getChatHistory(sessionId);
        
        Optional<Session> sessionOpt = getSession(sessionId);
        long durationMinutes = 0;
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            durationMinutes = java.time.Duration.between(
                    session.getCreatedAt(), 
                    LocalDateTime.now()
            ).toMinutes();
        }
        
        double avgEmotionScore = emotions.stream()
                .mapToDouble(EmotionHistoryEntry::getScore)
                .average()
                .orElse(0.0);
        
        Map<String, Long> emotionDistribution = emotions.stream()
                .collect(Collectors.groupingBy(
                        EmotionHistoryEntry::getLabel,
                        Collectors.counting()
                ));
        
        return SessionStatistics.builder()
                .sessionId(sessionId)
                .totalMessages(chats.size())
                .totalEmotionRecords(emotions.size())
                .averageEmotionScore(avgEmotionScore)
                .emotionDistribution(emotionDistribution)
                .sessionDurationMinutes(durationMinutes)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Session implements Serializable {
        private String sessionId;
        private String userId;
        private LocalDateTime createdAt;
        private LocalDateTime lastAccessedAt;
        private SessionState state;
        private Map<String, Object> metadata;
        
        @JsonIgnore
        public boolean isActive() {
            return state == SessionState.ACTIVE;
        }
        
        @JsonIgnore
        public long getDurationMinutes() {
            return java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes();
        }
    }

    public enum SessionState {
        ACTIVE, IDLE, CLOSED, EXPIRED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionHistoryEntry implements Serializable {
        private String label;
        private double score;
        private double confidence;
        private String source;
        private Map<String, Double> features;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime timestamp;
        
        public static EmotionHistoryEntry of(String label, double score, double confidence, 
                                              String source, Map<String, Double> features) {
            return EmotionHistoryEntry.builder()
                    .label(label)
                    .score(score)
                    .confidence(confidence)
                    .source(source != null ? source : "unknown")
                    .features(features)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage implements Serializable {
        private String role;
        private String content;
        private String intent;
        private String emotion;
        private Double emotionScore;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime timestamp;
        
        public static ChatMessage user(String content) {
            return ChatMessage.builder()
                    .role("user")
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
        
        public static ChatMessage assistant(String content) {
            return ChatMessage.builder()
                    .role("assistant")
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionTrend implements Serializable {
        private String trend;
        private double averageScore;
        private double changeRate;
        private int dataPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionStatistics implements Serializable {
        private String sessionId;
        private int totalMessages;
        private int totalEmotionRecords;
        private double averageEmotionScore;
        private Map<String, Long> emotionDistribution;
        private long sessionDurationMinutes;
    }
}
