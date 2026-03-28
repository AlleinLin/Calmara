package com.calmara.infrastructure.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ServiceManager {

    @Value("${calmara.services.auto-start:true}")
    private boolean autoStart;

    @Value("${calmara.services.wait-timeout:120}")
    private int waitTimeout;

    @Value("${calmara.chroma.url:http://localhost:8000}")
    private String chromaUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:root}")
    private String dbUsername;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${spring.datasource.database:calmara}")
    private String dbName;

    private final Map<String, ServiceStatus> serviceStatuses = new ConcurrentHashMap<>();

    private static final Map<String, ServiceConfig> REQUIRED_SERVICES = new LinkedHashMap<>();

    static {
        REQUIRED_SERVICES.put("mysql", new ServiceConfig("MySQL", 3306, "mysql", "calmara-mysql"));
        REQUIRED_SERVICES.put("redis", new ServiceConfig("Redis", 6379, "redis", "calmara-redis"));
        REQUIRED_SERVICES.put("chroma", new ServiceConfig("Chroma", 8000, "chromadb/chroma:latest", "calmara-chroma"));
    }

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("Calmara 服务管理器启动");
        log.info("========================================");

        checkAllServices();

        if (autoStart) {
            startMissingServices();
        }

        printServiceStatus();
    }

    public void checkAllServices() {
        log.info("检查依赖服务状态...");

        for (Map.Entry<String, ServiceConfig> entry : REQUIRED_SERVICES.entrySet()) {
            String name = entry.getKey();
            ServiceConfig config = entry.getValue();

            ServiceStatus status = checkService(name, config);
            serviceStatuses.put(name, status);
        }
    }

    private ServiceStatus checkService(String name, ServiceConfig config) {
        ServiceStatus status = new ServiceStatus();
        status.setName(name);
        status.setDisplayName(config.getDisplayName());
        status.setPort(config.getPort());
        status.setContainerName(config.getContainerName());

        boolean portOpen = isPortOpen("localhost", config.getPort());
        status.setPortOpen(portOpen);

        if (portOpen) {
            status.setStatus(ServiceStatus.Status.RUNNING);
            status.setHealthy(true);
            status.setMessage("服务运行中");
        } else {
            status.setStatus(ServiceStatus.Status.STOPPED);
            status.setHealthy(false);
            status.setMessage("服务未启动");
        }

        if ("chroma".equals(name) && portOpen) {
            status.setHealthy(checkChromaHealth());
        }

        return status;
    }

    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean checkChromaHealth() {
        try {
            URL url = new URL(chromaUrl + "/api/v1/heartbeat");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public void startMissingServices() {
        log.info("自动启动缺失的服务...");

        for (Map.Entry<String, ServiceStatus> entry : serviceStatuses.entrySet()) {
            String name = entry.getKey();
            ServiceStatus status = entry.getValue();

            if (!status.isHealthy()) {
                log.warn("服务 {} 未运行，尝试启动...", status.getDisplayName());
                boolean started = startService(name);
                status.setStarted(started);

                if (started) {
                    status.setStatus(ServiceStatus.Status.RUNNING);
                    status.setHealthy(true);
                    status.setMessage("自动启动成功");
                } else {
                    status.setMessage("自动启动失败，请手动启动");
                }
            }
        }
    }

    public boolean startService(String serviceName) {
        ServiceConfig config = REQUIRED_SERVICES.get(serviceName);
        if (config == null) {
            log.error("未知服务: {}", serviceName);
            return false;
        }

        if (!isDockerAvailable()) {
            log.warn("Docker不可用，无法自动启动服务");
            return false;
        }

        try {
            String containerName = config.getContainerName();

            if (isContainerRunning(containerName)) {
                log.info("容器 {} 已在运行", containerName);
                return true;
            }

            if (isContainerExists(containerName)) {
                return startExistingContainer(containerName);
            }

            return createAndStartContainer(serviceName, config);

        } catch (Exception e) {
            log.error("启动服务 {} 失败: {}", serviceName, e.getMessage());
            return false;
        }
    }

    private boolean isDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isContainerRunning(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "--filter", "name=" + containerName,
                    "--filter", "status=running", "-q"
            );
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            return line != null && !line.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isContainerExists(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "-a", "--filter", "name=" + containerName, "-q"
            );
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            return line != null && !line.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean startExistingContainer(String containerName) {
        try {
            log.info("启动已存在的容器: {}", containerName);
            ProcessBuilder pb = new ProcessBuilder("docker", "start", containerName);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Thread.sleep(2000);
                return waitForService(containerName, 30);
            }
            return false;
        } catch (Exception e) {
            log.error("启动容器失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean createAndStartContainer(String serviceName, ServiceConfig config) {
        try {
            List<String> command = buildDockerRunCommand(serviceName, config);
            log.info("创建并启动容器: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("[Docker] {}", line);
                    }
                } catch (IOException e) {
                    log.error("读取Docker输出失败", e);
                }
            }).start();

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return waitForService(config.getContainerName(), waitTimeout);
            }

            return false;
        } catch (Exception e) {
            log.error("创建容器失败: {}", e.getMessage());
            return false;
        }
    }

    private List<String> buildDockerRunCommand(String serviceName, ServiceConfig config) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("-d");
        command.add("--name");
        command.add(config.getContainerName());
        command.add("-p");
        command.add(config.getPort() + ":" + config.getPort());

        switch (serviceName) {
            case "mysql":
                if (dbPassword == null || dbPassword.isEmpty()) {
                    log.error("MySQL密码未配置，请设置环境变量 DB_PASSWORD 或 spring.datasource.password");
                    throw new IllegalStateException("MySQL密码未配置，无法启动服务");
                }
                command.add("-e");
                command.add("MYSQL_ROOT_PASSWORD=" + dbPassword);
                command.add("-e");
                command.add("MYSQL_DATABASE=" + dbName);
                command.add("-v");
                command.add("calmara-mysql-data:/var/lib/mysql");
                command.add("mysql:8.0");
                break;

            case "redis":
                command.add("-v");
                command.add("calmara-redis-data:/data");
                command.add("redis:7-alpine");
                break;

            case "chroma":
                command.add("-v");
                command.add("calmara-chroma-data:/chroma/chroma");
                command.add("chromadb/chroma:latest");
                break;

            default:
                command.add(config.getImage());
        }

        return command;
    }

    private boolean waitForService(String containerName, int timeoutSeconds) {
        log.info("等待服务启动: {} (超时: {}秒)", containerName, timeoutSeconds);

        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeout) {
            if (isContainerRunning(containerName)) {
                log.info("服务 {} 启动成功", containerName);
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.error("服务 {} 启动超时", containerName);
        return false;
    }

    public void stopAllServices() {
        log.info("停止所有服务...");

        for (ServiceConfig config : REQUIRED_SERVICES.values()) {
            try {
                if (isContainerRunning(config.getContainerName())) {
                    ProcessBuilder pb = new ProcessBuilder("docker", "stop", config.getContainerName());
                    Process process = pb.start();
                    process.waitFor();
                    log.info("服务 {} 已停止", config.getDisplayName());
                }
            } catch (Exception e) {
                log.error("停止服务 {} 失败: {}", config.getDisplayName(), e.getMessage());
            }
        }
    }

    public void printServiceStatus() {
        log.info("");
        log.info("========================================");
        log.info("服务状态汇总");
        log.info("========================================");

        boolean allHealthy = true;

        for (ServiceStatus status : serviceStatuses.values()) {
            String icon = status.isHealthy() ? "✓" : "✗";
            String statusStr = status.isHealthy() ? "运行中" : "已停止";
            log.info("  {} {}: {} (端口 {})", icon, status.getDisplayName(), statusStr, status.getPort());

            if (!status.isHealthy()) {
                allHealthy = false;
            }
        }

        log.info("========================================");

        if (!allHealthy) {
            log.warn("");
            log.warn("部分服务未启动，系统将以降级模式运行");
            log.warn("请手动启动缺失的服务，或使用以下命令：");
            log.warn("  docker-compose up -d");
            log.warn("");
        } else {
            log.info("所有服务运行正常！");
        }
    }

    public Map<String, ServiceStatus> getServiceStatuses() {
        return new HashMap<>(serviceStatuses);
    }

    public boolean isAllServicesHealthy() {
        return serviceStatuses.values().stream().allMatch(ServiceStatus::isHealthy);
    }

    public ServiceStatus getServiceStatus(String serviceName) {
        return serviceStatuses.get(serviceName);
    }

    @Data
    public static class ServiceConfig {
        private final String displayName;
        private final int port;
        private final String image;
        private final String containerName;
    }

    @Data
    public static class ServiceStatus {
        private String name;
        private String displayName;
        private int port;
        private String containerName;
        private Status status;
        private boolean healthy;
        private boolean portOpen;
        private boolean started;
        private String message;

        public enum Status {
            RUNNING, STOPPED, STARTING, ERROR
        }
    }
}
