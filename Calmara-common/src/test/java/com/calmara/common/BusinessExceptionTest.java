package com.calmara.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void testBusinessExceptionWithErrorCode() {
        BusinessException exception = new BusinessException(ErrorCode.USER_NOT_FOUND);

        assertEquals(1001, exception.getCode());
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void testBusinessExceptionWithCodeAndMessage() {
        BusinessException exception = new BusinessException(500, "Custom error");

        assertEquals(500, exception.getCode());
        assertEquals("Custom error", exception.getMessage());
    }

    @Test
    void testBusinessExceptionWithCause() {
        RuntimeException cause = new RuntimeException("Original error");
        BusinessException exception = new BusinessException(500, "Error with cause", cause);

        assertEquals(500, exception.getCode());
        assertEquals("Error with cause", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
