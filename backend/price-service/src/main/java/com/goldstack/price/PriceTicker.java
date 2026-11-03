package com.goldstack.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
class PriceTicker {

    private static final Logger log = LoggerFactory.getLogger(PriceTicker.class);

    private final PriceProvider provider;
    private final PriceService priceService;

    PriceTicker(PriceProvider provider, PriceService priceService) {
        this.provider = provider;
        this.priceService = priceService;
    }

    @Scheduled(fixedRateString = "${goldstack.price.tick-ms}", initialDelayString = "${goldstack.price.tick-ms}")
    @SchedulerLock(name = "priceTicker", lockAtMostFor = "PT10S", lockAtLeastFor = "PT4S")
    public void tick() {
        BigDecimal last = priceService.currentPrice().pricePerGram();
        BigDecimal next;
        try {
            next = provider.fetchPricePerGram(last);
        } catch (Exception e) {

            log.warn("Price provider {} failed, keeping last price {}: {}", provider.sourceName(), last, e.getMessage());
            return;
        }
        priceService.recordTick(next, provider.sourceName());
    }
}
