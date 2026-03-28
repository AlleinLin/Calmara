package com.calmara.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    INVALID_CREDENTIALS(1003, "用户名或密码错误"),
    TOKEN_EXPIRED(1004, "Token已过期"),
    TOKEN_INVALID(1005, "Token无效"),

    MODEL_ERROR(2001, "模型调用失败"),
    EMOTION_ANALYSIS_ERROR(2002, "情绪分析失败"),
    WHISPER_ERROR(2003, "语音识别失败"),
    MEDIAPIPE_ERROR(2004, "图像分析失败"),
    INTENT_CLASSIFY_ERROR(2005, "意图分类失败"),
    RAG_ERROR(2006, "知识检索失败"),
    EMBEDDING_ERROR(2007, "向量嵌入失败"),
    VIDEO_ANALYSIS_ERROR(2008, "视频分析失败"),

    MCP_ERROR(3001, "MCP服务调用失败"),
    EXCEL_WRITE_ERROR(3002, "Excel写入失败"),
    EMAIL_SEND_ERROR(3003, "邮件发送失败"),

    REDIS_ERROR(4001, "Redis操作失败"),
    UPLOAD_ERROR(4002, "文件上传失败"),
    VALIDATION_ERROR(4003, "数据校验失败");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
