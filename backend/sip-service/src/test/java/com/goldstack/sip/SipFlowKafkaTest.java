package com.goldstack.sip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.Side;
import com.goldstack.common.events.Events.OrderCompleted;
import com.goldstack.common.events.Events.SipDebitFailed;
import com.goldstack.common.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "goldstack.outbox.enabled=true"})
@Import(SipFlowKafkaTest.Containers.class)
@ActiveProfiles("test")
class SipFlowKafkaTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));
        }
    }

    private static final LocalDate RUN_DATE = LocalDate.of(2026, 1, 15);

    @Autowired SipRunner runner;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired KafkaContainer kafkaContainer;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("SIP over Kafka: requested -> order.completed comes back -> installment SUCCESS, and a second run requests nothing")
    void sipInstallmentSucceedsOverKafka() {
        UUID user = UUID.randomUUID();
        UUID sipId = insertDueSip(user, RUN_DATE);

        assertThat(runner.runDueSips(RUN_DATE).requested()).isEqualTo(1);
        awaitPublished(Topics.SIP_DEBIT_REQUESTED, user);
        assertThat(readUntil(Topics.SIP_DEBIT_REQUESTED, sipId.toString())).isTrue();

        UUID orderId = UUID.randomUUID();
        send(Topics.ORDER_COMPLETED, user, new OrderCompleted(UUID.randomUUID(), Instant.now(), orderId, user, Side.BUY,
                "SIP", new BigDecimal("500.00"), new BigDecimal("0.0500"), new BigDecimal("10000.00"), sipId, RUN_DATE));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM sip_executions WHERE sip_id = ?", String.class, sipId))
                        .isEqualTo("SUCCESS"));
        assertThat(jdbc.queryForObject("SELECT order_id FROM sip_executions WHERE sip_id = ?", UUID.class, sipId))
                .isEqualTo(orderId);

        assertThat(runner.runDueSips(RUN_DATE).requested()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sip_executions WHERE sip_id = ?", Long.class, sipId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("SIP over Kafka: sip.debit.failed comes back -> installment FAILED with the reason")
    void sipInstallmentFailsOverKafka() {
        UUID user = UUID.randomUUID();
        LocalDate runDate = RUN_DATE.plusDays(1);
        UUID sipId = insertDueSip(user, runDate);

        assertThat(runner.runDueSips(runDate).requested()).isEqualTo(1);
        awaitPublished(Topics.SIP_DEBIT_REQUESTED, user);

        send(Topics.SIP_DEBIT_FAILED, user, new SipDebitFailed(UUID.randomUUID(), Instant.now(), sipId, user,
                new BigDecimal("500.00"), runDate, null, "INSUFFICIENT_BALANCE", "not enough coins"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM sip_executions WHERE sip_id = ?", String.class, sipId))
                        .isEqualTo("FAILED"));
        assertThat(jdbc.queryForObject("SELECT failure_reason FROM sip_executions WHERE sip_id = ?", String.class, sipId))
                .isEqualTo("not enough coins");
    }

    private UUID insertDueSip(UUID user, LocalDate due) {
        jdbc.update("UPDATE sips SET status = 'CANCELLED' WHERE status = 'ACTIVE'");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO sips (id, user_id, amount, frequency, start_date, next_run_date, status, created_at, updated_at)
                VALUES (?, ?, 500.00, 'MONTHLY', ?, ?, 'ACTIVE', ?, ?)
                """, id, user, Date.valueOf(due), Date.valueOf(due), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return id;
    }

    private void awaitPublished(String topic, UUID user) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE topic = ? AND message_key = ? AND published_at IS NOT NULL",
                        Long.class, topic, user.toString())).isEqualTo(1L));
    }

    private void send(String topic, UUID user, Object event) {
        try {
            kafka.send(topic, user.toString(), mapper.writeValueAsString(event)).get();
        } catch (Exception e) {
            throw new IllegalStateException("Could not publish to " + topic, e);
        }
    }

    private boolean readUntil(String topic, String id) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> reader = new KafkaConsumer<>(props)) {
            reader.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : reader.poll(Duration.ofMillis(500))) {
                    if (r.value().contains(id)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
