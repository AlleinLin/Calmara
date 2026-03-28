package com.calmara.api.controller;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.common.Result;
import com.calmara.model.entity.User;
import com.calmara.security.service.AuthService;
import com.calmara.security.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;

    public AuthController(AuthService authService, JwtUtils jwtUtils) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
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

    @PostMapping("/logout")
    public Result<Boolean> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            log.info("用户登出: username={}", jwtUtils.getUsernameFromToken(token));
        }
        SecurityContextHolder.clearContext();
        return Result.success(true);
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refreshToken(HttpServletRequest request) {
        String token = extractToken(request);
        
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未提供认证令牌");
        }
        
        String username = jwtUtils.getUsernameFromToken(token);
        String role = jwtUtils.getRoleFromToken(token);
        
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无效的令牌");
        }
        
        User user = authService.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已被禁用");
        }
        
        String newToken = jwtUtils.generateToken(username, role);
        log.info("令牌刷新成功: username={}", username);
        
        return Result.success(new TokenResponse(newToken));
    }

    @GetMapping("/me")
    public Result<UserInfoResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        
        String username = authentication.getName();
        User user = authService.getUserByUsername(username);
        
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        UserInfoResponse response = new UserInfoResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRealName(user.getRealName());
        response.setStudentId(user.getStudentId());
        response.setRole(user.getRole());
        response.setAvatar(user.getAvatar());
        response.setLastLoginAt(user.getLastLoginAt());
        
        return Result.success(response);
    }

    @PutMapping("/password")
    public Result<Boolean> changePassword(@RequestBody ChangePasswordRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        
        if (!StringUtils.hasText(request.getOldPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "原密码不能为空");
        }
        if (!StringUtils.hasText(request.getNewPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码不能为空");
        }
        if (request.getNewPassword().length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码长度不能少于6位");
        }
        
        String username = authentication.getName();
        User user = authService.getUserByUsername(username);
        
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        boolean success = authService.updatePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
        log.info("密码修改成功: username={}", username);
        
        return Result.success(success);
    }

    @PutMapping("/profile")
    public Result<Boolean> updateProfile(@RequestBody UpdateProfileRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        
        String username = authentication.getName();
        User user = authService.getUserByUsername(username);
        
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        boolean success = authService.updateProfile(
                user.getId(),
                request.getRealName(),
                request.getPhone(),
                request.getAvatar()
        );
        
        log.info("资料更新成功: username={}", username);
        return Result.success(success);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return request.getParameter("token");
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

    @Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    public static class UpdateProfileRequest {
        private String realName;
        private String phone;
        private String avatar;
    }

    @Data
    public static class TokenResponse {
        private String token;
        
        public TokenResponse(String token) {
            this.token = token;
        }
    }

    @Data
    public static class UserInfoResponse {
        private Long id;
        private String username;
        private String email;
        private String phone;
        private String realName;
        private String studentId;
        private String role;
        private String avatar;
        private java.time.LocalDateTime lastLoginAt;
    }
}
