package com.calmara.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calmara.common.Result;
import com.calmara.model.entity.AlertRecord;
import com.calmara.model.entity.ChatMessage;
import com.calmara.model.entity.EmotionRecord;
import com.calmara.model.entity.User;
import com.calmara.model.mapper.AlertRecordMapper;
import com.calmara.model.mapper.ChatMessageMapper;
import com.calmara.model.mapper.EmotionRecordMapper;
import com.calmara.model.mapper.UserMapper;
import com.calmara.security.service.AuthService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserMapper userMapper;
    private final EmotionRecordMapper emotionRecordMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AuthService authService;

    public AdminController(UserMapper userMapper,
                           EmotionRecordMapper emotionRecordMapper,
                           AlertRecordMapper alertRecordMapper,
                           ChatMessageMapper chatMessageMapper,
                           AuthService authService) {
        this.userMapper = userMapper;
        this.emotionRecordMapper = emotionRecordMapper;
        this.alertRecordMapper = alertRecordMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.authService = authService;
    }

    @GetMapping("/dashboard")
    public Result<DashboardStats> getDashboardStats() {
        log.info("获取仪表盘统计数据");

        long totalUsers = userMapper.countUsers();

        long highRiskCount = emotionRecordMapper.countByRiskLevel("HIGH");
        long mediumRiskCount = emotionRecordMapper.countByRiskLevel("MEDIUM");

        long pendingAlerts = alertRecordMapper.countByStatus("PENDING");

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Map<String, Object>> emotionStats = emotionRecordMapper.countByEmotion(weekAgo);

        Map<String, Long> emotionDistribution = new HashMap<>();
        emotionDistribution.put("正常", 0L);
        emotionDistribution.put("焦虑", 0L);
        emotionDistribution.put("低落", 0L);
        emotionDistribution.put("高风险", 0L);

        for (Map<String, Object> stat : emotionStats) {
            String emotion = (String) stat.get("emotion");
            Long count = ((Number) stat.get("count")).longValue();
            emotionDistribution.put(emotion, count);
        }

        DashboardStats stats = new DashboardStats();
        stats.setTotalUsers(totalUsers);
        stats.setHighRiskCount(highRiskCount);
        stats.setMediumRiskCount(mediumRiskCount);
        stats.setPendingAlerts(pendingAlerts);
        stats.setEmotionDistribution(emotionDistribution);
        stats.setTodayNewUsers(userMapper.countTodayNewUsers());
        stats.setHighRiskUserCount(emotionRecordMapper.countHighRiskUsers());

        return Result.success(stats);
    }

    @GetMapping("/students")
    public Result<IPage<User>> getStudents(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String keyword) {

        log.info("获取学生列表: page={}, size={}, riskLevel={}, keyword={}", page, size, riskLevel, keyword);

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("role", "USER").eq("status", 1);

        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.and(w -> w
                    .like("username", keyword)
                    .or().like("email", keyword)
                    .or().like("real_name", keyword)
                    .or().like("student_id", keyword));
        }

        if (riskLevel != null && !riskLevel.isEmpty()) {
            List<Long> highRiskUserIds = emotionRecordMapper.findByRiskLevel(riskLevel)
                    .stream()
                    .map(EmotionRecord::getUserId)
                    .distinct()
                    .collect(Collectors.toList());

            if (!highRiskUserIds.isEmpty()) {
                queryWrapper.in("id", highRiskUserIds);
            } else {
                return Result.success(new Page<>(page, size));
            }
        }

        queryWrapper.orderByDesc("created_at");

        IPage<User> result = userMapper.selectPage(new Page<>(page, size), queryWrapper);
        return Result.success(result);
    }

    @GetMapping("/students/{studentId}")
    public Result<UserHistoryResponse> getStudentDetail(@PathVariable Long studentId) {
        log.info("获取学生详情: studentId={}", studentId);

        User user = userMapper.selectById(studentId);
        if (user == null) {
            return Result.error(404, "学生不存在");
        }

        List<EmotionRecord> emotions = emotionRecordMapper.findByUserId(studentId);

        List<ChatMessage> messages = chatMessageMapper.findRecentByUserId(studentId, 20);

        UserHistoryResponse response = new UserHistoryResponse();
        response.setUser(user);
        response.setEmotionRecords(emotions);
        response.setRecentMessages(messages);

        return Result.success(response);
    }

    @PutMapping("/students/{studentId}/status")
    public Result<Boolean> updateStudentStatus(
            @PathVariable Long studentId,
            @RequestBody StatusRequest request) {

        log.info("更新学生状态: studentId={}, status={}", studentId, request.getStatus());

        boolean success = authService.updateUserStatus(studentId, request.getStatus());
        return Result.success(success);
    }

    @GetMapping("/alerts")
    public Result<IPage<AlertRecord>> getAlerts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel) {

        log.info("获取预警列表: page={}, size={}, status={}, riskLevel={}", page, size, status, riskLevel);

        QueryWrapper<AlertRecord> queryWrapper = new QueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }

        if (riskLevel != null && !riskLevel.isEmpty()) {
            queryWrapper.eq("risk_level", riskLevel);
        }

        queryWrapper.orderByDesc("created_at");

        IPage<AlertRecord> result = alertRecordMapper.selectPage(new Page<>(page, size), queryWrapper);
        return Result.success(result);
    }

    @PutMapping("/alerts/{alertId}/status")
    public Result<Boolean> updateAlertStatus(
            @PathVariable Long alertId,
            @RequestBody AlertStatusRequest request,
            Authentication authentication) {

        log.info("更新预警状态: alertId={}, status={}", alertId, request.getStatus());

        AlertRecord alert = alertRecordMapper.selectById(alertId);
        if (alert == null) {
            return Result.error(404, "预警记录不存在");
        }

        Long handlerId = null;
        String handlerName = null;

        if (authentication != null) {
            User handler = authService.getUserByUsername(authentication.getName());
            if (handler != null) {
                handlerId = handler.getId();
                handlerName = handler.getRealName() != null ? handler.getRealName() : handler.getUsername();
            }
        }

        int updated = alertRecordMapper.updateStatus(alertId, request.getStatus(),
                handlerId, handlerName, request.getHandleNote());

        return Result.success(updated > 0);
    }

    @GetMapping("/alerts/{alertId}")
    public Result<AlertRecord> getAlertDetail(@PathVariable Long alertId) {
        log.info("获取预警详情: alertId={}", alertId);

        AlertRecord alert = alertRecordMapper.selectById(alertId);
        if (alert == null) {
            return Result.error(404, "预警记录不存在");
        }

        return Result.success(alert);
    }

    @GetMapping("/emotion-statistics")
    public Result<EmotionStatistics> getEmotionStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime endDate) {

        log.info("获取情绪统计数据: startDate={}, endDate={}", startDate, endDate);

        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        List<Map<String, Object>> emotionStats = emotionRecordMapper.countByEmotion(startDate);
        List<Map<String, Object>> riskStats = emotionRecordMapper.countByRiskLevelAfter(startDate);

        Map<String, Long> dailyEmotions = new HashMap<>();
        for (Map<String, Object> stat : emotionStats) {
            String emotion = (String) stat.get("emotion");
            Long count = ((Number) stat.get("count")).longValue();
            dailyEmotions.put(emotion, count);
        }

        Map<String, Long> riskDistribution = new HashMap<>();
        for (Map<String, Object> stat : riskStats) {
            String riskLevel = (String) stat.get("risk_level");
            Long count = ((Number) stat.get("count")).longValue();
            riskDistribution.put(riskLevel, count);
        }

        EmotionStatistics stats = new EmotionStatistics();
        stats.setDailyEmotions(dailyEmotions);
        stats.setRiskDistribution(riskDistribution);
        stats.setStartDate(startDate);
        stats.setEndDate(endDate);

        return Result.success(stats);
    }

    @GetMapping("/sessions/{sessionId}")
    public Result<SessionDetail> getSessionDetail(@PathVariable String sessionId) {
        log.info("获取会话详情: sessionId={}", sessionId);

        List<ChatMessage> messages = chatMessageMapper.findBySessionId(sessionId);

        List<EmotionRecord> emotions = emotionRecordMapper.findBySessionId(sessionId);

        SessionDetail detail = new SessionDetail();
        detail.setSessionId(sessionId);
        detail.setMessages(messages);
        detail.setEmotionRecords(emotions);

        return Result.success(detail);
    }

    @GetMapping("/high-risk-users")
    public Result<List<User>> getHighRiskUsers() {
        log.info("获取高风险用户列表");

        List<User> highRiskUsers = userMapper.findHighRiskUsers();
        return Result.success(highRiskUsers);
    }

    @GetMapping("/recent-alerts")
    public Result<List<AlertRecord>> getRecentAlerts(
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("获取最近预警: limit={}", limit);

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<AlertRecord> alerts = alertRecordMapper.findRecentAlerts(since);

        if (alerts.size() > limit) {
            alerts = alerts.subList(0, limit);
        }

        return Result.success(alerts);
    }

    @Data
    public static class DashboardStats {
        private Long totalUsers;
        private Long highRiskCount;
        private Long mediumRiskCount;
        private Long pendingAlerts;
        private Map<String, Long> emotionDistribution;
        private Long todayNewUsers;
        private Long highRiskUserCount;
    }

    @Data
    public static class UserHistoryResponse {
        private User user;
        private List<EmotionRecord> emotionRecords;
        private List<ChatMessage> recentMessages;
    }

    @Data
    public static class AlertStatusRequest {
        private String status;
        private String handleNote;
    }

    @Data
    public static class StatusRequest {
        private Integer status;
    }

    @Data
    public static class EmotionStatistics {
        private Map<String, Long> dailyEmotions;
        private Map<String, Long> riskDistribution;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    @Data
    public static class SessionDetail {
        private String sessionId;
        private List<ChatMessage> messages;
        private List<EmotionRecord> emotionRecords;
    }
}
