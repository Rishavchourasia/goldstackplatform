package com.goldstack.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;

@AutoConfiguration(after = KafkaAutoConfiguration.class)
public class MessagingAutoConfiguration {

    @Bean
    public KafkaAdmin.NewTopics goldstackTopics() {
        return new KafkaAdmin.NewTopics(Topics.ALL.stream()
                .map(t -> TopicBuilder.name(t).partitions(3).replicas(1).build())
                .toArray(NewTopic[]::new));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template),
                new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    @Bean
    public OutboxWriter outboxWriter(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        return new OutboxWriter(jdbc, mapper, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "goldstack.outbox.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelay outboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, Clock clock) {
        return new OutboxRelay(jdbc, kafka, clock);
    }

    @Bean
    public IdempotentConsumer idempotentConsumer(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        return new IdempotentConsumer(jdbc, mapper, clock);
    }
}
