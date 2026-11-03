package com.goldstack.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class Clients {

    private Clients() {
    }

    @FeignClient(name = "price-service", url = "${goldstack.clients.price-url}")
    interface PriceClient {
        @GetMapping("/internal/prices/latest")
        LivePrice latest();

        record LivePrice(BigDecimal pricePerGram, Instant recordedAt, String source) {
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
