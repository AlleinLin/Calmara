package com.calmara.install.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class InstallService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${calmara.install.lock-file:./.install.lock}")
    private String lockFilePath;

    @Value("${calmara.install.script-path:classpath:db/migration/}")
    private String scriptPath;

    @Value("${calmara.install.auto-migrate:true}")
    private boolean autoMigrate;

    private static final String VERSION = "1.0.0";
    private static final List<String> REQUIRED_TABLES = Arrays.asList(
            "user", "role", "permission", "role_permission",
            "emotion_record", "chat_message", "alert_record",
            "knowledge_document", "admin_email"
    );

    public InstallService(DataSource dataSource, JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public InstallStatus checkInstallStatus() {
        InstallStatus status = new InstallStatus();
        status.setCheckTime(LocalDateTime.now());

        if (isLockFileExists()) {
            status.setInstalled(true);
            status.setVersion(readInstalledVersion());
            status.setMessage("系统已安装");
            return status;
        }

        boolean dbConnected = testDatabaseConnection();
        status.setDatabaseConnected(dbConnected);

        if (!dbConnected) {
            status.setInstalled(false);
            status.setMessage("数据库连接失败");
            return status;
        }

        List<String> existingTables = getExistingTables();
        List<String> missingTables = new ArrayList<>();
        for (String table : REQUIRED_TABLES) {
            if (!existingTables.contains(table)) {
                missingTables.add(table);
            }
        }

        status.setExistingTables(existingTables);
        status.setMissingTables(missingTables);
        status.setInstalled(missingTables.isEmpty() && hasAdminUser());

        if (status.isInstalled()) {
            createLockFile();
            status.setVersion(VERSION);
            status.setMessage("系统已安装并检测到锁定文件");
        } else if (!missingTables.isEmpty()) {
            status.setMessage("数据库表不完整，缺少: " + String.join(", ", missingTables));
        } else {
            status.setMessage("数据库表已创建，但缺少管理员账户");
        }

        return status;
    }

    public boolean testDatabaseConnection() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("数据库连接测试失败", e);
            return false;
        }
    }

    public DatabaseInfo getDatabaseInfo() {
        DatabaseInfo info = new DatabaseInfo();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            info.setDatabaseProductName(metaData.getDatabaseProductName());
            info.setDatabaseProductVersion(metaData.getDatabaseProductVersion());
            info.setDriverName(metaData.getDriverName());
            info.setDriverVersion(metaData.getDriverVersion());
            info.setUrl(metaData.getURL());
            info.setUsername(metaData.getUserName());
            info.setConnected(true);
        } catch (SQLException e) {
            log.error("获取数据库信息失败", e);
            info.setConnected(false);
            info.setErrorMessage(e.getMessage());
        }
        return info;
    }

    public InstallResult install(InstallRequest request) {
        InstallResult result = new InstallResult();
        result.setStartTime(LocalDateTime.now());

        try {
            log.info("开始系统安装...");

            if (isLockFileExists()) {
                result.setSuccess(false);
                result.setErrorMessage("系统已安装，如需重新安装请删除锁定文件");
                return result;
            }

            if (!testDatabaseConnection()) {
                result.setSuccess(false);
                result.setErrorMessage("数据库连接失败，请检查配置");
                return result;
            }

            List<String> existingTables = getExistingTables();
            if (existingTables.isEmpty()) {
                log.info("执行数据库初始化脚本...");
                executeInitScript();
                result.setDatabaseInitialized(true);
            } else {
                log.info("数据库表已存在，跳过初始化");
                result.setDatabaseInitialized(false);
            }

            if (StringUtils.hasText(request.getAdminUsername())) {
                log.info("创建管理员账户...");
                createAdminUser(request);
                result.setAdminCreated(true);
            }

            if (StringUtils.hasText(request.getAdminEmail())) {
                log.info("配置管理员邮箱...");
                configureAdminEmail(request.getAdminEmail());
                result.setEmailConfigured(true);
            }

            createLockFile();
            writeInstallLog(request);

            result.setSuccess(true);
            result.setVersion(VERSION);
            result.setMessage("系统安装成功！");
            result.setEndTime(LocalDateTime.now());

            log.info("系统安装完成！");

        } catch (Exception e) {
            log.error("系统安装失败", e);
            result.setSuccess(false);
            result.setErrorMessage("安装失败: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }

        return result;
    }

    private void executeInitScript() {
        try {
            String sql = loadInitScript();
            String[] statements = sql.split(";");

            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    try {
                        jdbcTemplate.execute(trimmed);
                    } catch (Exception e) {
                        if (!trimmed.toUpperCase().contains("CREATE DATABASE") &&
                            !trimmed.toUpperCase().contains("USE ")) {
                            log.warn("执行SQL语句失败: {}", trimmed.substring(0, Math.min(50, trimmed.length())));
                        }
                    }
                }
            }

            log.info("数据库初始化脚本执行完成");

        } catch (Exception e) {
            log.error("执行初始化脚本失败", e);
            throw new RuntimeException("数据库初始化失败: " + e.getMessage(), e);
        }
    }

    private String loadInitScript() {
        try {
            Path path = Paths.get("database/init_database.sql");
            if (Files.exists(path)) {
                return Files.readString(path);
            }

            try (var is = getClass().getClassLoader().getResourceAsStream("db/init_database.sql")) {
                if (is != null) {
                    return new String(is.readAllBytes());
                }
            }

            return generateDefaultScript();

        } catch (Exception e) {
            log.warn("加载初始化脚本失败，使用默认脚本: {}", e.getMessage());
            return generateDefaultScript();
        }
    }

    private String generateDefaultScript() {
        return """
            -- 用户表
            CREATE TABLE IF NOT EXISTS `user` (
                `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                `username` VARCHAR(50) NOT NULL UNIQUE,
                `password` VARCHAR(255) NOT NULL,
                `email` VARCHAR(100) NOT NULL UNIQUE,
                `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
                `status` TINYINT NOT NULL DEFAULT 1,
                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

            -- 角色表
            CREATE TABLE IF NOT EXISTS `role` (
                `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                `role_name` VARCHAR(50) NOT NULL,
                `role_key` VARCHAR(50) NOT NULL UNIQUE,
                `description` VARCHAR(255),
                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

            -- 权限表
            CREATE TABLE IF NOT EXISTS `permission` (
                `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                `permission_name` VARCHAR(100) NOT NULL,
                `permission_key` VARCHAR(100) NOT NULL UNIQUE,
                `resource_type` VARCHAR(50) NOT NULL,
                `resource_path` VARCHAR(255) NOT NULL,
                `parent_id` BIGINT DEFAULT 0,
                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

            -- 情绪记录表
            CREATE TABLE IF NOT EXISTS `emotion_record` (
                `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                `user_id` BIGINT NOT NULL,
                `session_id` VARCHAR(50),
                `fusion_emotion` VARCHAR(20) NOT NULL,
                `fusion_score` DECIMAL(3,2) NOT NULL,
                `risk_level` VARCHAR(20) NOT NULL,
                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

            -- 聊天消息表
            CREATE TABLE IF NOT EXISTS `chat_message` (
                `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                `user_id` BIGINT NOT NULL,
                `session_id` VARCHAR(50) NOT NULL,
                `role` VARCHAR(20) NOT NULL,
                `content` TEXT NOT NULL,
                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

            -- 预警记录表
            CREATE TABLE IF NOT EXISTS `alert_record` (
                `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                `user_id` BIGINT NOT NULL,
                `risk_level` VARCHAR(20) NOT NULL,
                `content` TEXT,
                `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

            -- 管理员邮箱表
            CREATE TABLE IF NOT EXISTS `admin_email` (
                `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                `email` VARCHAR(100) NOT NULL,
                `name` VARCHAR(50) NOT NULL,
                `is_active` TINYINT NOT NULL DEFAULT 1,
                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

            -- 初始化角色
            INSERT IGNORE INTO `role` (`role_name`, `role_key`, `description`) VALUES
            ('学生用户', 'USER', '普通学生用户'),
            ('系统管理员', 'ADMIN', '系统管理员'),
            ('心理咨询师', 'COUNSELOR', '心理咨询师');
            """;
    }

    private void createAdminUser(InstallRequest request) {
        String checkSql = "SELECT COUNT(*) FROM user WHERE role = 'ADMIN'";
        Long count = jdbcTemplate.queryForObject(checkSql, Long.class);

        if (count != null && count > 0) {
            log.info("管理员账户已存在，跳过创建");
            return;
        }

        String encodedPassword = passwordEncoder.encode(request.getAdminPassword());

        String insertSql = """
            INSERT INTO user (username, password, email, role, status, real_name, created_at, updated_at)
            VALUES (?, ?, ?, 'ADMIN', 1, ?, NOW(), NOW())
            """;

        jdbcTemplate.update(insertSql,
                request.getAdminUsername(),
                encodedPassword,
                request.getAdminEmail(),
                request.getAdminRealName() != null ? request.getAdminRealName() : "系统管理员"
        );

        log.info("管理员账户创建成功: {}", request.getAdminUsername());
    }

    private void configureAdminEmail(String email) {
        String checkSql = "SELECT COUNT(*) FROM admin_email WHERE email = ?";
        Long count = jdbcTemplate.queryForObject(checkSql, Long.class, email);

        if (count != null && count > 0) {
            return;
        }

        String insertSql = "INSERT INTO admin_email (email, name, is_active, created_at) VALUES (?, '预警接收人', 1, NOW())";
        jdbcTemplate.update(insertSql, email);

        log.info("管理员邮箱配置成功: {}", email);
    }

    private List<String> getExistingTables() {
        List<String> tables = new ArrayList<>();
        try {
            String sql = "SHOW TABLES";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : results) {
                tables.add(row.values().iterator().next().toString().toLowerCase());
            }
        } catch (Exception e) {
            log.error("获取数据库表列表失败", e);
        }
        return tables;
    }

    private boolean hasAdminUser() {
        try {
            String sql = "SELECT COUNT(*) FROM user WHERE role = 'ADMIN'";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLockFileExists() {
        return Files.exists(Paths.get(lockFilePath));
    }

    private void createLockFile() {
        try {
            Path path = Paths.get(lockFilePath);
            String content = String.format("""
                # Calmara 安装锁定文件
                # 此文件表示系统已完成安装
                version=%s
                installed_at=%s
                """, VERSION, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            Files.writeString(path, content);
            log.info("安装锁定文件已创建: {}", path.toAbsolutePath());
        } catch (Exception e) {
            log.warn("创建锁定文件失败", e);
        }
    }

    private String readInstalledVersion() {
        try {
            Path path = Paths.get(lockFilePath);
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    if (line.startsWith("version=")) {
                        return line.substring("version=".length());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取安装版本失败", e);
        }
        return "unknown";
    }

    private void writeInstallLog(InstallRequest request) {
        try {
            Path logPath = Paths.get("./logs/install.log");
            Files.createDirectories(logPath.getParent());

            String logContent = String.format("""
                ========================================
                Calmara 系统安装日志
                ========================================
                安装时间: %s
                系统版本: %s
                管理员账户: %s
                管理员邮箱: %s
                数据库: %s
                ========================================
                """,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    VERSION,
                    request.getAdminUsername(),
                    request.getAdminEmail(),
                    getDatabaseInfo().getUrl()
            );

            Files.writeString(logPath, logContent);
        } catch (Exception e) {
            log.warn("写入安装日志失败", e);
        }
    }

    public boolean isInstalled() {
        return checkInstallStatus().isInstalled();
    }

    @Data
    public static class InstallStatus {
        private boolean installed;
        private boolean databaseConnected;
        private String version;
        private String message;
        private List<String> existingTables;
        private List<String> missingTables;
        private LocalDateTime checkTime;
    }

    @Data
    public static class InstallResult {
        private boolean success;
        private String message;
        private String errorMessage;
        private String version;
        private boolean databaseInitialized;
        private boolean adminCreated;
        private boolean emailConfigured;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Data
    public static class InstallRequest {
        private String adminUsername;
        private String adminPassword;
        private String adminEmail;
        private String adminRealName;
        private String siteName;
        private String siteUrl;
        
        public String getAdminEmail() {
            return adminEmail;
        }
    }

    @Data
    public static class DatabaseInfo {
        private boolean connected;
        private String databaseProductName;
        private String databaseProductVersion;
        private String driverName;
        private String driverVersion;
        private String url;
        private String username;
        private String errorMessage;
    }
}
