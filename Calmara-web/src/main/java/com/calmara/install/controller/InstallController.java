package com.calmara.install.controller;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.common.Result;
import com.calmara.install.service.InstallService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
@RequestMapping("/install")
public class InstallController {

    private final InstallService installService;

    public InstallController(InstallService installService) {
        this.installService = installService;
    }

    @GetMapping("")
    public String installPage() {
        if (installService.isInstalled()) {
            log.info("系统已安装，重定向到首页");
            return "redirect:/";
        }
        return "forward:/install.html";
    }

    @GetMapping("/status")
    @ResponseBody
    public Result<InstallService.InstallStatus> getInstallStatus() {
        InstallService.InstallStatus status = installService.checkInstallStatus();
        return Result.success(status);
    }

    @GetMapping("/database-info")
    @ResponseBody
    public Result<InstallService.DatabaseInfo> getDatabaseInfo() {
        InstallService.DatabaseInfo info = installService.getDatabaseInfo();
        return Result.success(info);
    }

    @PostMapping(value = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Result<InstallService.InstallResult> executeInstall(
            @RequestBody InstallService.InstallRequest request) {

        validateInstallRequest(request);

        log.info("收到安装请求: adminUsername={}, adminEmail={}",
                request.getAdminUsername(), request.getAdminEmail());

        InstallService.InstallResult result = installService.install(request);

        if (result.isSuccess()) {
            log.info("系统安装成功");
            return Result.success(result);
        } else {
            log.error("系统安装失败: {}", result.getErrorMessage());
            return Result.error(500, result.getErrorMessage());
        }
    }

    private void validateInstallRequest(InstallService.InstallRequest request) {
        if (!StringUtils.hasText(request.getAdminUsername())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员用户名不能为空");
        }
        if (request.getAdminUsername().length() < 3 || request.getAdminUsername().length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员用户名长度应在3-50个字符之间");
        }
        if (!StringUtils.hasText(request.getAdminPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员密码不能为空");
        }
        if (request.getAdminPassword().length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员密码长度不能少于6位");
        }
        if (!StringUtils.hasText(request.getAdminEmail())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员邮箱不能为空");
        }
        if (!request.getAdminEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员邮箱格式不正确");
        }
    }

    @PostMapping("/test-database")
    @ResponseBody
    public Result<Boolean> testDatabaseConnection() {
        boolean connected = installService.testDatabaseConnection();
        if (connected) {
            return Result.success(true, "数据库连接成功");
        } else {
            return Result.error(500, "数据库连接失败");
        }
    }

    @GetMapping("/requirements")
    @ResponseBody
    public Result<SystemRequirements> checkRequirements() {
        SystemRequirements requirements = new SystemRequirements();

        requirements.setJavaVersion(System.getProperty("java.version"));
        requirements.setOsName(System.getProperty("os.name"));
        requirements.setOsVersion(System.getProperty("os.version"));

        Runtime runtime = Runtime.getRuntime();
        requirements.setMaxMemory(runtime.maxMemory() / 1024 / 1024 + " MB");
        requirements.setTotalMemory(runtime.totalMemory() / 1024 / 1024 + " MB");
        requirements.setFreeMemory(runtime.freeMemory() / 1024 / 1024 + " MB");

        requirements.setDatabaseConnected(installService.testDatabaseConnection());

        requirements.setJavaOk(isJavaVersionOk());
        requirements.setMemoryOk(runtime.maxMemory() >= 512 * 1024 * 1024);
        requirements.setAllOk(requirements.isJavaOk() && requirements.isMemoryOk() && requirements.isDatabaseConnected());

        return Result.success(requirements);
    }

    private boolean isJavaVersionOk() {
        try {
            String version = System.getProperty("java.version");
            int majorVersion = Integer.parseInt(version.split("\\.")[0]);
            return majorVersion >= 17;
        } catch (Exception e) {
            return false;
        }
    }

    @Data
    public static class SystemRequirements {
        private String javaVersion;
        private String osName;
        private String osVersion;
        private String maxMemory;
        private String totalMemory;
        private String freeMemory;
        private boolean databaseConnected;
        private boolean javaOk;
        private boolean memoryOk;
        private boolean allOk;
    }
}
