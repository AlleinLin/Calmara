package com.calmara.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    private String phone;

    private String realName;

    private String studentId;

    private String role;

    private Integer status;

    private String avatar;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public boolean isAdmin() {
        return "ADMIN".equals(this.role);
    }

    public boolean isCounselor() {
        return "COUNSELOR".equals(this.role);
    }

    public boolean isActive() {
        return this.status != null && this.status == 1;
    }
}
