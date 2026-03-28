package com.calmara.security.service;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.model.entity.User;
import com.calmara.model.mapper.UserMapper;
import com.calmara.security.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("encodedPassword");
        testUser.setEmail("test@example.com");
        testUser.setRole("USER");
        testUser.setStatus(1);
        testUser.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("登录成功测试")
    void testLoginSuccess() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(jwtUtils.generateToken("testuser", "USER")).thenReturn("test-token");

        AuthService.LoginResponse response = authService.login("testuser", "password");

        assertNotNull(response);
        assertEquals("test-token", response.getToken());
        assertEquals("testuser", response.getUsername());
        assertEquals("USER", response.getRole());
        assertEquals(1L, response.getUserId());

        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("登录失败-用户不存在")
    void testLoginUserNotFound() {
        when(userMapper.findByUsername("nonexistent")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, 
                () -> authService.login("nonexistent", "password"));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("登录失败-密码错误")
    void testLoginWrongPassword() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("wrongpassword", "encodedPassword")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login("testuser", "wrongpassword"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("登录失败-账户被禁用")
    void testLoginAccountDisabled() {
        testUser.setStatus(0);
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login("testuser", "password"));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("注册成功测试")
    void testRegisterSuccess() {
        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.findByEmail("new@example.com")).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        boolean result = authService.register("newuser", "password", "new@example.com");

        assertTrue(result);
        verify(userMapper).insert(any(User.class));
    }

    @Test
    @DisplayName("注册失败-用户名已存在")
    void testRegisterUsernameExists() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register("testuser", "password", "new@example.com"));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("注册失败-邮箱已存在")
    void testRegisterEmailExists() {
        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.findByEmail("test@example.com")).thenReturn(testUser);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register("newuser", "password", "test@example.com"));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("更新用户状态测试")
    void testUpdateUserStatus() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        boolean result = authService.updateUserStatus(1L, 0);

        assertTrue(result);
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("修改密码测试")
    void testUpdatePassword() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        boolean result = authService.updatePassword(1L, "oldPassword", "newPassword");

        assertTrue(result);
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("修改密码失败-原密码错误")
    void testUpdatePasswordWrongOld() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(passwordEncoder.matches("wrongOldPassword", "encodedPassword")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.updatePassword(1L, "wrongOldPassword", "newPassword"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), exception.getCode());
    }
}
