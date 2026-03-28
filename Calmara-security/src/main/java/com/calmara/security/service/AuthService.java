package com.calmara.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.model.entity.User;
import com.calmara.model.mapper.UserMapper;
import com.calmara.security.utils.JwtUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AuthService {

    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AuthService(JwtUtils jwtUtils, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    public LoginResponse login(String username, String password) {
        User user = userMapper.findByUsername(username);

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtUtils.generateToken(user.getUsername(), user.getRole());

        log.info("用户登录成功: username={}, role={}", username, user.getRole());

        return new LoginResponse(token, user.getUsername(), user.getRole(), user.getId());
    }

    public User getUserByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Transactional
    public boolean register(String username, String password, String email) {
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在");
        }

        if (userMapper.findByEmail(email) != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setEmail(email);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        int result = userMapper.insert(user);

        if (result > 0) {
            log.info("用户注册成功: username={}, email={}", username, email);
            return true;
        }

        return false;
    }

    @Transactional
    public boolean registerStudent(String username, String password, String email, 
                                    String realName, String studentId) {
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在");
        }

        if (userMapper.findByEmail(email) != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setEmail(email);
        user.setRealName(realName);
        user.setStudentId(studentId);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        int result = userMapper.insert(user);

        if (result > 0) {
            log.info("学生用户注册成功: username={}, studentId={}", username, studentId);
            return true;
        }

        return false;
    }

    public List<User> getAllStudents() {
        return userMapper.findByRole("USER");
    }

    public long countUsers() {
        return userMapper.countUsers();
    }

    @Transactional
    public boolean updateUserStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        
        int result = userMapper.updateById(user);
        
        log.info("用户状态更新: userId={}, status={}", userId, status);
        return result > 0;
    }

    @Transactional
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        
        int result = userMapper.updateById(user);
        
        log.info("用户密码更新成功: userId={}", userId);
        return result > 0;
    }

    @Transactional
    public boolean resetPassword(Long userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        
        int result = userMapper.updateById(user);
        
        log.info("用户密码重置成功: userId={}", userId);
        return result > 0;
    }

    @Transactional
    public boolean updateProfile(Long userId, String realName, String phone, String avatar) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        if (realName != null) user.setRealName(realName);
        if (phone != null) user.setPhone(phone);
        if (avatar != null) user.setAvatar(avatar);
        user.setUpdatedAt(LocalDateTime.now());
        
        int result = userMapper.updateById(user);
        
        log.info("用户资料更新成功: userId={}", userId);
        return result > 0;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private String username;
        private String role;
        private Long userId;

        public LoginResponse(String token, String username, String role, Long userId) {
            this.token = token;
            this.username = username;
            this.role = role;
            this.userId = userId;
        }
    }
}
