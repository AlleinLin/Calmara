package com.calmara.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdGeneratorTest {

    @Test
    void testGenerateSessionId() {
        String sessionId1 = IdGenerator.generateSessionId();
        String sessionId2 = IdGenerator.generateSessionId();

        assertNotNull(sessionId1);
        assertNotNull(sessionId2);
        assertTrue(sessionId1.startsWith("sess_"));
        assertTrue(sessionId2.startsWith("sess_"));
        assertNotEquals(sessionId1, sessionId2);
        assertEquals(21, sessionId1.length());
    }

    @Test
    void testGenerateUserId() {
        String userId1 = IdGenerator.generateUserId();
        String userId2 = IdGenerator.generateUserId();

        assertNotNull(userId1);
        assertNotNull(userId2);
        assertTrue(userId1.startsWith("U"));
        assertNotEquals(userId1, userId2);
    }

    @Test
    void testGenerateMessageId() {
        String msgId1 = IdGenerator.generateMessageId();
        String msgId2 = IdGenerator.generateMessageId();

        assertNotNull(msgId1);
        assertNotNull(msgId2);
        assertTrue(msgId1.startsWith("msg_"));
        assertNotEquals(msgId1, msgId2);
    }
}
