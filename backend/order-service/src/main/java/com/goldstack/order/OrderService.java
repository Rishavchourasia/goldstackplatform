package com.goldstack.order;

import com.goldstack.common.Side;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.error.InsufficientFundsException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import com.goldstack.order.OrderDtos.OrderResponse;
import com.goldstack.order.OrderDtos.OrderResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final long LOCK_WAIT_STEP_MS = 50;
    private static final long LOCK_WAIT_MAX_MS = 10_000;
    private static final long OUTCOME_POLL_MS = 100;

    private final GoldOrderRepository orders;
    private final OrderPlacer placer;
    private final QuoteStore quotes;
    private final IdempotencyStore idem;
    private final OrderProperties props;
    private final Clock clock;

    public OrderService(GoldOrderRepository orders, OrderPlacer placer, QuoteStore quotes, IdempotencyStore idem,
                        OrderProperties props, Clock clock) {
        this.orders = orders;
        this.placer = placer;
        this.quotes = quotes;
        this.idem = idem;
        this.props = props;
        this.clock = clock;
    }

    public OrderResult buy(UUID userId, UUID quoteId, String idempotencyKey) {
        return trade(userId, quoteId, idempotencyKey, Side.BUY);
    }

    public OrderResult sell(UUID userId, UUID quoteId, String idempotencyKey) {
        return trade(userId, quoteId, idempotencyKey, Side.SELL);
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

    private OrderResult trade(UUID userId, UUID quoteId, String key, Side side) {
        long waited = 0;
        while (true) {
            Optional<IdempotencyStore.Entry> seen = idem.find(userId, key);
            if (seen.isPresent() && seen.get().done()) {
                return replay(seen.get().orderId(), seen.get().quoteId(), quoteId);
            }
            if (idem.tryLock(userId, key)) {
                return placeLocked(userId, quoteId, key, side);
            }
            if (waited >= LOCK_WAIT_MAX_MS) {
                throw ApiException.conflict("REQUEST_IN_PROGRESS",
                        "An identical request is still being processed. Retry in a moment.");
            }
            sleep(LOCK_WAIT_STEP_MS);
            waited += LOCK_WAIT_STEP_MS;
        }
    }

    private OrderResult placeLocked(UUID userId, UUID quoteId, String key, Side side) {
        Quote quote;
        UUID orderId;
        try {

            Optional<GoldOrder> inDb = orders.findByUserIdAndIdempotencyKey(userId, key);
            if (inDb.isPresent()) {
                idem.done(userId, key, inDb.get().getQuoteId(), inDb.get().getId());
                return replay(inDb.get().getId(), inDb.get().getQuoteId(), quoteId);
            }
            quote = quotes.take(userId, quoteId, side, Instant.now(clock));
            try {
                orderId = side == Side.BUY ? placer.placeBuy(userId, quote, key) : placer.placeSell(userId, quote, key);
            } catch (RuntimeException e) {
                quotes.putBack(quote, Instant.now(clock));
                throw e;
            }
        } catch (DataIntegrityViolationException e) {
            GoldOrder winner = orders.findByUserIdAndIdempotencyKey(userId, key).orElseThrow(() -> e);
            idem.done(userId, key, winner.getQuoteId(), winner.getId());
            return replay(winner.getId(), winner.getQuoteId(), quoteId);
        } catch (RuntimeException e) {
            idem.release(userId, key);
            throw e;
        }
        idem.done(userId, key, quoteId, orderId);

        GoldOrder outcome = awaitOutcome(orderId);
        if (outcome.getStatus() == GoldOrder.Status.FAILED && "INSUFFICIENT_BALANCE".equals(outcome.getFailureCode())) {

            quotes.putBack(quote, Instant.now(clock));
        }
        return toResult(outcome, false);
    }

    private OrderResult replay(UUID orderId, UUID originalQuoteId, UUID requestedQuoteId) {
        if (requestedQuoteId != null && originalQuoteId != null && !requestedQuoteId.equals(originalQuoteId)) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request");
        }
        return toResult(awaitOutcome(orderId), true);
    }

    private GoldOrder awaitOutcome(UUID orderId) {
        long deadline = System.currentTimeMillis() + props.waitMs();
        while (true) {
            GoldOrder o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
            if (!o.isPending() || System.currentTimeMillis() >= deadline) {
                return o;
            }
            sleep(OUTCOME_POLL_MS);
        }
    }

    private static OrderResult toResult(GoldOrder o, boolean replayed) {
        if (o.getStatus() == GoldOrder.Status.FAILED) {
            String code = o.getFailureCode() == null ? "ORDER_FAILED" : o.getFailureCode();
            if (code.startsWith("INSUFFICIENT_")) {
                throw new InsufficientFundsException(code, o.getFailureReason());
            }
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, o.getFailureReason());
        }
        return new OrderResult(OrderResponse.from(o), replayed);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.conflict("REQUEST_IN_PROGRESS", "Interrupted while waiting");
        }
    }
}
