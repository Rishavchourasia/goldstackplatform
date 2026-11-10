package com.goldstack.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.Side;
import com.goldstack.common.events.Events.WalletSettled;
import com.goldstack.common.events.Events.WalletSettlementFailed;
import com.goldstack.common.events.Topics;
import com.goldstack.order.Clients.PriceClient;
import com.goldstack.order.Clients.WalletClient;
import com.goldstack.order.OrderDtos.CreateQuoteRequest;
import com.goldstack.order.OrderDtos.OrderResult;
import com.goldstack.order.OrderDtos.QuoteResponse;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "goldstack.order.wait-ms=200",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "goldstack.outbox.enabled=true"})
@Import(OrderSagaKafkaTest.Containers.class)
@ActiveProfiles("test")
class OrderSagaKafkaTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));
        }
    }

    @MockitoBean PriceClient priceClient;
    @MockitoBean WalletClient walletClient;

    @Autowired QuoteService quoteService;
    @Autowired OrderService orderService;
    @Autowired GoldOrderRepository orders;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaContainer kafkaContainer;

    @BeforeEach
    void stubOtherServices() {
        when(priceClient.latest()).thenReturn(new PriceClient.LivePrice(new BigDecimal("10000.00"), Instant.now(), "TEST"));
        when(walletClient.balance(any())).thenReturn(new WalletClient.Balance(new BigDecimal("100000.00")));
    }

    @Test
    @DisplayName("BUY over Kafka: wallet.settled in -> COMPLETED -> order.completed comes out on the topic")
    void buyCompletesOverKafka() {
        UUID user = UUID.randomUUID();
        OrderResult placed = orderService.buy(user, buyQuote(user, "500").quoteId(), "k-" + UUID.randomUUID());
        UUID orderId = placed.order().id();

        send(Topics.WALLET_SETTLED, user, new WalletSettled(UUID.randomUUID(), Instant.now(), orderId, user, Side.BUY));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(GoldOrder.Status.COMPLETED));
        awaitPublished(Topics.ORDER_COMPLETED, user);
        assertThat(readUntil(Topics.ORDER_COMPLETED, orderId)).isTrue();
    }

    @Test
    @DisplayName("BUY over Kafka: wallet.settlement.failed in -> FAILED -> order.failed comes out on the topic")
    void buyFailsOverKafka() {
        UUID user = UUID.randomUUID();
        OrderResult placed = orderService.buy(user, buyQuote(user, "500").quoteId(), "k-" + UUID.randomUUID());
        UUID orderId = placed.order().id();

        send(Topics.WALLET_SETTLEMENT_FAILED, user, new WalletSettlementFailed(UUID.randomUUID(), Instant.now(),
                orderId, user, Side.BUY, "INSUFFICIENT_BALANCE", "not enough coins"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(GoldOrder.Status.FAILED));
        awaitPublished(Topics.ORDER_FAILED, user);
        assertThat(readUntil(Topics.ORDER_FAILED, orderId)).isTrue();
    }

    private QuoteResponse buyQuote(UUID user, String coins) {
        return quoteService.createQuote(user, new CreateQuoteRequest(Side.BUY, new BigDecimal(coins), null));
    }

    private void send(String topic, UUID user, Object event) {
        try {
            kafka.send(topic, user.toString(), mapper.writeValueAsString(event)).get();
        } catch (Exception e) {
            throw new IllegalStateException("Could not publish to " + topic, e);
        }
    }

    private void awaitPublished(String topic, UUID user) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE topic = ? AND message_key = ? AND published_at IS NOT NULL",
                        Long.class, topic, user.toString())).isEqualTo(1L));
    }

    private boolean readUntil(String topic, UUID orderId) {
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
                    if (r.value().contains(orderId.toString())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
