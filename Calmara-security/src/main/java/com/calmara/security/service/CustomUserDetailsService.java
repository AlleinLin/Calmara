package com.calmara.security.service;

import com.calmara.model.entity.Permission;
import com.calmara.model.entity.User;
import com.calmara.model.mapper.PermissionMapper;
import com.calmara.model.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;

    public CustomUserDetailsService(UserMapper userMapper, PermissionMapper permissionMapper) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username);

        if (user == null) {
            log.warn("用户不存在: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("用户已被禁用: {}", username);
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }

        List<GrantedAuthority> authorities = getUserAuthorities(user);

        log.debug("加载用户详情: username={}, role={}, permissions={}", 
                username, user.getRole(), authorities.size());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isActive(),
                true,
                true,
                true,
                authorities
        );
    }

    public UserDetails loadUserById(Long userId) {
        User user = userMapper.selectById(userId);

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + userId);
        }

        List<GrantedAuthority> authorities = getUserAuthorities(user);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isActive(),
                true,
                true,
                true,
                authorities
        );
    }

    private List<GrantedAuthority> getUserAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));

        List<Permission> permissions = permissionMapper.findPermissionsByUserId(user.getId());
        
        List<GrantedAuthority> permissionAuthorities = permissions.stream()
                .map(p -> new SimpleGrantedAuthority(p.getPermissionKey()))
                .collect(Collectors.toList());

        authorities.addAll(permissionAuthorities);

        return authorities;
    }

    public User getUserEntity(String username) {
        return userMapper.findByUsername(username);
    }

    public boolean hasPermission(String username, String permissionKey) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            return false;
        }

        List<Permission> permissions = permissionMapper.findPermissionsByUserId(user.getId());
        return permissions.stream()
                .anyMatch(p -> p.getPermissionKey().equals(permissionKey));
    }

    public boolean hasRole(String username, String roleKey) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            return false;
        }
        return user.getRole().equals(roleKey);
    }
}
