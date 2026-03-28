package com.calmara.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_email")
public class AdminEmail {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String name;

    private String department;

    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
