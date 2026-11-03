package com.goldstack.order;

import com.goldstack.common.error.ApiException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import com.goldstack.order.OrderDtos.OrderResponse;
import com.goldstack.order.OrderDtos.OrderResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class OrderService {

    private static final long WAIT_STEP_MS = 50;
    private static final long WAIT_MAX_MS = 10_000;

    private final OrderExecutor executor;
    private final GoldOrderRepository orders;
    private final QuoteStore quotes;
    private final IdempotencyStore idem;
    private final Clock clock;

    public OrderService(OrderExecutor executor, GoldOrderRepository orders, QuoteStore quotes,
                        IdempotencyStore idem, Clock clock) {
        this.executor = executor;
        this.orders = orders;
        this.quotes = quotes;
        this.idem = idem;
        this.clock = clock;
    }

    public OrderResult buy(UUID userId, UUID quoteId, String idempotencyKey) {
        return idempotent(userId, idempotencyKey, quoteId, () -> withQuote(userId, quoteId, idempotencyKey, Side.BUY));
    }

    public OrderResult sell(UUID userId, UUID quoteId, String idempotencyKey) {
        return idempotent(userId, idempotencyKey, quoteId, () -> withQuote(userId, quoteId, idempotencyKey, Side.SELL));
    }

    public OrderResult buyForSip(UUID userId, BigDecimal coins, String idempotencyKey) {
        return idempotent(userId, idempotencyKey, null, () -> executor.executeSipBuy(userId, coins, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(UUID userId, int page, int size) {
        return PageResponse.of(orders.findByUserId(userId, Paging.newestFirst(page, size, "createdAt")),
                OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID userId, UUID orderId) {
        return orders.findByIdAndUserId(orderId, userId).map(OrderResponse::from)
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> allOrdersOldestFirst(UUID userId) {
        return orders.findByUserIdOrderByCreatedAtAsc(userId).stream().map(OrderResponse::from).toList();
    }

    private OrderResult withQuote(UUID userId, UUID quoteId, String key, Side side) {
        Quote quote = quotes.take(userId, quoteId, side, Instant.now(clock));
        try {
            return executor.executeWithQuote(userId, quote, key);
        } catch (RuntimeException e) {
            quotes.putBack(quote, Instant.now(clock));
            throw e;
        }
    }

    private OrderResult idempotent(UUID userId, String key, UUID quoteId, Supplier<OrderResult> action) {
        long waitedMs = 0;
        while (true) {

            Optional<IdempotencyStore.Entry> seen = idem.find(userId, key);
            if (seen.isPresent() && seen.get().completed()) {
                return replay(seen.get().quoteId(), seen.get().order(), quoteId);
            }

            if (idem.tryLock(userId, key)) {
                return runLocked(userId, key, quoteId, action);
            }

            if (waitedMs >= WAIT_MAX_MS) {
                throw ApiException.conflict("REQUEST_IN_PROGRESS",
                        "An identical request is still being processed. Retry in a moment.");
            }
            sleep(WAIT_STEP_MS);
            waitedMs += WAIT_STEP_MS;
        }
    }

    private OrderResult runLocked(UUID userId, String key, UUID quoteId, Supplier<OrderResult> action) {
        try {

            Optional<GoldOrder> inDb = orders.findByUserIdAndIdempotencyKey(userId, key);
            if (inDb.isPresent()) {
                GoldOrder o = inDb.get();
                idem.complete(userId, key, o.getQuoteId(), OrderResponse.from(o));
                return replay(o.getQuoteId(), OrderResponse.from(o), quoteId);
            }

            OrderResult result = action.get();
            idem.complete(userId, key, quoteId, result.order());
            return result;
        } catch (DataIntegrityViolationException e) {

            GoldOrder winner = orders.findByUserIdAndIdempotencyKey(userId, key).orElseThrow(() -> e);
            idem.complete(userId, key, winner.getQuoteId(), OrderResponse.from(winner));
            return replay(winner.getQuoteId(), OrderResponse.from(winner), quoteId);
        } catch (RuntimeException e) {
            idem.release(userId, key);
            throw e;
        }
    }

    private static OrderResult replay(UUID originalQuoteId, OrderResponse original, UUID requestedQuoteId) {
        if (requestedQuoteId != null && !requestedQuoteId.equals(originalQuoteId)) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request");
        }
        return new OrderResult(original, true);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.conflict("REQUEST_IN_PROGRESS", "Interrupted while waiting for an identical request");
        }
    }
}
