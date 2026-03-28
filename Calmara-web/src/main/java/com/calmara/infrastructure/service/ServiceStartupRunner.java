package com.calmara.infrastructure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceStartupRunner implements ApplicationRunner {

    private final ServiceManager serviceManager;

    @Override
    public void run(ApplicationArguments args) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║       Calmara 心理健康智能体系统启动完成                    ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("");

        if (!serviceManager.isAllServicesHealthy()) {
            log.warn("⚠️  部分依赖服务未就绪，系统将以降级模式运行");
            log.warn("   访问 /api/services/status 查看服务状态");
            log.warn("   或运行 docker-compose up -d 启动所有服务");
        } else {
            log.info("✅ 所有依赖服务已就绪，系统运行正常");
        }

        log.info("");
        log.info("访问地址: http://localhost:8080");
        log.info("API文档: http://localhost:8080/swagger-ui.html");
        log.info("服务状态: http://localhost:8080/api/services/status");
        log.info("");
    }
}
