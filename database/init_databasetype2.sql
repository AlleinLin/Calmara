-- Calmara 心理健康智能体系统数据库初始化脚本
-- 创建时间: 2026-03-28
-- 数据库: MySQL 8.0+

-- 创建数据库
CREATE DATABASE IF NOT EXISTS calmara DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE calmara;

-- =====================================================
-- 用户表
-- =====================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `student_id` VARCHAR(50) DEFAULT NULL COMMENT '学号(学生用户)',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER, ADMIN, COUNSELOR',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_username` (`username`),
    INDEX `idx_email` (`email`),
    INDEX `idx_role` (`role`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =====================================================
-- 角色权限表
-- =====================================================
CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    `role_key` VARCHAR(50) NOT NULL UNIQUE COMMENT '角色标识',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- =====================================================
-- 权限表
-- =====================================================
CREATE TABLE IF NOT EXISTS `permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
    `permission_name` VARCHAR(100) NOT NULL UNIQUE COMMENT '权限名称',
    `permission_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '权限标识',
    `resource_type` VARCHAR(50) NOT NULL COMMENT '资源类型: MENU, BUTTON, API',
    `resource_path` VARCHAR(255) NOT NULL COMMENT '资源路径',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '权限描述',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- =====================================================
-- 角色权限关联表
-- =====================================================
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    FOREIGN KEY (`role_id`) REFERENCES `role`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`permission_id`) REFERENCES `permission`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- =====================================================
-- 管理员邮箱表
-- =====================================================
CREATE TABLE IF NOT EXISTS `admin_email` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱地址',
    `name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `department` VARCHAR(100) DEFAULT NULL COMMENT '部门',
    `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用: 0-否, 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_email` (`email`),
    INDEX `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员邮箱表';

