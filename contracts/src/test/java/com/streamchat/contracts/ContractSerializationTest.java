package com.streamchat.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── MessageCreatedEvent ───────────────────────────────────────────────────

    @Test
    void messageCreatedEvent_serializesToJson() throws Exception {
        UUID eventId        = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID messageId      = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID groupId        = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID senderId       = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID correlationId  = UUID.fromString("00000000-0000-0000-0000-000000000005");
        Instant createdAt   = Instant.parse("2024-01-15T10:30:00Z");

        MessageCreatedEvent event = new MessageCreatedEvent(
                eventId, "MESSAGE_CREATED", messageId, groupId,
                senderId, "Hello world", createdAt, correlationId, 1);

        String json = mapper.writeValueAsString(event);
        JsonNode node = mapper.readTree(json);

        assertEquals(eventId.toString(),       node.get("eventId").asText());
        assertEquals("MESSAGE_CREATED",        node.get("eventType").asText());
        assertEquals(messageId.toString(),     node.get("messageId").asText());
        assertEquals(groupId.toString(),       node.get("groupId").asText());
        assertEquals(senderId.toString(),      node.get("senderId").asText());
        assertEquals("Hello world",            node.get("content").asText());
        assertEquals("2024-01-15T10:30:00Z",   node.get("createdAt").asText());
        assertEquals(correlationId.toString(), node.get("correlationId").asText());
        assertEquals(1,                        node.get("schemaVersion").asInt());
    }

    @Test
    void messageCreatedEvent_deserializesFromJson() throws Exception {
        String json = """
                {
                  "eventId":       "00000000-0000-0000-0000-000000000001",
                  "eventType":     "MESSAGE_CREATED",
                  "messageId":     "00000000-0000-0000-0000-000000000002",
                  "groupId":       "00000000-0000-0000-0000-000000000003",
                  "senderId":      "00000000-0000-0000-0000-000000000004",
                  "content":       "Hello world",
                  "createdAt":     "2024-01-15T10:30:00Z",
                  "correlationId": "00000000-0000-0000-0000-000000000005",
                  "schemaVersion": 1
                }
                """;

        MessageCreatedEvent event = mapper.readValue(json, MessageCreatedEvent.class);

        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), event.eventId());
        assertEquals("MESSAGE_CREATED", event.eventType());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), event.messageId());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000003"), event.groupId());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000004"), event.senderId());
        assertEquals("Hello world", event.content());
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), event.createdAt());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000005"), event.correlationId());
        assertEquals(1, event.schemaVersion());
    }

    @Test
    void messageCreatedEvent_roundTrip() throws Exception {
        MessageCreatedEvent original = new MessageCreatedEvent(
                UUID.randomUUID(), "MESSAGE_CREATED", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Round-trip content", Instant.now(), UUID.randomUUID(), 1);

        String json = mapper.writeValueAsString(original);
        MessageCreatedEvent deserialized = mapper.readValue(json, MessageCreatedEvent.class);

        assertEquals(original, deserialized);
    }

    // ── UserActivityEvent ─────────────────────────────────────────────────────

    @Test
    void userActivityEvent_serializesToJson() throws Exception {
        UUID eventId       = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID groupId       = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID userId        = UUID.fromString("10000000-0000-0000-0000-000000000003");
        UUID correlationId = UUID.fromString("10000000-0000-0000-0000-000000000004");
        Instant occurredAt = Instant.parse("2024-02-20T12:00:00Z");

        UserActivityEvent event = new UserActivityEvent(
                eventId, "TYPING_STARTED", groupId, userId,
                occurredAt, correlationId, 1);

        String json = mapper.writeValueAsString(event);
        JsonNode node = mapper.readTree(json);

        assertEquals(eventId.toString(),       node.get("eventId").asText());
        assertEquals("TYPING_STARTED",         node.get("eventType").asText());
        assertEquals(groupId.toString(),       node.get("groupId").asText());
        assertEquals(userId.toString(),        node.get("userId").asText());
        assertEquals("2024-02-20T12:00:00Z",   node.get("occurredAt").asText());
        assertEquals(correlationId.toString(), node.get("correlationId").asText());
        assertEquals(1,                        node.get("schemaVersion").asInt());
    }

    @Test
    void userActivityEvent_deserializesFromJson() throws Exception {
        String json = """
                {
                  "eventId":       "10000000-0000-0000-0000-000000000001",
                  "eventType":     "TYPING_STARTED",
                  "groupId":       "10000000-0000-0000-0000-000000000002",
                  "userId":        "10000000-0000-0000-0000-000000000003",
                  "occurredAt":    "2024-02-20T12:00:00Z",
                  "correlationId": "10000000-0000-0000-0000-000000000004",
                  "schemaVersion": 1
                }
                """;

        UserActivityEvent event = mapper.readValue(json, UserActivityEvent.class);

        assertEquals(UUID.fromString("10000000-0000-0000-0000-000000000001"), event.eventId());
        assertEquals("TYPING_STARTED", event.eventType());
        assertEquals(UUID.fromString("10000000-0000-0000-0000-000000000002"), event.groupId());
        assertEquals(UUID.fromString("10000000-0000-0000-0000-000000000003"), event.userId());
        assertEquals(Instant.parse("2024-02-20T12:00:00Z"), event.occurredAt());
        assertEquals(UUID.fromString("10000000-0000-0000-0000-000000000004"), event.correlationId());
        assertEquals(1, event.schemaVersion());
    }

    @Test
    void userActivityEvent_roundTrip() throws Exception {
        UserActivityEvent original = new UserActivityEvent(
                UUID.randomUUID(), "TYPING_STOPPED", UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), UUID.randomUUID(), 1);

        String json = mapper.writeValueAsString(original);
        UserActivityEvent deserialized = mapper.readValue(json, UserActivityEvent.class);

        assertEquals(original, deserialized);
    }

    // ── ServerEvent ───────────────────────────────────────────────────────────

    @Test
    void serverEvent_withStringPayload_roundTrip() throws Exception {
        ServerEvent original = new ServerEvent("NOTIFICATION", "plain text payload");

        String json = mapper.writeValueAsString(original);
        ServerEvent deserialized = mapper.readValue(json, ServerEvent.class);

        assertEquals(original.type(), deserialized.type());
        assertEquals(original.payload(), deserialized.payload());
    }

    @Test
    void serverEvent_withTypingPayload_roundTrip() throws Exception {
        UUID groupId   = UUID.randomUUID();
        UUID userId    = UUID.randomUUID();
        Instant now    = Instant.parse("2024-03-10T08:00:00Z");
        TypingUpdatePayload payload = new TypingUpdatePayload(groupId, userId, true, now);

        ServerEvent event = new ServerEvent("TYPING_UPDATE", payload);

        String json = mapper.writeValueAsString(event);
        JsonNode node = mapper.readTree(json);

        assertEquals("TYPING_UPDATE", node.get("type").asText());

        TypingUpdatePayload recovered = mapper.convertValue(
                node.get("payload"), TypingUpdatePayload.class);

        assertEquals(groupId,  recovered.groupId());
        assertEquals(userId,   recovered.userId());
        assertTrue(recovered.typing());
        assertEquals(now, recovered.occurredAt());
    }

    // ── TypingUpdatePayload ───────────────────────────────────────────────────

    @Test
    void typingUpdatePayload_serializesToJson() throws Exception {
        UUID groupId   = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID userId    = UUID.fromString("20000000-0000-0000-0000-000000000002");
        Instant ts     = Instant.parse("2024-04-01T09:15:00Z");

        TypingUpdatePayload payload = new TypingUpdatePayload(groupId, userId, true, ts);

        String json = mapper.writeValueAsString(payload);
        JsonNode node = mapper.readTree(json);

        assertEquals(groupId.toString(),     node.get("groupId").asText());
        assertEquals(userId.toString(),      node.get("userId").asText());
        assertTrue(node.get("typing").asBoolean());
        assertEquals("2024-04-01T09:15:00Z", node.get("occurredAt").asText());
    }

    @Test
    void typingUpdatePayload_roundTrip() throws Exception {
        TypingUpdatePayload original = new TypingUpdatePayload(
                UUID.randomUUID(), UUID.randomUUID(), true, Instant.now());

        String json = mapper.writeValueAsString(original);
        TypingUpdatePayload deserialized = mapper.readValue(json, TypingUpdatePayload.class);

        assertEquals(original, deserialized);
    }

    @Test
    void typingUpdatePayload_typing_false() throws Exception {
        TypingUpdatePayload payload = new TypingUpdatePayload(
                UUID.randomUUID(), UUID.randomUUID(), false, Instant.now());

        String json = mapper.writeValueAsString(payload);
        JsonNode node = mapper.readTree(json);
        assertFalse(node.get("typing").asBoolean());

        TypingUpdatePayload deserialized = mapper.readValue(json, TypingUpdatePayload.class);
        assertFalse(deserialized.typing());
        assertEquals(payload, deserialized);
    }
}
