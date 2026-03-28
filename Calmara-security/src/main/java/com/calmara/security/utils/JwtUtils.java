package com.calmara.security.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    private static final String DEFAULT_SECRET = "calmara-secret-key-for-jwt-token-generation-must-be-long-enough";
    private static final int MIN_SECRET_LENGTH = 32;
    
    @Value("${calmara.jwt.secret:}")
    private String jwtSecret;

    @Value("${calmara.jwt.expiration:86400000}")
    private long jwtExpiration;
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    
    @Value("${calmara.jwt.allow-default-secret:false}")
    private boolean allowDefaultSecret;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            if ("prod".equalsIgnoreCase(activeProfile) && !allowDefaultSecret) {
                throw new IllegalStateException(
                    "生产环境必须配置 calmara.jwt.secret 参数！请在 application.yml 中设置安全的JWT密钥。"
                );
            }
            jwtSecret = DEFAULT_SECRET;
            log.warn("========================================");
            log.warn("警告: 使用默认JWT密钥！这在生产环境中是不安全的。");
            log.warn("请在配置文件中设置 calmara.jwt.secret 参数。");
            log.warn("========================================");
        }
        
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                String.format("JWT密钥长度不足！当前长度: %d, 最小要求: %d", 
                    jwtSecret.length(), MIN_SECRET_LENGTH)
            );
        }
        
        if (jwtSecret.equals(DEFAULT_SECRET) && "prod".equalsIgnoreCase(activeProfile)) {
            if (!allowDefaultSecret) {
                throw new IllegalStateException(
                    "生产环境禁止使用默认JWT密钥！请配置自定义密钥或设置 calmara.jwt.allow-default-secret=true (不推荐)"
                );
            }
            log.error("生产环境使用默认JWT密钥，存在严重安全风险！");
        }
        
        log.info("JwtUtils初始化完成, token过期时间: {}ms", jwtExpiration);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }
    
    public String getUsernameFromTokenSafe(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (Exception e) {
            log.warn("从token获取用户名失败: {}", e.getMessage());
            return null;
        }
    }

    public String getRoleFromToken(String token) {
        return (String) parseClaims(token).get("role");
    }
    
    public String getRoleFromTokenSafe(String token) {
        try {
            return (String) parseClaims(token).get("role");
        } catch (Exception e) {
            log.warn("从token获取角色失败: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            log.error("JWT token为空");
            return false;
        }
        
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("JWT token已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("不支持的JWT token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("JWT token格式错误: {}", e.getMessage());
        } catch (SecurityException e) {
            log.error("JWT signature验证失败: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT token无效: {}", e.getMessage());
        }
        return false;
    }
    
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
