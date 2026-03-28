package com.calmara.mcp.email;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.model.dto.RiskAlert;
import com.calmara.model.dto.SendResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class EmailMCPService {

    private final JavaMailSender mailSender;

    @Value("${calmara.mcp.email.from:alert@calmara.edu}")
    private String fromEmail;

    @Value("${calmara.mcp.email.enabled:true}")
    private boolean enabled;

    private static final int MAX_RETRY = 3;

    private static final String DEFAULT_ALERT_TEMPLATE = """
            【高危心理预警】学生用户 %s 存在自伤风险

            系统在对话中监测到1名学生出现高风险心理状态，请及时关注并干预。

            【预警信息如下】
            用户ID：%s
            对话内容：%s
            情绪判定：%s
            综合情绪得分：%.2f
            风险等级：%s
            对话时间：%s

            系统已自动存档对话记录至 Excel，请尽快处理。
            """;

    public EmailMCPService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public SendResult sendAlert(RiskAlert alert) {
        if (!enabled) {
            log.info("邮件发送已禁用，跳过预警邮件");
            return SendResult.success("邮件发送已禁用");
        }

        if (alert.getRecipients() == null || alert.getRecipients().isEmpty()) {
            log.warn("没有配置收件人");
            return SendResult.failure("没有配置收件人");
        }

        String subject = String.format("【高危心理预警】学生用户 %s 存在自伤风险", alert.getUserId());
        String content = String.format(DEFAULT_ALERT_TEMPLATE,
                alert.getUserId(),
                alert.getUserId(),
                alert.getContent() != null ? alert.getContent() : "未提供",
                alert.getEmotion() != null ? alert.getEmotion() : "未知",
                alert.getEmotionScore() != null ? alert.getEmotionScore() : 0.0,
                alert.getRiskLevel() != null ? alert.getRiskLevel().name() : "未知",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        int successCount = 0;
        int failCount = 0;

        for (String recipient : alert.getRecipients()) {
            boolean sent = sendWithRetry(recipient, subject, content);
            if (sent) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("预警邮件发送完成: 成功={}, 失败={}", successCount, failCount);

        if (successCount > 0) {
            return SendResult.success(String.format("成功发送%d封，失败%d封", successCount, failCount));
        } else {
            return SendResult.failure("所有邮件发送失败");
        }
    }

    private boolean sendWithRetry(String to, String subject, String content) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(content);

                mailSender.send(message);
                log.info("邮件发送成功: to={}", to);
                return true;

            } catch (Exception e) {
                log.warn("邮件发送失败，第{}次重试: to={}, error={}", i + 1, to, e.getMessage());

                if (i < MAX_RETRY - 1) {
                    try {
                        Thread.sleep(1000L * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        log.error("邮件发送最终失败: to={}", to);
        return false;
    }

    public SendResult sendRevocationNotice(List<String> recipients, String subject, String content) {
        if (!enabled) {
            log.info("邮件发送已禁用，跳过撤销通知");
            return SendResult.success("邮件发送已禁用");
        }

        if (recipients == null || recipients.isEmpty()) {
            log.warn("没有配置收件人");
            return SendResult.failure("没有配置收件人");
        }

        int successCount = 0;
        int failCount = 0;

        for (String recipient : recipients) {
            boolean sent = sendWithRetry(recipient, subject, content);
            if (sent) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("撤销通知发送完成: 成功={}, 失败={}", successCount, failCount);

        if (successCount > 0) {
            return SendResult.success(String.format("成功发送%d封，失败%d封", successCount, failCount));
        } else {
            return SendResult.failure("所有邮件发送失败");
        }
    }

    @Data
    public static class AdminEmailInfo {
        private Long id;
        private String email;
        private String name;
        private String department;
        private Boolean isActive;
    }
}
