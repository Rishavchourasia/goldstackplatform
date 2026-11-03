package com.goldstack.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.events.Events.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

public class IdempotentConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IdempotentConsumer(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public <E extends DomainEvent> void handle(String topic, String payload, Class<E> type, Consumer<E> handler) {
        E event = parse(payload, type);
        int inserted = jdbc.update(
                "INSERT INTO processed_events (event_id, topic, processed_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                event.eventId(), topic, Timestamp.from(Instant.now(clock)));
        if (inserted == 0) {
            log.info("Skipping duplicate {} event {}", topic, event.eventId());
            return;
        }
        handler.accept(event);
    }

    private <E> E parse(String payload, Class<E> type) {
        try {
            return mapper.readValue(payload, type);
        } catch (JsonProcessingException e) {

            throw new IllegalArgumentException("Unreadable " + type.getSimpleName() + ": " + payload, e);
        }
    }
}
