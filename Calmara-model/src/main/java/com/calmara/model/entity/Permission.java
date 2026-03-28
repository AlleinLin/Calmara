package com.calmara.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String permissionName;

    private String permissionKey;

    private String resourceType;

    private String resourcePath;

    private String description;

    private Long parentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
