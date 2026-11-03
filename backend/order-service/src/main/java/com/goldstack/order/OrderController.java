package com.goldstack.order;

import com.goldstack.common.error.ApiException;
import com.goldstack.common.security.AuthUser;
import com.goldstack.common.web.PageResponse;
import com.goldstack.order.OrderDtos.CreateQuoteRequest;
import com.goldstack.order.OrderDtos.ExecuteOrderRequest;
import com.goldstack.order.OrderDtos.GoldBalanceResponse;
import com.goldstack.order.OrderDtos.GoldLedgerEntryResponse;
import com.goldstack.order.OrderDtos.GoldLedgerReport;
import com.goldstack.order.OrderDtos.OrderResponse;
import com.goldstack.order.OrderDtos.OrderResult;
import com.goldstack.order.OrderDtos.QuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Orders & gold")
public class OrderController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final QuoteService quoteService;
    private final OrderService orderService;
    private final GoldService goldService;
    private final boolean devEndpoints;

    public OrderController(QuoteService quoteService, OrderService orderService, GoldService goldService,
                           @Value("${goldstack.dev-endpoints.enabled:false}") boolean devEndpoints) {
        this.quoteService = quoteService;
        this.orderService = orderService;
        this.goldService = goldService;
        this.devEndpoints = devEndpoints;
    }

    @PostMapping("/api/orders/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Step 1: lock a price for 5 minutes. BUY with 'coins', SELL with 'grams'.")
    public QuoteResponse quote(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateQuoteRequest req) {
        return quoteService.createQuote(user.id(), req);
    }

    @PostMapping("/api/orders/buy")
    @Operation(summary = "Step 2: buy with a quote. 201 done, 202 still processing (poll GET /api/orders/{id}), 422 failed.")
    public ResponseEntity<OrderResponse> buy(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "A unique random string per purchase attempt, e.g. a UUID")
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 8, max = 64) String idempotencyKey,
            @Valid @RequestBody ExecuteOrderRequest req) {
        return toResponse(orderService.buy(user.id(), req.quoteId(), idempotencyKey));
    }

    @PostMapping("/api/orders/sell")
    @Operation(summary = "Step 2: sell with a SELL quote")
    public ResponseEntity<OrderResponse> sell(
            @AuthenticationPrincipal AuthUser user,
            @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 8, max = 64) String idempotencyKey,
            @Valid @RequestBody ExecuteOrderRequest req) {
        return toResponse(orderService.sell(user.id(), req.quoteId(), idempotencyKey));
    }

    @GetMapping("/api/orders")
    @Operation(summary = "My orders, newest first (paginated), including PENDING and FAILED")
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal AuthUser user,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return orderService.listOrders(user.id(), page, size);
    }

    @GetMapping("/api/orders/{id}")
    @Operation(summary = "One of my orders: poll this after a 202 until status is COMPLETED or FAILED")
    public OrderResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return orderService.getOrder(user.id(), id);
    }

    @GetMapping("/api/gold")
    @Operation(summary = "My gold balance in grams")
    public GoldBalanceResponse gold(@AuthenticationPrincipal AuthUser user) {
        return goldService.balance(user.id());
    }

    @GetMapping("/api/gold/ledger")
    @Operation(summary = "My gold ledger entries, newest first")
    public PageResponse<GoldLedgerEntryResponse> goldLedger(@AuthenticationPrincipal AuthUser user,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return goldService.ledger(user.id(), page, size);
    }

    @GetMapping("/api/dev/ledger/gold")
    @Operation(summary = "Dev: check the gold ledger balances to zero")
    public GoldLedgerReport reconcile() {
        requireDev();
        return goldService.reconcile();
    }

    @PostMapping("/api/dev/saga/fail-next-gold-credit")
    @Operation(summary = "Dev: make the NEXT buy fail after the coins were taken, to watch the saga refund them")
    public Map<String, String> failNextGoldCredit() {
        requireDev();
        goldService.failNextGoldCredit();
        return Map.of("armed", "The next BUY will fail at the gold-credit step and be compensated with a refund");
    }

    private void requireDev() {
        if (!devEndpoints) {
            throw ApiException.notFound("Endpoint");
        }
    }

    private static ResponseEntity<OrderResponse> toResponse(OrderResult result) {
        HttpStatus status = "PENDING".equals(result.order().status()) ? HttpStatus.ACCEPTED
                : result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.order());
    }
}
