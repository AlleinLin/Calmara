package com.calmara.model.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MCPResultTest {

    @Test
    void testSuccessWithMessage() {
        MCPResult result = MCPResult.success("Operation completed");

        assertTrue(result.isSuccess());
        assertEquals("Operation completed", result.getMessage());
        assertNotNull(result.getData());
    }

    @Test
    void testSuccessWithMessageAndData() {
        MCPResult result = MCPResult.success("File written")
                .withData("path", "/path/to/file.xlsx");

        assertTrue(result.isSuccess());
        assertEquals("File written", result.getMessage());
        assertEquals("/path/to/file.xlsx", result.getData("path"));
    }

    @Test
    void testWithData() {
        MCPResult result = MCPResult.success("Test")
                .withData("key1", "value1")
                .withData("key2", 123);

        assertEquals("value1", result.getData("key1"));
        assertEquals(123, result.getData("key2"));
    }

    @Test
    void testFailure() {
        MCPResult result = MCPResult.failure("Operation failed");

        assertFalse(result.isSuccess());
        assertEquals("Operation failed", result.getMessage());
        assertNull(result.getData());
    }
}
