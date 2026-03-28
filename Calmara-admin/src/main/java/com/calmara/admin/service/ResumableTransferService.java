package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.jcraft.jsch.*;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class ResumableTransferService {

    private static final int CHUNK_SIZE = 1024 * 1024 * 10;
    private static final int MAX_CONCURRENT_TRANSFERS = 3;
    private final Semaphore transferSemaphore = new Semaphore(MAX_CONCURRENT_TRANSFERS);
    
    private final Map<String, TransferProgress> activeTransfers = new ConcurrentHashMap<>();
    private volatile boolean shutdown = false;

    @PreDestroy
    public void shutdown() {
        shutdown = true;
        activeTransfers.values().forEach(p -> p.setCancelled(true));
        log.info("ResumableTransferService已关闭，取消所有活动传输");
    }

    public UploadResult uploadWithResume(FinetuneConfigEntity.RemoteServerConfig config,
                                          String localPath, String remotePath,
                                          TransferProgressCallback callback) {
        if (shutdown) {
            return UploadResult.failure("服务正在关闭，无法启动新传输");
        }
        
        String transferId = UUID.randomUUID().toString();
        TransferProgress progress = new TransferProgress(transferId, localPath, remotePath, true);
        activeTransfers.put(transferId, progress);
        
        Session session = null;
        ChannelSftp sftp = null;
        
        try {
            transferSemaphore.acquire();
            
            File localFile = new File(localPath);
            if (!localFile.exists()) {
                return UploadResult.failure("本地文件不存在: " + localPath);
            }
            
            long fileSize = localFile.length();
            progress.setTotalBytes(fileSize);
            
            session = createSession(config);
            session.connect();
            
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
            
            long remoteSize = getRemoteFileSize(sftp, remotePath);
            long offset = canResumeUpload(sftp, localPath, remotePath, remoteSize);
            
            if (offset > 0) {
                log.info("检测到可续传: localPath={}, remotePath={}, offset={}", localPath, remotePath, offset);
                progress.setTransferredBytes(offset);
            }
            
            ensureRemoteDirectory(sftp, remotePath);
            
            final ChannelSftp finalSftp = sftp;
            try (RandomAccessFile raf = new RandomAccessFile(localFile, "r")) {
                raf.seek(offset);
                
                finalSftp.put(new InputStream() {
                    private long remaining = fileSize - offset;
                    
                    @Override
                    public int read() throws IOException {
                        byte[] b = new byte[1];
                        int n = read(b);
                        return n == -1 ? -1 : b[0] & 0xFF;
                    }
                    
                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        if (remaining <= 0) return -1;
                        if (progress.isCancelled()) return -1;
                        
                        int toRead = (int) Math.min(len, remaining);
                        int read = raf.read(b, off, toRead);
                        
                        if (read > 0) {
                            remaining -= read;
                            progress.addTransferredBytes(read);
                            
                            if (callback != null) {
                                callback.onProgress(transferId, progress.getTransferredBytes(), progress.getTotalBytes());
                            }
                        }
                        
                        return read;
                    }
                }, remotePath, ChannelSftp.RESUME);
            }
            
            progress.setCompleted(true);
            log.info("文件上传完成: {} -> {}", localPath, remotePath);
            
            return UploadResult.success(localPath, remotePath, fileSize);
            
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            progress.setError(e.getMessage());
            return UploadResult.failure(e.getMessage());
            
        } finally {
            closeQuietly(sftp, "SFTP");
            closeQuietly(session, "SSH Session");
            transferSemaphore.release();
            activeTransfers.remove(transferId);
        }
    }

    public DownloadResult downloadWithResume(FinetuneConfigEntity.RemoteServerConfig config,
                                              String remotePath, String localPath,
                                              TransferProgressCallback callback) {
        if (shutdown) {
            return DownloadResult.failure("服务正在关闭，无法启动新传输");
        }
        
        String transferId = UUID.randomUUID().toString();
        TransferProgress progress = new TransferProgress(transferId, remotePath, localPath, false);
        activeTransfers.put(transferId, progress);
        
        Session session = null;
        ChannelSftp sftp = null;
        
        try {
            transferSemaphore.acquire();
            
            session = createSession(config);
            session.connect();
            
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
            
            SftpATTRS attrs = sftp.stat(remotePath);
            long fileSize = attrs.getSize();
            progress.setTotalBytes(fileSize);
            
            File localFile = new File(localPath);
            long offset = 0;
            
            if (localFile.exists()) {
                offset = localFile.length();
                if (offset >= fileSize) {
                    log.info("文件已存在且完整: {}", localPath);
                    return DownloadResult.success(remotePath, localPath, fileSize);
                }
                log.info("检测到可续传: remotePath={}, localPath={}, offset={}", remotePath, localPath, offset);
                progress.setTransferredBytes(offset);
            } else {
                localFile.getParentFile().mkdirs();
            }
            
            final ChannelSftp finalSftp = sftp;
            try (RandomAccessFile raf = new RandomAccessFile(localFile, "rw")) {
                raf.seek(offset);
                
                final long finalOffset = offset;
                finalSftp.get(remotePath, new OutputStream() {
                    private long written = finalOffset;
                    
                    @Override
                    public void write(int b) throws IOException {
                        if (progress.isCancelled()) {
                            throw new IOException("传输已取消");
                        }
                        raf.write(b);
                        written++;
                        progress.setTransferredBytes(written);
                        
                        if (callback != null && written % 8192 == 0) {
                            callback.onProgress(transferId, progress.getTransferredBytes(), progress.getTotalBytes());
                        }
                    }
                    
                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        if (progress.isCancelled()) {
                            throw new IOException("传输已取消");
                        }
                        raf.write(b, off, len);
                        written += len;
                        progress.setTransferredBytes(written);
                        
                        if (callback != null) {
                            callback.onProgress(transferId, progress.getTransferredBytes(), progress.getTotalBytes());
                        }
                    }
                });
            }
            
            progress.setCompleted(true);
            log.info("文件下载完成: {} -> {}", remotePath, localPath);
            
            return DownloadResult.success(remotePath, localPath, fileSize);
            
        } catch (Exception e) {
            log.error("文件下载失败: {}", e.getMessage(), e);
            progress.setError(e.getMessage());
            return DownloadResult.failure(e.getMessage());
            
        } finally {
            closeQuietly(sftp, "SFTP");
            closeQuietly(session, "SSH Session");
            transferSemaphore.release();
            activeTransfers.remove(transferId);
        }
    }

    private void closeQuietly(ChannelSftp sftp, String name) {
        if (sftp != null) {
            try {
                if (sftp.isConnected()) {
                    sftp.disconnect();
                }
            } catch (Exception e) {
                log.warn("关闭{}连接时出错: {}", name, e.getMessage());
            }
        }
    }

    private void closeQuietly(Session session, String name) {
        if (session != null) {
            try {
                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception e) {
                log.warn("关闭{}连接时出错: {}", name, e.getMessage());
            }
        }
    }

    private long canResumeUpload(ChannelSftp sftp, String localPath, String remotePath, long remoteSize) {
        if (remoteSize <= 0) return 0;
        
        try {
            File localFile = new File(localPath);
            long localSize = localFile.length();
            
            if (remoteSize > localSize) {
                return 0;
            }
            
            return remoteSize;
            
        } catch (Exception e) {
            log.warn("检查续传条件失败: {}", e.getMessage());
            return 0;
        }
    }

    private long getRemoteFileSize(ChannelSftp sftp, String remotePath) {
        try {
            SftpATTRS attrs = sftp.stat(remotePath);
            return attrs.getSize();
        } catch (Exception e) {
            return 0;
        }
    }

    private void ensureRemoteDirectory(ChannelSftp sftp, String remotePath) throws SftpException {
        String parentPath = remotePath.substring(0, remotePath.lastIndexOf('/'));
        if (parentPath.isEmpty()) return;
        
        try {
            sftp.cd(parentPath);
        } catch (SftpException e) {
            ensureRemoteDirectory(sftp, parentPath);
            try {
                sftp.mkdir(parentPath);
            } catch (SftpException ex) {
                if (ex.id != ChannelSftp.SSH_FX_FAILURE) {
                    throw ex;
                }
            }
        }
    }

    private Session createSession(FinetuneConfigEntity.RemoteServerConfig config) throws JSchException {
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
        
        Properties prop = new Properties();
        String knownHostsFile = System.getProperty("user.home") + "/.ssh/known_hosts";
        File knownHosts = new File(knownHostsFile);
        if (knownHosts.exists()) {
            jsch.setKnownHosts(knownHostsFile);
            prop.put("StrictHostKeyChecking", "ask");
        } else {
            prop.put("StrictHostKeyChecking", "accept-new");
            log.warn("未找到known_hosts文件，使用accept-new策略。建议配置SSH主机密钥以提高安全性。");
        }
        prop.put("PreferredAuthentications", "publickey,password");
        session.setConfig(prop);
        session.setTimeout(config.getConnectionTimeout() > 0 ? config.getConnectionTimeout() : 30000);
        
        return session;
    }

    public Optional<TransferProgress> getTransferProgress(String transferId) {
        return Optional.ofNullable(activeTransfers.get(transferId));
    }

    public List<TransferProgress> getActiveTransfers() {
        return new ArrayList<>(activeTransfers.values());
    }

    public void cancelTransfer(String transferId) {
        TransferProgress progress = activeTransfers.get(transferId);
        if (progress != null) {
            progress.setCancelled(true);
            log.info("已取消传输: {}", transferId);
        }
    }

    @Data
    public static class TransferProgress {
        private final String transferId;
        private final String sourcePath;
        private final String destinationPath;
        private final boolean isUpload;
        private long totalBytes;
        private long transferredBytes;
        private boolean completed;
        private boolean cancelled;
        private String error;
        private final LocalDateTime startTime = LocalDateTime.now();
        
        public TransferProgress(String transferId, String sourcePath, String destinationPath, boolean isUpload) {
            this.transferId = transferId;
            this.sourcePath = sourcePath;
            this.destinationPath = destinationPath;
            this.isUpload = isUpload;
        }
        
        public double getProgressPercent() {
            return totalBytes > 0 ? (double) transferredBytes / totalBytes * 100 : 0;
        }
        
        public synchronized void addTransferredBytes(long bytes) {
            this.transferredBytes += bytes;
        }
    }

    @Data
    public static class UploadResult {
        private boolean success;
        private String message;
        private String error;
        private String localPath;
        private String remotePath;
        private long bytesTransferred;
        
        public static UploadResult success(String localPath, String remotePath, long bytes) {
            UploadResult result = new UploadResult();
            result.setSuccess(true);
            result.setMessage("上传成功");
            result.setLocalPath(localPath);
            result.setRemotePath(remotePath);
            result.setBytesTransferred(bytes);
            return result;
        }
        
        public static UploadResult failure(String message) {
            UploadResult result = new UploadResult();
            result.setSuccess(false);
            result.setMessage(message);
            result.setError(message);
            return result;
        }
    }

    @Data
    public static class DownloadResult {
        private boolean success;
        private String message;
        private String error;
        private String remotePath;
        private String localPath;
        private long bytesTransferred;
        
        public static DownloadResult success(String remotePath, String localPath, long bytes) {
            DownloadResult result = new DownloadResult();
            result.setSuccess(true);
            result.setMessage("下载成功");
            result.setRemotePath(remotePath);
            result.setLocalPath(localPath);
            result.setBytesTransferred(bytes);
            return result;
        }
        
        public static DownloadResult failure(String message) {
            DownloadResult result = new DownloadResult();
            result.setSuccess(false);
            result.setMessage(message);
            result.setError(message);
            return result;
        }
    }

    @FunctionalInterface
    public interface TransferProgressCallback {
        void onProgress(String transferId, long transferred, long total);
    }
}
