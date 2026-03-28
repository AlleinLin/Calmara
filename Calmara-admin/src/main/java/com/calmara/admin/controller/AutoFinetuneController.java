package com.calmara.admin.controller;

import com.calmara.admin.service.AutoFinetuneService;
import com.calmara.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/finetune")
public class AutoFinetuneController {

    @Autowired
    private AutoFinetuneService autoFinetuneService;

    @Value("${calmara.finetune.enabled:false}")
    private boolean finetuneEnabled;

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = autoFinetuneService.getTrainingStatus();
        status.put("autoFinetuneEnabled", finetuneEnabled);
        return Result.success(status);
    }

    @PostMapping("/trigger")
    public Result<String> triggerFinetune(@RequestBody(required = false) Map<String, String> params) {
        if (autoFinetuneService.isTrainingInProgress()) {
            return Result.error(400, "训练已在进行中");
        }

        String reason = params != null ? params.get("reason") : "手动触发";
        autoFinetuneService.triggerFinetune(reason != null ? reason : "手动触发");

        return Result.success("微调任务已启动");
    }

    @PostMapping("/stop")
    public Result<String> stopFinetune() {
        if (!autoFinetuneService.isTrainingInProgress()) {
            return Result.error(400, "没有正在进行的训练");
        }

        autoFinetuneService.stopTraining();
        return Result.success("已发送停止信号");
    }

    @GetMapping("/is-training")
    public Result<Boolean> isTraining() {
        return Result.success(autoFinetuneService.isTrainingInProgress());
    }
}
