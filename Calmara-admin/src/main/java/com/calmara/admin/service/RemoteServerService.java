package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.jcraft.jsch.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class RemoteServerService {

    public ConnectionTestResult testConnection(FinetuneConfigEntity.RemoteServerConfig config) {
        ConnectionTestResult result = new ConnectionTestResult();
        result.setHost(config.getHost());
        result.setPort(config.getPort());
        
        Session session = null;
        try {
            JSch jsch = new JSch();
            
            if (config.getPrivateKeyPath() != null && !config.getPrivateKeyPath().isEmpty()) {
                if (config.getPrivateKeyPassphrase() != null && !config.getPrivateKeyPassphrase().isEmpty()) {
                    jsch.addIdentity(config.getPrivateKeyPath(), config.getPrivateKeyPassphrase());
                } else {
                    jsch.addIdentity(config.getPrivateKeyPath());
                }
            }
            
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                session.setPassword(config.getPassword());
            }
            
            Properties prop = new Properties();
            prop.put("StrictHostKeyChecking", "no");
            prop.put("PreferredAuthentications", "publickey,password");
            session.setConfig(prop);
            session.setTimeout(config.getConnectionTimeout());
            
            session.connect();
            result.setConnected(true);
            result.setMessage("连接成功");
            
            String gpuInfo = executeCommand(session, "nvidia-smi --query-gpu=name,memory.total --format=csv,noheader 2>/dev/null || echo 'No GPU found'");
            result.setGpuInfo(gpuInfo);
            
            String pythonVersion = executeCommand(session, config.getRemotePythonPath() + " --version 2>/dev/null || echo 'Python not found'");
            result.setPythonVersion(pythonVersion);
            
            String diskSpace = executeCommand(session, "df -h " + config.getRemoteModelPath() + " 2>/dev/null | tail -1 | awk '{print $4}' || echo 'Unknown'");
            result.setAvailableDiskSpace(diskSpace);
            
            log.info("远程服务器连接测试成功: {}:{}", config.getHost(), config.getPort());
            
        } catch (JSchException e) {
            result.setConnected(false);
            result.setMessage("连接失败: " + e.getMessage());
            log.error("远程服务器连接失败: {}", e.getMessage());
        } catch (Exception e) {
            result.setConnected(false);
            result.setMessage("测试失败: " + e.getMessage());
            log.error("远程服务器测试失败", e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
    }

    public CommandResult executeRemoteCommand(FinetuneConfigEntity.RemoteServerConfig config, String command) {
        CommandResult result = new CommandResult();
        Session session = null;
        
        try {
            session = createSession(config);
            session.connect();
            
            String output = executeCommand(session, command);
            result.setSuccess(true);
            result.setOutput(output);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            log.error("执行远程命令失败: {}", e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
    }

    public UploadResult uploadFiles(FinetuneConfigEntity.RemoteServerConfig config, 
                                     String localPath, String remotePath) {
        UploadResult result = new UploadResult();
        Session session = null;
        
        try {
            session = createSession(config);
            session.connect();
            
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
            
            File localFile = new File(localPath);
            
            if (localFile.isDirectory()) {
                uploadDirectory(sftp, localFile, remotePath);
            } else {
                sftp.put(localPath, remotePath);
            }
            
            sftp.disconnect();
            
            result.setSuccess(true);
            result.setMessage("上传成功");
            log.info("文件上传成功: {} -> {}", localPath, remotePath);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            log.error("文件上传失败: {}", e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
    }

    public DownloadResult downloadFiles(FinetuneConfigEntity.RemoteServerConfig config,
                                         String remotePath, String localPath) {
        DownloadResult result = new DownloadResult();
        Session session = null;
        
        try {
            session = createSession(config);
            session.connect();
            
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
            
            new File(localPath).getParentFile().mkdirs();
            sftp.get(remotePath, localPath);
            
            sftp.disconnect();
            
            result.setSuccess(true);
            result.setMessage("下载成功");
            log.info("文件下载成功: {} -> {}", remotePath, localPath);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            log.error("文件下载失败: {}", e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
    }

    public TrainingLaunchResult launchRemoteTraining(FinetuneConfigEntity.RemoteServerConfig config,
                                                      String trainingCommand) {
        TrainingLaunchResult result = new TrainingLaunchResult();
        Session session = null;
        
        try {
            session = createSession(config);
            session.connect();
            
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(trainingCommand);
            
            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            
            channel.connect();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder output = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            channel.disconnect();
            
            result.setSuccess(true);
            result.setOutput(output.toString());
            log.info("远程训练启动成功");
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            log.error("远程训练启动失败: {}", e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
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
        prop.put("StrictHostKeyChecking", "no");
        prop.put("PreferredAuthentications", "publickey,password");
        session.setConfig(prop);
        session.setTimeout(config.getConnectionTimeout());
        
        return session;
    }

    private String executeCommand(Session session, String command) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        
        InputStream in = channel.getInputStream();
        channel.connect();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder output = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        
        channel.disconnect();
        
        return output.toString().trim();
    }

    private void uploadDirectory(ChannelSftp sftp, File localDir, String remotePath) throws SftpException {
        try {
            sftp.mkdir(remotePath);
        } catch (SftpException e) {
            // 目录可能已存在
        }
        
        for (File file : localDir.listFiles()) {
            String remoteFilePath = remotePath + "/" + file.getName();
            if (file.isDirectory()) {
                uploadDirectory(sftp, file, remoteFilePath);
            } else {
                sftp.put(file.getAbsolutePath(), remoteFilePath);
            }
        }
    }

    @Data
    public static class ConnectionTestResult {
        private String host;
        private int port;
        private boolean connected;
        private String message;
        private String gpuInfo;
        private String pythonVersion;
        private String availableDiskSpace;
    }

    @Data
    public static class CommandResult {
        private boolean success;
        private String output;
        private String error;
    }

    @Data
    public static class UploadResult {
        private boolean success;
        private String message;
        private String error;
    }

    @Data
    public static class DownloadResult {
        private boolean success;
        private String message;
        private String error;
    }

    @Data
    public static class TrainingLaunchResult {
        private boolean success;
        private String output;
        private String error;
    }
}
