package com.calmara.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void testSuccessWithData() {
        Result<String> result = Result.success("test data");

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("test data", result.getData());
        assertTrue(result.isSuccess());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void testSuccessWithoutData() {
        Result<Void> result = Result.success();

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
        assertTrue(result.isSuccess());
    }

    @Test
    void testErrorWithCodeAndMessage() {
        Result<Void> result = Result.error(500, "Internal Error");

        assertEquals(500, result.getCode());
        assertEquals("Internal Error", result.getMessage());
        assertNull(result.getData());
        assertFalse(result.isSuccess());
    }

    @Test
    void testErrorWithErrorCode() {
        Result<Void> result = Result.error(ErrorCode.USER_NOT_FOUND);

        assertEquals(1001, result.getCode());
        assertEquals("用户不存在", result.getMessage());
        assertFalse(result.isSuccess());
    }

    @Test
    void testIsSuccess() {
        Result<String> successResult = Result.success("data");
        Result<String> errorResult = Result.error(400, "Bad Request");

        assertTrue(successResult.isSuccess());
        assertFalse(errorResult.isSuccess());
    }
}
