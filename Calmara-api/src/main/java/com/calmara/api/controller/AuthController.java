package com.calmara.api.controller;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.common.Result;
import com.calmara.security.service.AuthService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<AuthService.LoginResponse> login(@RequestBody LoginRequest request) {
        if (!StringUtils.hasText(request.getUsername())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名不能为空");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不能为空");
        }
        log.info("登录请求: username={}", request.getUsername());
        AuthService.LoginResponse response = authService.login(request.getUsername(), request.getPassword());
        return Result.success(response);
    }

    @PostMapping("/register")
    public Result<Boolean> register(@RequestBody RegisterRequest request) {
        if (!StringUtils.hasText(request.getUsername())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名不能为空");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不能为空");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "邮箱不能为空");
        }
        if (request.getPassword().length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度不能少于6位");
        }
        if (!request.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "邮箱格式不正确");
        }
        log.info("注册请求: username={}", request.getUsername());
        boolean success = authService.register(request.getUsername(), request.getPassword(), request.getEmail());
        return Result.success(success);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;
    }
}
