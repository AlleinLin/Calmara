package com.calmara.install.interceptor;

import com.calmara.install.service.InstallService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class InstallInterceptor implements HandlerInterceptor {

    private final InstallService installService;

    private static final List<String> ALLOWED_PATHS = Arrays.asList(
            "/install",
            "/install/",
            "/install/status",
            "/install/execute",
            "/install/database-info",
            "/install/test-database",
            "/install/requirements",
            "/install.html",
            "/css/",
            "/js/",
            "/images/",
            "/favicon.ico",
            "/error"
    );

    private volatile Boolean installedCache = null;
    private volatile long lastCheckTime = 0;
    private static final long CACHE_TTL = 60000;

    public InstallInterceptor(InstallService installService) {
        this.installService = installService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        log.debug("InstallInterceptor检查: {} {}", method, requestURI);

        if (isAllowedPath(requestURI)) {
            log.debug("允许访问安装相关路径: {}", requestURI);
            return true;
        }

        if (!isInstalled()) {
            log.info("系统未安装，重定向到安装页面: {}", requestURI);

            if (isApiRequest(requestURI)) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":503,\"message\":\"系统未安装，请先完成安装\",\"data\":{\"installUrl\":\"/install\"}}");
                return false;
            }

            response.sendRedirect("/install");
            return false;
        }

        return true;
    }

    private boolean isAllowedPath(String path) {
        if (path == null) {
            return false;
        }

        for (String allowed : ALLOWED_PATHS) {
            if (path.startsWith(allowed) || path.equals(allowed)) {
                return true;
            }
        }

        return false;
    }

    private boolean isApiRequest(String path) {
        return path != null && path.startsWith("/api/");
    }

    private boolean isInstalled() {
        long now = System.currentTimeMillis();

        if (installedCache != null && (now - lastCheckTime) < CACHE_TTL) {
            return installedCache;
        }

        synchronized (this) {
            if (installedCache != null && (now - lastCheckTime) < CACHE_TTL) {
                return installedCache;
            }

            installedCache = installService.isInstalled();
            lastCheckTime = now;

            log.debug("检查安装状态: {}", installedCache ? "已安装" : "未安装");

            return installedCache;
        }
    }

    public void clearCache() {
        synchronized (this) {
            installedCache = null;
            lastCheckTime = 0;
            log.info("安装状态缓存已清除");
        }
    }
}
