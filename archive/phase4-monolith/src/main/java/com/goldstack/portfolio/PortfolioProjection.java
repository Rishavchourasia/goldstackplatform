package com.goldstack.portfolio;

import com.goldstack.order.OrderDtos.OrderCompletedEvent;
import com.goldstack.user.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class PortfolioProjection {

    private final PortfolioPositionRepository positions;
    private final Clock clock;

    PortfolioProjection(PortfolioPositionRepository positions, Clock clock) {
        this.positions = positions;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    void onUserRegistered(UserRegisteredEvent event) {
        positions.save(new PortfolioPosition(event.userId(), Instant.now(clock)));
    }

    @EventListener
    @Transactional
    void onOrderCompleted(OrderCompletedEvent event) {
        PortfolioPosition position = positions.lockByUserId(event.userId())
                .orElseGet(() -> positions.save(new PortfolioPosition(event.userId(), Instant.now(clock))));
        position.apply(event.side(), event.grams(), event.coins(), Instant.now(clock));
    }
}
