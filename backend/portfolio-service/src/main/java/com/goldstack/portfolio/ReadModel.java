package com.goldstack.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.Side;
import com.goldstack.common.events.Events.OrderCompleted;
import com.goldstack.common.events.Events.PriceUpdated;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.IdempotentConsumer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Entity
@Table(name = "portfolio_transactions")
class PortfolioTransaction {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal coins;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal grams;

    @Column(name = "price_per_gram", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerGram;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected PortfolioTransaction() {
    }

    PortfolioTransaction(OrderCompleted e) {
        this.orderId = e.orderId();
        this.userId = e.userId();
        this.side = e.side();
        this.source = e.source();
        this.coins = e.coins();
        this.grams = e.grams();
        this.pricePerGram = e.pricePerGram();
        this.completedAt = e.occurredAt();
    }

    UUID getOrderId() {
        return orderId;
    }

    Side getSide() {
        return side;
    }

    String getSource() {
        return source;
    }

    BigDecimal getCoins() {
        return coins;
    }

    BigDecimal getGrams() {
        return grams;
    }

    BigDecimal getPricePerGram() {
        return pricePerGram;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}

interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, UUID> {

    Page<PortfolioTransaction> findByUserId(UUID userId, Pageable pageable);

    List<PortfolioTransaction> findByUserIdOrderByCompletedAtAsc(UUID userId);
}

final class Clients {

    private Clients() {
    }

    @FeignClient(name = "price-service", url = "${goldstack.clients.price-url}")
    interface PriceClient {
        @GetMapping("/internal/prices/latest")
        LivePrice latest();

        @GetMapping("/internal/prices/daily")
        List<DailyPrice> daily(@RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

        @GetMapping("/internal/prices/earliest")
        Earliest earliest();

        record LivePrice(BigDecimal pricePerGram, Instant recordedAt, String source) {
        }

        record DailyPrice(LocalDate date, BigDecimal closePrice) {
        }

        record Earliest(LocalDate date) {
        }
    }

    @FeignClient(name = "wallet-service", url = "${goldstack.clients.wallet-url}")
    interface WalletClient {
        @GetMapping("/internal/wallets/{userId}/balance")
        Balance balance(@PathVariable("userId") UUID userId);

        record Balance(BigDecimal balance) {
        }
    }
}

@Component
class LatestPrice {

    record Value(BigDecimal pricePerGram, Instant at) {
    }

    private final AtomicReference<Value> latest = new AtomicReference<>();
    private final Clients.PriceClient priceClient;
    private final ObjectMapper mapper;

    LatestPrice(Clients.PriceClient priceClient, ObjectMapper mapper) {
        this.priceClient = priceClient;
        this.mapper = mapper;
    }

    Value get() {
        Value v = latest.get();
        if (v == null) {
            Clients.PriceClient.LivePrice p = priceClient.latest();
            v = new Value(p.pricePerGram(), p.recordedAt());
            latest.compareAndSet(null, v);
        }
        return v;
    }

    @KafkaListener(topics = Topics.PRICE_UPDATED, groupId = "portfolio-price-${random.uuid}",
            properties = "auto.offset.reset=latest")
    void onPrice(String payload) throws Exception {
        PriceUpdated e = mapper.readValue(payload, PriceUpdated.class);
        latest.set(new Value(e.pricePerGram(), e.recordedAt()));
    }
}

@Component
class PortfolioProjection {

    private final IdempotentConsumer consumer;
    private final PortfolioPositionRepository positions;
    private final PortfolioTransactionRepository transactions;
    private final Clock clock;

    PortfolioProjection(IdempotentConsumer consumer, PortfolioPositionRepository positions,
                        PortfolioTransactionRepository transactions, Clock clock) {
        this.consumer = consumer;
        this.positions = positions;
        this.transactions = transactions;
        this.clock = clock;
    }

    @KafkaListener(topics = Topics.ORDER_COMPLETED)
    void onOrderCompleted(String payload) {
        consumer.handle(Topics.ORDER_COMPLETED, payload, OrderCompleted.class, this::apply);
    }

    void apply(OrderCompleted e) {
        if (transactions.existsById(e.orderId())) {
            return;
        }
        transactions.save(new PortfolioTransaction(e));
        PortfolioPosition position = positions.lockByUserId(e.userId())
                .orElseGet(() -> positions.save(new PortfolioPosition(e.userId(), Instant.now(clock))));
        position.apply(e.side(), e.grams(), e.coins(), Instant.now(clock));
    }
}
