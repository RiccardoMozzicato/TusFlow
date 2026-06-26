-- Spring AI JDBC chat-memory store (SPRING_AI_CHAT_MEMORY).
--
-- Schema is owned by Flyway, not the framework: keep
-- spring.ai.chat.memory.repository.jdbc.initialize-schema=never.
--
-- conversation_id is widened from the shipped VARCHAR(36) to VARCHAR(100): RagService keys memory by
-- "{userId}:{conversationId}" (two UUIDs + separator ≈ 73 chars) so a conversation is isolated per
-- authenticated user. Columns/index names otherwise match the framework's schema-postgresql.sql.
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");
