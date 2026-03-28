package com.calmara.web.controller;

import com.calmara.common.Result;
import com.calmara.infrastructure.service.ServiceManager;
import com.calmara.infrastructure.service.ServiceManager.ServiceStatus;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceManager serviceManager;

    public ServiceController(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @GetMapping("/status")
    public Result<ServicesStatusResponse> getStatus() {
        serviceManager.checkAllServices();

        ServicesStatusResponse response = new ServicesStatusResponse();
        response.setAllHealthy(serviceManager.isAllServicesHealthy());
        response.setServices(serviceManager.getServiceStatuses());
        response.setTimestamp(new Date());

        return Result.success(response);
    }

    @PostMapping("/start")
    public Result<StartServicesResponse> startServices() {
        serviceManager.startMissingServices();

        StartServicesResponse response = new StartServicesResponse();
        response.setAllHealthy(serviceManager.isAllServicesHealthy());
        response.setServices(serviceManager.getServiceStatuses());

        return Result.success(response);
    }

    @PostMapping("/start/{serviceName}")
    public Result<ServiceStatus> startService(@PathVariable String serviceName) {
        boolean started = serviceManager.startService(serviceName);

        ServiceStatus status = serviceManager.getServiceStatus(serviceName);
        if (status == null) {
            return Result.error(404, "服务不存在: " + serviceName);
        }

        return Result.success(status);
    }

    @PostMapping("/stop")
    public Result<String> stopServices() {
        serviceManager.stopAllServices();
        return Result.success("所有服务已停止");
    }

    @GetMapping("/check")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allHealthy", serviceManager.isAllServicesHealthy());
        result.put("timestamp", new Date());

        Map<String, Boolean> serviceHealth = new LinkedHashMap<>();
        for (Map.Entry<String, ServiceStatus> entry : serviceManager.getServiceStatuses().entrySet()) {
            serviceHealth.put(entry.getKey(), entry.getValue().isHealthy());
        }
        result.put("services", serviceHealth);

        return Result.success(result);
    }

    @Data
    public static class ServicesStatusResponse {
        private boolean allHealthy;
        private Map<String, ServiceStatus> services;
        private Date timestamp;
    }

    @Data
    public static class StartServicesResponse {
        private boolean allHealthy;
        private Map<String, ServiceStatus> services;
    }
}
