package com.goldstack.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, Clock clock) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.clock = clock;
    }

    private record Row(UUID id, String topic, String key, String payload) {
    }

    @Scheduled(fixedDelayString = "${goldstack.outbox.poll-ms:300}")
    @Transactional
    public void publishPending() {
        List<Row> rows = jdbc.query("""
                SELECT id, topic, message_key, payload FROM outbox
                WHERE published_at IS NULL ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED
                """, (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                rs.getString("message_key"), rs.getString("payload")));
        for (Row row : rows) {
            try {
                kafka.send(row.topic(), row.key(), row.payload()).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Outbox publish to {} failed, will retry: {}", row.topic(), e.getMessage());

                throw new IllegalStateException("Kafka unavailable", e);
            }
            jdbc.update("UPDATE outbox SET published_at = ? WHERE id = ?", Timestamp.from(Instant.now(clock)), row.id());
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void deleteOldPublished() {
        int n = jdbc.update("DELETE FROM outbox WHERE published_at < ?",
                Timestamp.from(Instant.now(clock).minus(7, ChronoUnit.DAYS)));
        if (n > 0) {
            log.info("Deleted {} old outbox rows", n);
        }
    }
}
