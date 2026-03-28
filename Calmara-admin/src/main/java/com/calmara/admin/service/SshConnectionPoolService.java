package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.jcraft.jsch.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class SshConnectionPoolService {

    private static final int MAX_POOL_SIZE = 10;
    private static final int MIN_IDLE_CONNECTIONS = 2;
    private static final long CONNECTION_TIMEOUT_MS = 30000;
    private static final long IDLE_TIMEOUT_MS = 300000;
    private static final long HEALTH_CHECK_INTERVAL_MS = 60000;
    
    private final Map<String, PooledConnection> connectionPool = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUsedTime = new ConcurrentHashMap<>();
    private final Queue<String> availableConnections = new ConcurrentLinkedQueue<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    
    private final ScheduledExecutorService healthChecker = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean shutdownRequested = false;

    public SshConnectionPoolService() {
        startHealthChecker();
    }

    public PooledConnection getConnection(FinetuneConfigEntity.RemoteServerConfig config) 
            throws ConnectionException {
        String poolKey = generatePoolKey(config);
        
        PooledConnection connection = tryGetExistingConnection(poolKey);
        if (connection != null && connection.isValid()) {
            lastUsedTime.put(poolKey, System.currentTimeMillis());
            log.debug("复用现有连接: poolKey={}", poolKey);
            return connection;
        }
        
        if (totalConnections.get() >= MAX_POOL_SIZE) {
            cleanupIdleConnections();
        }
        
        return createNewConnection(config, poolKey);
    }

    public void releaseConnection(String poolKey) {
        if (poolKey != null && connectionPool.containsKey(poolKey)) {
            availableConnections.offer(poolKey);
            log.debug("释放连接: poolKey={}", poolKey);
        }
    }

    public void invalidateConnection(String poolKey) {
        PooledConnection connection = connectionPool.remove(poolKey);
        if (connection != null) {
            connection.disconnect();
            totalConnections.decrementAndGet();
            log.info("连接已失效并移除: poolKey={}", poolKey);
        }
        availableConnections.remove(poolKey);
        lastUsedTime.remove(poolKey);
    }

    private PooledConnection tryGetExistingConnection(String poolKey) {
        String availableKey = availableConnections.poll();
        if (availableKey != null && availableKey.equals(poolKey)) {
            return connectionPool.get(poolKey);
        }
        if (availableKey != null) {
            availableConnections.offer(availableKey);
        }
        return null;
    }

    private PooledConnection createNewConnection(FinetuneConfigEntity.RemoteServerConfig config, 
                                                  String poolKey) throws ConnectionException {
        try {
            JSch jsch = new JSch();
            
            if (config.getPrivateKeyPath() != null && !config.getPrivateKeyPath().isEmpty()) {
                if (config.getPrivateKeyPassphrase() != null && !config.getPrivateKeyPassphrase().isEmpty()) {
                    jsch.addIdentity(config.getPrivateKeyPath(), config.getPrivateKeyPassphrase());
                } else {
                    jsch.addIdentity(config.getPrivateKeyPath());
                }
            }
            
            Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                session.setPassword(config.getPassword());
            }
            
            java.util.Properties prop = new java.util.Properties();
            prop.put("StrictHostKeyChecking", "no");
            prop.put("PreferredAuthentications", "publickey,password");
            session.setConfig(prop);
            session.setTimeout(config.getConnectionTimeout());
            
            session.connect();
            
            PooledConnection pooledConnection = new PooledConnection(poolKey, session);
            connectionPool.put(poolKey, pooledConnection);
            lastUsedTime.put(poolKey, System.currentTimeMillis());
            totalConnections.incrementAndGet();
            
            log.info("创建新SSH连接: poolKey={}, 总连接数={}", poolKey, totalConnections.get());
            
            return pooledConnection;
            
        } catch (JSchException e) {
            throw new ConnectionException("创建SSH连接失败: " + e.getMessage(), e);
        }
    }

    private void startHealthChecker() {
        healthChecker.scheduleAtFixedRate(() -> {
            if (shutdownRequested) return;
            
            try {
                cleanupIdleConnections();
                validateConnections();
                ensureMinIdleConnections();
            } catch (Exception e) {
                log.error("连接池健康检查失败: {}", e.getMessage());
            }
        }, HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void cleanupIdleConnections() {
        long now = System.currentTimeMillis();
        
        lastUsedTime.forEach((poolKey, lastUsed) -> {
            if (now - lastUsed > IDLE_TIMEOUT_MS) {
                if (totalConnections.get() > MIN_IDLE_CONNECTIONS) {
                    invalidateConnection(poolKey);
                    log.info("清理空闲连接: poolKey={}", poolKey);
                }
            }
        });
    }

    private void validateConnections() {
        connectionPool.forEach((poolKey, connection) -> {
            if (!connection.isValid()) {
                invalidateConnection(poolKey);
                log.warn("移除无效连接: poolKey={}", poolKey);
            }
        });
    }

    private void ensureMinIdleConnections() {
        // 简化实现，不预创建连接
    }

    private String generatePoolKey(FinetuneConfigEntity.RemoteServerConfig config) {
        return String.format("%s@%s:%d", 
                config.getUsername(), config.getHost(), config.getPort());
    }

    public void shutdown() {
        shutdownRequested = true;
        healthChecker.shutdown();
        
        connectionPool.forEach((key, conn) -> {
            try {
                conn.disconnect();
            } catch (Exception e) {
                log.warn("关闭连接失败: {}", e.getMessage());
            }
        });
        
        connectionPool.clear();
        availableConnections.clear();
        lastUsedTime.clear();
        
        log.info("SSH连接池已关闭");
    }

    public PoolStats getStats() {
        return new PoolStats(
                totalConnections.get(),
                connectionPool.size(),
                availableConnections.size(),
                lastUsedTime.size()
        );
    }

    @Data
    public static class PooledConnection {
        private final String poolKey;
        private final Session session;
        private final long createdAt = System.currentTimeMillis();
        private volatile boolean valid = true;
        
        public boolean isValid() {
            return valid && session != null && session.isConnected();
        }
        
        public void disconnect() {
            valid = false;
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        public Session getSession() {
            return session;
        }
    }

    @Data
    public static class PoolStats {
        private final int totalConnections;
        private final int activeConnections;
        private final int availableConnections;
        private final int trackedKeys;
    }

    public static class ConnectionException extends Exception {
        public ConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