-- =====================================================
-- 情绪记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `emotion_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(50) NOT NULL COMMENT '会话ID',
    `text_emotion` VARCHAR(20) DEFAULT NULL COMMENT '文本情绪标签',
    `text_score` DECIMAL(3,2) DEFAULT NULL COMMENT '文本情绪分数',
    `audio_emotion` VARCHAR(20) DEFAULT NULL COMMENT '语音情绪标签',
    `audio_score` DECIMAL(3,2) DEFAULT NULL COMMENT '语音情绪分数',
    `visual_emotion` VARCHAR(20) DEFAULT NULL COMMENT '视觉情绪标签',
    `visual_score` DECIMAL(3,2) DEFAULT NULL COMMENT '视觉情绪分数',
    `fusion_emotion` VARCHAR(20) NOT NULL COMMENT '融合情绪标签',
    `fusion_score` DECIMAL(3,2) NOT NULL COMMENT '融合情绪分数',
    `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级: LOW, MEDIUM, HIGH',
    `input_types` VARCHAR(100) DEFAULT NULL COMMENT '输入类型: text,audio,visual',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_risk_level` (`risk_level`),
    INDEX `idx_created_at` (`created_at`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪记录表';

-- =====================================================
-- 聊天消息表
-- =====================================================
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(50) NOT NULL COMMENT '会话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色: user, assistant',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `emotion_label` VARCHAR(20) DEFAULT NULL COMMENT '情绪标签',
    `intent_type` VARCHAR(20) DEFAULT NULL COMMENT '意图类型',
    `model_used` VARCHAR(50) DEFAULT NULL COMMENT '使用的模型',
    `tokens_used` INT DEFAULT 0 COMMENT '使用的Token数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_created_at` (`created_at`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- =====================================================
-- 预警记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `alert_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '预警ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(50) DEFAULT NULL COMMENT '会话ID',
    `emotion_record_id` BIGINT DEFAULT NULL COMMENT '情绪记录ID',
    `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级',
    `content` TEXT COMMENT '预警内容',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING, PROCESSING, RESOLVED, CLOSED',
    `handler_id` BIGINT DEFAULT NULL COMMENT '处理人ID',
    `handler_name` VARCHAR(50) DEFAULT NULL COMMENT '处理人姓名',
    `handle_note` TEXT DEFAULT NULL COMMENT '处理备注',
    `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间',
    `email_sent` TINYINT DEFAULT 0 COMMENT '是否已发送邮件: 0-否, 1-是',
    `email_sent_at` DATETIME DEFAULT NULL COMMENT '邮件发送时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_risk_level` (`risk_level`),
    INDEX `idx_created_at` (`created_at`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`handler_id`) REFERENCES `user`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警记录表';

-- =====================================================
-- 知识文档表
-- =====================================================
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    `title` VARCHAR(255) NOT NULL COMMENT '文档标题',
    `content` TEXT NOT NULL COMMENT '文档内容',
    `category` VARCHAR(50) DEFAULT NULL COMMENT '分类',
    `tags` VARCHAR(255) DEFAULT NULL COMMENT '标签(JSON数组)',
    `source` VARCHAR(100) DEFAULT NULL COMMENT '来源',
    `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    `view_count` INT DEFAULT 0 COMMENT '查看次数',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_category` (`category`),
    INDEX `idx_is_active` (`is_active`),
    FULLTEXT INDEX `ft_content` (`title`, `content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档表';

-- =====================================================
-- 用户会话表
-- =====================================================
CREATE TABLE IF NOT EXISTS `user_session` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    `session_id` VARCHAR(50) NOT NULL UNIQUE COMMENT '会话标识',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `message_count` INT DEFAULT 0 COMMENT '消息数量',
    `avg_emotion_score` DECIMAL(3,2) DEFAULT NULL COMMENT '平均情绪分数',
    `max_risk_level` VARCHAR(20) DEFAULT NULL COMMENT '最高风险等级',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_start_time` (`start_time`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话表';

-- =====================================================
-- 系统配置表
-- =====================================================
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `config_type` VARCHAR(50) DEFAULT 'STRING' COMMENT '配置类型: STRING, NUMBER, BOOLEAN, JSON',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '配置描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- =====================================================
-- 操作日志表
-- =====================================================
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    `operation` VARCHAR(100) NOT NULL COMMENT '操作类型',
    `method` VARCHAR(200) DEFAULT NULL COMMENT '请求方法',
    `params` TEXT DEFAULT NULL COMMENT '请求参数',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    `user_agent` VARCHAR(255) DEFAULT NULL COMMENT '用户代理',
    `result` TINYINT DEFAULT 1 COMMENT '操作结果: 0-失败, 1-成功',
    `error_msg` TEXT DEFAULT NULL COMMENT '错误信息',
    `duration` INT DEFAULT 0 COMMENT '执行时长(ms)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_operation` (`operation`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- =====================================================
-- 初始化角色数据
-- =====================================================
INSERT INTO `role` (`role_name`, `role_key`, `description`) VALUES
('学生用户', 'USER', '普通学生用户，可以使用心理咨询功能'),
('系统管理员', 'ADMIN', '系统管理员，拥有所有权限'),
('心理咨询师', 'COUNSELOR', '心理咨询师，可以查看和处理学生心理数据');

-- =====================================================
-- 初始化权限数据
-- =====================================================
INSERT INTO `permission` (`permission_name`, `permission_key`, `resource_type`, `resource_path`, `description`, `parent_id`) VALUES
-- 用户管理
('用户管理', 'user:manage', 'MENU', '/admin/users', '用户管理菜单', 0),
('查看用户列表', 'user:list', 'BUTTON', '/api/admin/users', '查看用户列表', 1),
('查看用户详情', 'user:detail', 'BUTTON', '/api/admin/users/*', '查看用户详情', 1),
('禁用/启用用户', 'user:status', 'BUTTON', '/api/admin/users/*/status', '禁用或启用用户', 1),
-- 预警管理
('预警管理', 'alert:manage', 'MENU', '/admin/alerts', '预警管理菜单', 0),
('查看预警列表', 'alert:list', 'BUTTON', '/api/admin/alerts', '查看预警列表', 5),
('处理预警', 'alert:handle', 'BUTTON', '/api/admin/alerts/*', '处理预警', 5),
('预警统计', 'alert:stats', 'BUTTON', '/api/admin/alerts/statistics', '预警统计', 5),
-- 数据统计
('数据统计', 'statistics:manage', 'MENU', '/admin/statistics', '数据统计菜单', 0),
('情绪统计', 'statistics:emotion', 'BUTTON', '/api/admin/emotion-statistics', '情绪统计', 9),
('仪表盘数据', 'statistics:dashboard', 'BUTTON', '/api/admin/dashboard', '仪表盘数据', 9),
-- 知识库管理
('知识库管理', 'knowledge:manage', 'MENU', '/admin/knowledge', '知识库管理菜单', 0),
('查看知识库', 'knowledge:list', 'BUTTON', '/api/admin/knowledge', '查看知识库列表', 13),
('添加知识', 'knowledge:add', 'BUTTON', '/api/admin/knowledge', '添加知识文档', 13),
('编辑知识', 'knowledge:edit', 'BUTTON', '/api/admin/knowledge/*', '编辑知识文档', 13),
('删除知识', 'knowledge:delete', 'BUTTON', '/api/admin/knowledge/*', '删除知识文档', 13),
-- 系统设置
('系统设置', 'system:manage', 'MENU', '/admin/settings', '系统设置菜单', 0),
('管理员邮箱配置', 'system:email', 'BUTTON', '/api/admin/emails', '管理员邮箱配置', 18),
('系统配置', 'system:config', 'BUTTON', '/api/admin/config', '系统配置', 18),
-- 心理咨询
('心理咨询', 'consultation:use', 'MENU', '/chat', '心理咨询功能', 0),
('发起咨询', 'consultation:chat', 'BUTTON', '/api/chat/stream', '发起心理咨询', 21),
('查看历史记录', 'consultation:history', 'BUTTON', '/api/chat/history', '查看历史记录', 21);

-- =====================================================
-- 初始化角色权限关联
-- =====================================================
-- ADMIN角色拥有所有权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `permission`;

-- COUNSELOR角色权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(3, 5), (3, 6), (3, 7), (3, 8),
(3, 9), (3, 10), (3, 11),
(3, 13), (3, 14);

-- USER角色权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(1, 21), (1, 22), (1, 23);

-- =====================================================
-- 初始化默认管理员账户
-- 密码为 admin123 (BCrypt加密后的值)
-- =====================================================
INSERT INTO `user` (`username`, `password`, `email`, `real_name`, `role`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin@calmara.edu', '系统管理员', 'ADMIN', 1);

-- =====================================================
-- 初始化默认管理员邮箱
-- =====================================================
INSERT INTO `admin_email` (`email`, `name`, `department`, `is_active`) VALUES
('counselor@calmara.edu', '心理咨询中心', '学生工作处', 1),
('alert@calmara.edu', '预警通知邮箱', '系统通知', 1);

-- =====================================================
-- 初始化系统配置
-- =====================================================
INSERT INTO `system_config` (`config_key`, `config_value`, `config_type`, `description`) VALUES
('risk.threshold.low', '1.0', 'NUMBER', '低风险阈值'),
('risk.threshold.high', '2.0', 'NUMBER', '高风险阈值'),
('risk.weight.text', '0.1', 'NUMBER', '文本情绪权重'),
('risk.weight.audio', '0.4', 'NUMBER', '语音情绪权重'),
('risk.weight.visual', '0.5', 'NUMBER', '视觉情绪权重'),
('email.alert.enabled', 'true', 'BOOLEAN', '是否启用邮件预警'),
('email.alert.recipients', 'counselor@calmara.edu', 'STRING', '预警邮件接收者'),
('chat.max.history', '100', 'NUMBER', '聊天历史最大保存条数'),
('session.timeout.minutes', '30', 'NUMBER', '会话超时时间(分钟)'),
('embedding.provider', 'local', 'STRING', '向量嵌入提供者: local, openai, ollama'),
('model.default', 'qwen2.5:7b-chat', 'STRING', '默认大模型'),
('rag.max.retries', '3', 'NUMBER', 'RAG最大重试次数'),
('rag.similarity.threshold', '0.3', 'NUMBER', '相似度阈值');

-- =====================================================
-- 创建视图：用户情绪概览
-- =====================================================
CREATE OR REPLACE VIEW `v_user_emotion_overview` AS
SELECT 
    u.id AS user_id,
    u.username,
    u.email,
    COUNT(DISTINCT er.id) AS total_sessions,
    AVG(er.fusion_score) AS avg_emotion_score,
    MAX(er.fusion_score) AS max_emotion_score,
    SUM(CASE WHEN er.risk_level = 'HIGH' THEN 1 ELSE 0 END) AS high_risk_count,
    SUM(CASE WHEN er.risk_level = 'MEDIUM' THEN 1 ELSE 0 END) AS medium_risk_count,
    MAX(er.created_at) AS last_emotion_time
FROM `user` u
LEFT JOIN `emotion_record` er ON u.id = er.user_id
WHERE u.role = 'USER' AND u.status = 1
GROUP BY u.id, u.username, u.email;

-- =====================================================
-- 创建视图：预警统计
-- =====================================================
CREATE OR REPLACE VIEW `v_alert_statistics` AS
SELECT 
    DATE(created_at) AS alert_date,
    risk_level,
    status,
    COUNT(*) AS alert_count
FROM `alert_record`
GROUP BY DATE(created_at), risk_level, status;

-- =====================================================
-- 创建存储过程：清理过期会话
-- =====================================================
DELIMITER //
CREATE PROCEDURE `sp_clean_expired_sessions`(IN timeout_minutes INT)
BEGIN
    DECLARE exit_handler BOOLEAN DEFAULT FALSE;
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET exit_handler = TRUE;
    
    UPDATE `user_session` 
    SET `end_time` = NOW()
    WHERE `end_time` IS NULL 
    AND TIMESTAMPDIFF(MINUTE, `start_time`, NOW()) > timeout_minutes;
    
    IF exit_handler THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '清理过期会话失败';
    END IF;
END //
DELIMITER ;

-- =====================================================
-- 创建触发器：自动创建预警记录
-- =====================================================
DELIMITER //
CREATE TRIGGER `tr_create_alert_on_high_risk`
AFTER INSERT ON `emotion_record`
FOR EACH ROW
BEGIN
    IF NEW.risk_level = 'HIGH' THEN
        INSERT INTO `alert_record` (`user_id`, `session_id`, `emotion_record_id`, `risk_level`, `content`, `status`)
        VALUES (NEW.user_id, NEW.session_id, NEW.id, NEW.risk_level, 
                CONCAT('检测到高风险情绪状态，情绪标签: ', NEW.fusion_emotion, ', 分数: ', NEW.fusion_score),
                'PENDING');
    END IF;
END //
DELIMITER ;

-- 数据库初始化完成
SELECT '数据库初始化完成!' AS message;
